package onj.parser

import onj.customization.Namespace
import onj.customization.OnjConfig
import onj.customization.OnjFunction
import onj.value.*
import java.io.File
import java.io.IOException
import java.nio.file.Paths

class OnjParser private constructor(
    private val tokens: List<OnjToken>,
    private val code: String,
    private val fileName: String,
    private val file: File?,
    private val disallowedImports: List<File>
) {

    private var next = 0

    private val variables: MutableMap<String, OnjValue> = mutableMapOf()

    private val namespaces: MutableList<Namespace> = mutableListOf()

    init {
        OnjConfig.getGlobalNamespace()?.let { namespaces.add(it) }
    }

    private fun parseTopLevel(): OnjObject {
        val keys = mutableMapOf<String, OnjValue>()

        var allowKeyValue = true
        while (!end()) {
            allowKeyValue = parseTopLevelDeclaration(keys, allowKeyValue)
        }

        return OnjObject(keys)
    }

    private fun parseTopLevelDeclaration(keys: MutableMap<String, OnjValue>, allowKeyValue: Boolean): Boolean {
        val token = consume()

        when (token.type) {

            OnjTokenType.VAR -> parseVariableDeclaration()
            OnjTokenType.IMPORT -> parseImport()

            OnjTokenType.IDENTIFIER, OnjTokenType.STRING -> {

                if (!allowKeyValue) throw OnjParserException.fromErrorToken(
                    last(), "end of file", code, fileName
                )

                val (key, value) = parseKeyValuePair()
                if (keys.containsKey(key)) {
                    throw OnjParserException.fromErrorMessage(
                        token.char, code,
                        "Key $key was already defined",
                        fileName
                    )
                }
                keys[key] = value
                return tryConsume(OnjTokenType.COMMA)
            }

            else -> throw OnjParserException.fromErrorToken(
                token, "top level declaration", code, fileName
            )

        }
        return true
    }

    private fun parseImport() {
        val importPathToken = peek()
        val importPathValue = parseLiteral()
        if (!importPathValue.isString()) throw OnjParserException.fromErrorMessage(
            importPathToken.char, code,
            "Expected a string, found ${importPathToken::class.simpleName}",
            fileName
        )
        val importPath = importPathValue.value as String

        val toImport = doOnjFileImport(importPath, importPathToken)

//        if (peek().type == onj.builder.OnjTokenType.IDENTIFIER && peek().literal as String == "with") {
//            consume()
//            val schemaPathValue = parseLiteral()
//        }
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

    private fun doOnjFileImport(
        importPath: String,
        importPathToken: OnjToken
    ): OnjObject {
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
                importPathToken.char, code, "Couldn't read file '$importPath'", fileName
            )
        }
        val tokensToImport = Tokenizer(codeToImport, importPath, false).tokenize()
        val parser = OnjParser(
            tokensToImport,
            codeToImport,
            importPath,
            fileToImport,
            file?.let { disallowedImports + file.canonicalFile } ?: disallowedImports
        )
        return parser.parseTopLevel()
    }

    private fun parseKeyValuePair(): Pair<String, OnjValue> {
        val key = last()
        consume(OnjTokenType.COLON)
        return key.literal as String to parseValue()
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


    private fun parseValue(): OnjValue = parseInfixFunctionCall()

    private fun parseInfixFunctionCall(): OnjValue {
        var left = parseTerm()

        while (tryConsume(OnjTokenType.IDENTIFIER)) {
            val nameToken = last()
            val name = nameToken.literal as String
            val right = parseTerm()
            val functionArgs = arrayOf(left, right)
            val function = lookupFunction(name, functionArgs)

            if (function == null) {
                val argsString = functionArgs.joinToString(
                    separator = ", ",
                    prefix = "(",
                    postfix = ")",
                    transform = { it::class.simpleName ?: "" }
                )
                throw OnjParserException.fromErrorMessage(
                    nameToken.char, code,
                    "Cannot find infix function $name$argsString",
                    fileName
                )
            }

            left = function(functionArgs, nameToken, code, fileName)

        }

        return left
    }

    private fun parseTerm(): OnjValue {
        var left = parseFactor()
        while (tryConsume(OnjTokenType.PLUS, OnjTokenType.MINUS)) {
            val operator = last()
            val operatorName = operator.type.toString().lowercase()
            val right = parseFactor()
            val functionArgs = arrayOf(left, right)
            val function = lookupFunction("operator%$operatorName", functionArgs)
                ?: throw OnjParserException.fromErrorMessage(
                    operator.char, code,
                    "no overload for operator $operatorName and types ${left::class.simpleName}, ${right::class.simpleName}",
                    fileName
                )
            left = function(functionArgs, operator, code, fileName)
        }
        return left
    }

    private fun parseFactor(): OnjValue {
        var left = parseTypeConversion()
        while (tryConsume(OnjTokenType.STAR, OnjTokenType.DIV)) {
            val operator = last()
            val operatorName = operator.type.toString().lowercase()
            val right = parseTypeConversion()
            val functionArgs = arrayOf(left, right)
            val function = lookupFunction("operator%$operatorName", functionArgs)
                ?: throw OnjParserException.fromErrorMessage(
                    operator.char, code,
                    "no overload for operator $operatorName and types ${left::class.simpleName}, ${right::class.simpleName}",
                    fileName
                )
            left = function(functionArgs, operator, code, fileName)
        }
        return left
    }

    private fun parseTypeConversion(): OnjValue {
        var left = parseNegation()
        while (tryConsume(OnjTokenType.HASH)) {
            val convertToToken = consume(OnjTokenType.IDENTIFIER)
            val convertTo = convertToToken.literal as String

            val functionArgs = arrayOf(left)
            val function = lookupFunction("convert%$convertTo", functionArgs)
                ?: throw OnjParserException.fromErrorMessage(
                    convertToToken.char, code,
                    "no overload for converting ${left::class.simpleName} to '$convertTo'",
                    fileName
                )
            left = function(functionArgs, convertToToken, code, fileName)
        }
        return left
    }

    private fun parseNegation(): OnjValue {
        if (!tryConsume(OnjTokenType.MINUS)) return parseVariableAccess()

        val operator = last()

        val right = parseNegation()

        val functionArgs = arrayOf(right)
        val function = lookupFunction("operator%unaryMinus", functionArgs)
            ?: throw OnjParserException.fromErrorMessage(
                operator.char, code,
                "no overload for operator unary minus and type ${right::class.simpleName}",
                fileName
            )

        return function(functionArgs, operator, code, fileName)
    }

    private fun parseVariableAccess(): OnjValue {
        var left = parseLiteral()
        while (tryConsume(OnjTokenType.DOT)) {

            val token = consume()
            val accessWith = when (token.type) {

                OnjTokenType.L_PAREN -> {
                    val value = parseValue()
                    consume(OnjTokenType.R_PAREN)
                    value
                }

                OnjTokenType.STRING, OnjTokenType.IDENTIFIER -> OnjString(token.literal as String)
                OnjTokenType.INT -> OnjInt(token.literal as Long)

                else -> throw OnjParserException.fromErrorToken(
                    token, "accessor", code, fileName
                )

            }

            if (accessWith.isString()) {
                if (!left.isOnjObject()) throw OnjParserException.fromErrorMessage(
                    token.char, code,
                    "Cannot access ${left::class.simpleName} using type ${accessWith::class.simpleName}",
                    fileName
                )
                left = (left as OnjObject)[accessWith.value as String] ?: throw OnjParserException.fromErrorMessage(
                    token.char, code,
                    "Identifier ${accessWith.value as String} is not defined",
                    fileName
                )
            } else if (accessWith.isInt()) {
                if (!left.isOnjArray()) throw OnjParserException.fromErrorMessage(
                    token.char, code,
                    "Cannot access ${left::class.simpleName} using type ${accessWith::class.simpleName}",
                    fileName
                )
                left = (left as OnjArray).value.getOrNull((accessWith.value as Long).toInt())
                    ?: throw OnjParserException.fromErrorMessage(
                        token.char, code,
                        "Array does not define index ${accessWith.value as Long}",
                        fileName
                    )
            } else {
                throw OnjParserException.fromErrorMessage(
                    token.char, code,
                    "Expected an identifier, an int or a string, found ${accessWith::class.simpleName}",
                    fileName
                )
            }
        }
        return left
    }

    private fun parseLiteral(): OnjValue {
        val literal = consume()

        return when (literal.type) {

            OnjTokenType.INT -> OnjInt(literal.literal as Long)
            OnjTokenType.FLOAT -> OnjFloat(literal.literal as Double)
            OnjTokenType.BOOLEAN -> OnjBoolean(literal.literal as Boolean)
            OnjTokenType.STRING -> OnjString(literal.literal as String)
            OnjTokenType.L_BRACE -> parseObject()
            OnjTokenType.L_BRACKET -> parseArray()
            OnjTokenType.NULL -> OnjNull()
            OnjTokenType.IDENTIFIER -> {
                val name = last()
                if (tryConsume(OnjTokenType.L_PAREN)) parseFunctionCall(name) else parseVariable()
            }
            OnjTokenType.L_PAREN -> {
                val value = parseValue()
                consume(OnjTokenType.R_PAREN)
                value
            }
            OnjTokenType.DOLLAR -> {
                val nameToken = consume(OnjTokenType.IDENTIFIER)
                consume(OnjTokenType.L_BRACE)
                val obj = parseObject()
                @Suppress("UNCHECKED_CAST")
                OnjNamedObject(nameToken.literal as String, obj.value as Map<String, OnjValue>)
            }

            else -> throw OnjParserException.fromErrorToken(
                literal, "literal", code, fileName
            )

        }
    }

    private fun parseObject(): OnjValue {
        val keys = mutableMapOf<String, OnjValue>()
        while (!end()) {

            if (tryConsume(OnjTokenType.R_BRACE)) break

            if (tryConsume(OnjTokenType.DOT)) {
                consume(OnjTokenType.DOT)
                consume(OnjTokenType.DOT)

                val token = peek()
                val toInclude = parseLiteral()

                if (!toInclude.isOnjObject()) throw OnjParserException.fromErrorMessage(
                    token.char, code,
                    "Value included using the triple-dot must be of type Object, found ${toInclude::class.simpleName}",
                    fileName
                )

                for ((key, value) in (toInclude as OnjObject).value) {
                    if (keys.containsKey(key)) throw OnjParserException.fromErrorMessage(
                        token.char, code,
                        "key '$key' included using the triple-dot is already defined in the object",
                        fileName
                    )
                    keys[key] = value
                }
                if (!tryConsume(OnjTokenType.COMMA)) {
                    consume(OnjTokenType.R_BRACE)
                    break
                }
                continue
            }

            consume(OnjTokenType.IDENTIFIER, OnjTokenType.STRING)
            val keyToken = last()
            val (key, value) = parseKeyValuePair()

            if (keys.containsKey(key)) throw OnjParserException.fromErrorMessage(
                keyToken.char, code, "key $key was already defined", fileName
            )

            keys[key] = value

            if (!tryConsume(OnjTokenType.COMMA)) {
                consume(OnjTokenType.R_BRACE)
                break
            }
        }
        return OnjObject(keys)
    }

    private fun parseArray(): OnjValue {
        val values = mutableListOf<OnjValue>()
        while (!end()) {

            if (tryConsume(OnjTokenType.R_BRACKET)) break

            if (tryConsume(OnjTokenType.DOT)) {
                consume(OnjTokenType.DOT)
                consume(OnjTokenType.DOT)
                val token = peek()
                val toInclude = parseLiteral()
                if (!toInclude.isOnjArray()) throw OnjParserException.fromErrorMessage(
                    token.char, code,
                    "Value included using the triple-dot must be of type array, found ${toInclude::class.simpleName}",
                    fileName
                )
                for (value in (toInclude as OnjArray).value) values.add(value)

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
        return OnjArray(values)
    }

    private fun parseVariable(): OnjValue {
        val nameToken = last()
        val name = nameToken.literal as String
        return variables[name]
            ?:  lookupGlobalVariable(name)
            ?:  throw OnjParserException.fromErrorMessage(
                    nameToken.char, code, "Unknown variable $name", fileName
                )
    }

    private fun parseFunctionCall(nameToken: OnjToken): OnjValue {
        val name = nameToken.literal as String
        val paramsList = mutableListOf<OnjValue>()
        while (true) {
            if (tryConsume(OnjTokenType.R_PAREN)) break
            paramsList.add(parseValue())
            if (!tryConsume(OnjTokenType.COMMA)) {
                consume(OnjTokenType.R_PAREN)
                break
            }
        }
        val params = paramsList.toTypedArray()

        val function = lookupFunction(name, params)

        if (function == null) {

            val paramsString = paramsList.joinToString(
                separator = ", ",
                prefix = "(",
                postfix = ")",
                transform = { it::class.simpleName ?: "" }
            )

            throw OnjParserException.fromErrorMessage(
                nameToken.char, code,
                "no function $name$paramsString",
                fileName
            )
        }

        return function(params, nameToken, code, fileName)
    }

    private fun lookupFunction(name: String, params: Array<OnjValue>): OnjFunction? {
        for (nameSpace in namespaces) {
            nameSpace.getFunction(name, params)?.let { return it }
        }
        return null
    }

    private fun lookupGlobalVariable(name: String): OnjValue? {
        for (nameSpace in namespaces) {
            nameSpace.getVariable(name)?.let { return it }
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

        fun parseFile(file: File): OnjValue {
            val code = file.readText(Charsets.UTF_8)
            val tokens = Tokenizer(code, file.name, false).tokenize()
            return OnjParser(tokens, code, file.name, file, listOf()).parseTopLevel()
        }

        fun parseFile(path: String): OnjValue = parseFile(Paths.get(path).toFile())

        fun parse(code: String): OnjValue {
            val tokens = Tokenizer(code, "anonymous", false).tokenize()
            return OnjParser(tokens, code, "anonymous", null, listOf()).parseTopLevel()
        }

    }

}