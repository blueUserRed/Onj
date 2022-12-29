package onj.parser

import onj.customization.Namespace
import onj.customization.OnjConfig
import onj.schema.*
import onj.value.OnjObject
import java.io.File
import java.io.IOException
import java.nio.file.Paths

class OnjSchemaParser internal constructor(
    private val tokens: List<OnjToken>,
    private val code: String,
    private val fileName: String,
    private val file: File?,
    private val disallowedImports: List<File>
) {

    private var next = 0
    private val variables: MutableMap<String, OnjSchema> = mutableMapOf()
    private val namedObjectGroups: MutableMap<String, List<OnjSchemaNamedObject>> = mutableMapOf()
    private val namedObjectTokensToCheck: MutableList<OnjToken> = mutableListOf()

    private val namespaces: MutableList<Namespace> = mutableListOf()

    init {
        OnjConfig.getGlobalNamespace()?.let { namespaces.add(it) }
    }

    private fun parseTopLevel(): OnjSchema {
        val keys = mutableMapOf<String, OnjSchema>()
        val optionalKeys = mutableMapOf<String, OnjSchema>()

        var allowKeyValue = true
        while (!end()) {
            allowKeyValue = parseTopLevelDeclaration(keys, optionalKeys, allowKeyValue)
        }
        checkNamedObjectTokens()
        return OnjSchemaObject(false, keys, optionalKeys, false)
    }

    private fun parseVariableDeclaration() {
        val nameToken = consume(OnjTokenType.IDENTIFIER)
        val name = nameToken.literal as String
        if (variables.containsKey(name)) throw OnjParserException.fromErrorMessage(
            nameToken.char, code, "variable $name was already defined", fileName
        )
        consume(OnjTokenType.EQUALS)
        variables[name] = parseValue()
        consume(OnjTokenType.SEMICOLON)
    }

    private fun checkNamedObjectTokens() {
        val names = namedObjectGroups.keys
        for (token in namedObjectTokensToCheck) {
            val name = token.literal as String
            if (name !in names) throw OnjParserException.fromErrorMessage(
                token.char, code, "No group named $name", fileName
            )
        }
    }

    private fun parseTopLevelDeclaration(
        keys: MutableMap<String, OnjSchema>,
        optionalKeys: MutableMap<String, OnjSchema>,
        allowKeyValue: Boolean
    ): Boolean {
        val token = consume()

        when (token.type) {

            OnjTokenType.VAR -> parseVariableDeclaration()
            OnjTokenType.IMPORT -> parseImport()
            OnjTokenType.DOLLAR -> parseNamedObjectGroup()

            OnjTokenType.USE -> {
                val identifierToken = consume(OnjTokenType.IDENTIFIER)
                consume(OnjTokenType.SEMICOLON)
                val name = identifierToken.literal as String
                val namespace = OnjConfig.getNamespace(name)
                    ?: throw OnjParserException.fromErrorMessage(
                        identifierToken.char, code,
                        "Could not find namespace $name",
                        fileName
                    )
                namespaces.add(namespace)
            }

            OnjTokenType.IDENTIFIER, OnjTokenType.STRING -> {

                if (!allowKeyValue) throw OnjParserException.fromErrorToken(
                    last(), "end of file", code, fileName
                )

                val (key, value, optional) = parseKeyValuePair()
                val keysToCheck = if (optional) optionalKeys else keys
                if (keysToCheck.containsKey(key) || optionalKeys.containsKey(key)) {
                    throw OnjParserException.fromErrorMessage(
                        token.char, code,
                        "Key $key was already defined",
                        fileName
                    )
                }
                keysToCheck[key] = value
                return tryConsume(OnjTokenType.COMMA)
            }

            OnjTokenType.DOT -> {
                consume(OnjTokenType.DOT)
                consume(OnjTokenType.DOT)
                val includeToken = peek()
                val toInclude = parseLiteral()
                consume(OnjTokenType.COMMA)
                if (toInclude !is OnjSchemaObject) throw OnjParserException.fromErrorMessage(
                    includeToken.char, code,
                    "Value included using the triple" +
                            " dot must be of type Object, fount ${toInclude::class.simpleName}",
                    fileName
                )
                for ((key, value) in toInclude.keys) {
                    if (keys.containsKey(key) || optionalKeys.containsKey(key)) throw OnjParserException.fromErrorMessage(
                        includeToken.char, code,
                        "Key $key included using the triple dot was already declared",
                        fileName
                    )
                    keys[key] = value
                }
                for ((key, value) in toInclude.optionalKeys) {
                    if (keys.containsKey(key) || optionalKeys.containsKey(key)) throw OnjParserException.fromErrorMessage(
                        includeToken.char, code,
                        "Key $key included using the triple dot was already declared",
                        fileName
                    )
                    optionalKeys[key] = value
                }
            }

            else -> throw OnjParserException.fromErrorToken(
                token, "top level declaration", code, fileName
            )

        }
        return true
    }

    private fun parseNamedObjectGroup() {
        consume(OnjTokenType.IDENTIFIER)
        val groupNameToken = last()
        val groupName = groupNameToken.literal as String
        consume(OnjTokenType.L_BRACE)
        val usedNames = mutableListOf<String>()
        val objects = mutableListOf<OnjSchemaNamedObject>()
        namedObjectGroups
            .flatMap { it.value }
            .forEach { usedNames.add(it.name) }
        while (tryConsume(OnjTokenType.DOLLAR)) {
            val nameToken = consume(OnjTokenType.IDENTIFIER)
            val name = nameToken.literal as String
            consume(OnjTokenType.L_BRACE)
            val obj = parseObject() as OnjSchemaObject
            if (name in usedNames) throw OnjParserException.fromErrorMessage(
                nameToken.char, code,
                "Object with name $name already exists" +
                        "(Note that object names must be unique across groups)",
                fileName
            )
            usedNames.add(name)
            objects.add(OnjSchemaNamedObject(name, obj))
        }
        consume(OnjTokenType.R_BRACE)
        if (namedObjectGroups.containsKey(groupName)) throw OnjParserException.fromErrorMessage(
            groupNameToken.char, code, "Group named $groupName already exists", fileName
        )
        namedObjectGroups[groupName] = objects
    }

    private fun parseImport() {
        val importPathToken = consume(OnjTokenType.STRING)
        val importPath = importPathToken.literal as String

        val (toImport, parser) = doOnjSchemaFileImport(importPath, importPathToken)

        val namedObjectNames = namedObjectGroups
            .flatMap { it.value }
            .map { it.name }

        for ((namedObjectGroup, namedObjects) in parser.namedObjectGroups) {
            if (namedObjectGroup in namedObjectGroups.keys) throw OnjParserException.fromErrorMessage(
                importPathToken.char, code,
                "named object group $namedObjectGroup imported here was already declared",
                fileName
            )
            for (namedObject in namedObjects) {
                if (namedObject.name in namedObjectNames) throw OnjParserException.fromErrorMessage(
                    importPathToken.char, code,
                    "named object ${namedObject.name} imported here was already declared" +
                            "(Note that named object names need to be unique across groups)",
                    fileName
                )
            }
            namedObjectGroups[namedObjectGroup] = namedObjects
        }

        consume(OnjTokenType.IDENTIFIER)
        if ((last().literal as String).lowercase() != "as") throw OnjParserException.fromErrorToken(
            last(), "as", code, fileName
        )
        val varNameToken = consume(OnjTokenType.IDENTIFIER)
        val varName = varNameToken.literal as String

        consume(OnjTokenType.SEMICOLON)

        if (varName == "_") return

        if (variables.containsKey(varName)) throw OnjParserException.fromErrorMessage(
            varNameToken.char, code, "Variable $varName was already defined!", fileName
        )
        variables[varName] = toImport
    }

    private fun doOnjSchemaFileImport(
        importPath: String,
        importPathToken: OnjToken
    ): Pair<OnjSchema, OnjSchemaParser> {
        val fileToImport = file?.let {
            file.parentFile.toPath().resolve(importPath).toFile()
        } ?: Paths.get(importPath).toFile()

        if (fileToImport.canonicalFile in disallowedImports) throw OnjParserException.fromErrorMessage(
            importPathToken.char, code,
            "Import loop detected: file '$importPath' imported here is currently importing this file",
            fileName
        )

        val codeToImport = try {
            fileToImport.readText(Charsets.UTF_8)
        } catch (e: IOException) {
            throw OnjParserException.fromErrorMessage(
                importPathToken.char, code, "Couldn't read file '$importPath'", fileName, e
            )
        }
        val tokensToImport = Tokenizer(codeToImport, importPath, true).tokenize()
        val parser = OnjSchemaParser(
            tokensToImport,
            codeToImport,
            importPath,
            fileToImport,
            file?.let { disallowedImports + file.canonicalFile } ?: disallowedImports
        )
        return parser.parseTopLevel() to parser
    }

    private fun parseKeyValuePair(): Triple<String, OnjSchema, Boolean> {
        val key = last()
        val optional = tryConsume(OnjTokenType.QUESTION)
        consume(OnjTokenType.COLON)
        return Triple(key.literal as String, parseValue(), optional)
    }

    private fun parseValue(): OnjSchema {
        return parsePostFixArray()
    }

    private fun parsePostFixArray(): OnjSchema {
        var left = parseLiteral()
        while (tryConsume(OnjTokenType.L_BRACKET)) {
            val size = if (tryConsume(OnjTokenType.INT)) (last().literal as Long).toInt() else null
            consume(OnjTokenType.R_BRACKET)
            val nullable = tryConsume(OnjTokenType.QUESTION)
            left = TypeBasedOnjSchemaArray(left, size, nullable)
        }
        return left
    }

    private fun parseLiteral(): OnjSchema {
        val token = consume()
        val schema = when (token.type) {

            OnjTokenType.T_INT -> OnjSchemaInt(false)
            OnjTokenType.T_FLOAT -> OnjSchemaFloat(false)
            OnjTokenType.T_BOOLEAN -> OnjSchemaBoolean(false)
            OnjTokenType.T_STRING -> OnjSchemaString(false)
            OnjTokenType.STAR -> OnjSchemaAny()
            OnjTokenType.L_BRACE -> parseObject()
            OnjTokenType.L_BRACKET -> parseArray()
            OnjTokenType.IDENTIFIER -> {
                val identifierToken = last()
                val identifier = identifierToken.literal as String
                variables[identifier]
                    ?:  lookupCustomDatatype(identifier)
                    ?:  throw OnjParserException.fromErrorMessage(
                            identifierToken.char, code, "Unknown variable $identifier", fileName
                        )
            }
            OnjTokenType.DOLLAR -> {
                val nameToken = consume(OnjTokenType.IDENTIFIER)
                namedObjectTokensToCheck.add(nameToken)
                OnjSchemaNamedObjectGroup(nameToken.literal as String, false, namedObjectGroups)
            }

            else -> throw OnjParserException.fromErrorToken(
                token, "value", code, fileName
            )

        }
        return if (tryConsume(OnjTokenType.QUESTION)) schema.getAsNullable() else schema
    }

    private fun parseObject(): OnjSchema {
        val keys = mutableMapOf<String, OnjSchema>()
        val optionalKeys = mutableMapOf<String, OnjSchema>()
        var allowsAdditional = false
        while (!end()) {

            if (tryConsume(OnjTokenType.R_BRACE)) break

            if (tryConsume(OnjTokenType.DOT)) {
                consume(OnjTokenType.DOT)
                consume(OnjTokenType.DOT)

                if (tryConsume(OnjTokenType.STAR)) {
                    allowsAdditional = true
                    continue
                }

                val token = peek()
                val toInclude = parseLiteral()

                if (toInclude !is OnjSchemaObject) throw OnjParserException.fromErrorMessage(
                    token.char, code,
                    "Value included using the triple-dot must be of type Object, found ${toInclude::class.simpleName}",
                    fileName
                )

                for ((key, value) in toInclude.keys) {
                    if (keys.containsKey(key) || optionalKeys.containsKey(key)) throw OnjParserException.fromErrorMessage(
                        token.char, code,
                        "key '$key' included using the triple-dot is already defined in the object",
                        fileName
                    )
                    keys[key] = value
                }
                for ((key, value) in toInclude.optionalKeys) {
                    if (optionalKeys.containsKey(key) || optionalKeys.containsKey(key)) throw OnjParserException.fromErrorMessage(
                        token.char, code,
                        "key '$key' included using the triple-dot is already defined in the object",
                        fileName
                    )
                    optionalKeys[key] = value
                }

                if (!tryConsume(OnjTokenType.COMMA)) {
                    consume(OnjTokenType.R_BRACE)
                    break
                }
                continue
            }

            consume(OnjTokenType.IDENTIFIER, OnjTokenType.STRING)
            val keyToken = last()
            val (key, value, optional) = parseKeyValuePair()

            if (keys.containsKey(key) || optionalKeys.containsKey(key)) throw OnjParserException.fromErrorMessage(
                keyToken.char, code, "key $key was already defined", fileName
            )

            if (optional) optionalKeys[key] = value else keys[key] = value

            if (!tryConsume(OnjTokenType.COMMA)) {
                consume(OnjTokenType.R_BRACE)
                break
            }
        }
        return OnjSchemaObject(false, keys, optionalKeys, allowsAdditional)

    }

    private fun parseArray(): OnjSchema {
        val values = mutableListOf<OnjSchema>()
        while (!end()) {

            if (tryConsume(OnjTokenType.R_BRACKET)) break

            if (tryConsume(OnjTokenType.DOT)) {
                consume(OnjTokenType.DOT)
                consume(OnjTokenType.DOT)
                val token = peek()
                val toInclude = parseLiteral()
                if (toInclude is TypeBasedOnjSchemaArray) throw OnjParserException.fromErrorMessage(
                    token.char, code,
                    "Arrays can only include arrays that were defined as" +
                    " literals (using the [element, element] syntax)",
                    fileName
                )
                if (toInclude !is LiteralOnjSchemaArray) throw OnjParserException.fromErrorMessage(
                    token.char, code,
                    "Value included using the triple-dot must be of type array, found ${toInclude::class.simpleName}",
                    fileName
                )
                for (value in toInclude.schemas) values.add(value)

                if (!tryConsume(OnjTokenType.COMMA)) {
                    consume(OnjTokenType.R_BRACKET)
                    break
                }
                continue
            }

            values.add(parseValue())

            if (!tryConsume(OnjTokenType.COMMA)) {
                consume(OnjTokenType.R_BRACKET)
                break
            }
        }
        return LiteralOnjSchemaArray(values, false)
    }

    private fun lookupCustomDatatype(name: String): OnjSchema? {
        for (namespace in namespaces) {
            namespace.getCustomDataType(name)?.let { return OnjSchemaCustomDataType(name, it, false) }
        }
        return null
    }

    private fun end(): Boolean = next >= tokens.size || tokens[next].type == OnjTokenType.EOF

    private fun tryConsume(type: OnjTokenType): Boolean {
        val token = consume()
        if (type == token.type) return true
        next--
        return false
    }

    private fun tryConsume(vararg types: OnjTokenType): Boolean {
        val token = consume()
        if (token.type in types) return true
        next--
        return false
    }

    private fun last(): OnjToken = tokens[next - 1]

    private fun peek(): OnjToken = tokens[next]

    private fun consume(type: OnjTokenType): OnjToken {
        val token = consume()
        if (type == token.type) return token
        throw OnjParserException.fromErrorToken(token, type, code, fileName)
    }

    private fun consume(vararg types: OnjTokenType, expected: String? = null): OnjToken {
        val token = consume()
        if (token.type in types) return token
        throw OnjParserException.fromErrorToken(
            token,
            expected ?: "one of ${tokenTypesAsString(types)}",
            code, fileName
        )
    }

    private fun tokenTypesAsString(types: Array<out OnjTokenType>): String {
        return types.joinToString(", ", "[", "]")
    }

    private fun consume(): OnjToken = tokens[next++]

    companion object {

        fun parseFile(file: File): OnjSchema {
            val code = file.readText(Charsets.UTF_8)
            val tokens = Tokenizer(code, file.name, true).tokenize()
            return OnjSchemaParser(tokens, code, file.name, file, listOf()).parseTopLevel()
        }

        fun parseFile(path: String): OnjSchema = parseFile(Paths.get(path).toFile())

        fun parse(code: String): OnjSchema {
            val tokens = Tokenizer(code, "anonymous", true).tokenize()
            return OnjSchemaParser(tokens, code, "anonymous", null, listOf()).parseTopLevel()
        }

    }

}