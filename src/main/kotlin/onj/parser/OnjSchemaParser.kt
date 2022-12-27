package onj.parser

import onj.schema.*
import onj.value.OnjArray
import onj.value.OnjObject
import onj.value.OnjValue
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


    private fun parseTopLevel(): OnjSchema {
        val keys = mutableMapOf<String, OnjSchema>()
        val optionalKeys = mutableMapOf<String, OnjSchema>()

        var allowKeyValue = true
        while (!end()) {
            allowKeyValue = parseTopLevelDeclaration(keys, optionalKeys, allowKeyValue)
        }

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

    private fun parseTopLevelDeclaration(
        keys: MutableMap<String, OnjSchema>,
        optionalKeys: MutableMap<String, OnjSchema>,
        allowKeyValue: Boolean
    ): Boolean {
        val token = consume()

        when (token.type) {

            OnjTokenType.VAR -> parseVariableDeclaration()
            OnjTokenType.IMPORT -> parseImport()

            OnjTokenType.IDENTIFIER, OnjTokenType.STRING -> {

                if (!allowKeyValue) throw OnjParserException.fromErrorToken(
                    last(), "end of file", code, fileName
                )

                val (key, value, optional) = parseKeyValuePair()
                val keysToCheck = if (optional) optionalKeys else keys
                if (keysToCheck.containsKey(key)) {
                    throw OnjParserException.fromErrorMessage(
                        token.char, code,
                        "Key $key was already defined",
                        fileName
                    )
                }
                keysToCheck[key] = value
                return tryConsume(OnjTokenType.COMMA)
            }

            else -> throw OnjParserException.fromErrorToken(
                token, "top level declaration", code, fileName
            )

        }
        return true
    }

    private fun parseImport() {
        val importPathToken = consume(OnjTokenType.STRING)
        val importPath = importPathToken.literal as String

        val toImport = doOnjSchemaFileImport(importPath, importPathToken)

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
    ): OnjSchema {
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
        val tokensToImport = OnjTokenizer().tokenize(codeToImport, importPath, false)
        val parser = OnjSchemaParser(
            tokensToImport,
            codeToImport,
            importPath,
            fileToImport,
            file?.let { disallowedImports + file.canonicalFile } ?: disallowedImports
        )
        return parser.parseTopLevel()
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
                variables[identifier] ?: throw OnjParserException.fromErrorMessage(
                    identifierToken.char, code, "Unknown variable $identifier", fileName
                )
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
                    if (keys.containsKey(key)) throw OnjParserException.fromErrorMessage(
                        token.char, code,
                        "key '$key' included using the triple-dot is already defined in the object",
                        fileName
                    )
                    keys[key] = value
                }
                for ((key, value) in toInclude.optionalKeys) {
                    if (optionalKeys.containsKey(key)) throw OnjParserException.fromErrorMessage(
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
            val keysToCheck = if (optional) optionalKeys else keys

            if (keysToCheck.containsKey(key)) throw OnjParserException.fromErrorMessage(
                keyToken.char, code, "key $key was already defined", fileName
            )

            keysToCheck[key] = value

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
            val tokens = OnjTokenizer().tokenize(code, file.name, true)
            return OnjSchemaParser(tokens, code, file.name, file, listOf()).parseTopLevel()
        }

        fun parseFile(path: String): OnjSchema = parseFile(Paths.get(path).toFile())

        fun parse(code: String): OnjSchema {
            val tokens = OnjTokenizer().tokenize(code, "anonymous", true)
            return OnjSchemaParser(tokens, code, "anonymous", null, listOf()).parseTopLevel()
        }

    }

}