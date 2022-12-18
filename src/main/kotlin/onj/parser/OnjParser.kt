package onj.parser

import onj.*
import onj.OnjToken
import onj.OnjTokenizer
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths

/**
 * used for parsing a .onj file
 */
class OnjParser private constructor(private val previousFiles: List<Path> = listOf()) {

    private var next: Int = 0
    private var tokens: List<OnjToken> = listOf()
    private var code: String = ""
    private var filename: String = ""

    private val variables: MutableMap<String, OnjValue> = mutableMapOf()
    private val exports: MutableList<String> = mutableListOf()

    private fun parse(tokens: List<OnjToken>, code: String, filename: String): OnjObject {
        next = 0
        this.tokens = tokens
        this.code = code
        this.filename = filename
        this.variables.clear()

        return parseTopLevel()
    }

    private fun parseTopLevel(): OnjObject {
        val values = mutableMapOf<String, OnjValue>()
        while (!tryConsume(OnjTokenType.EOF)) {

            if (tryConsume(OnjTokenType.EXCLAMATION)) parseVariableDeclaration(false)
            else if (tryConsume(OnjTokenType.IMPORT)) parseImport()
            else if (tryConsume(OnjTokenType.EXPORT)) {
                consume(OnjTokenType.EXCLAMATION)
                parseVariableDeclaration(true)
            }
            else {
                val key = if (tryConsume(OnjTokenType.IDENTIFIER)) {
                    last().literal as String
                } else if (tryConsume(OnjTokenType.STRING)) {
                    last().literal as String
                } else {
                    throw OnjParserException.fromErrorToken(
                        current(),
                        "identifier or string identifier",
                        code,
                        filename
                    )
                }
                consume(OnjTokenType.COLON)
                values[key] = parseValue()
            }

        }
        return OnjObject(values)
    }

    private fun parseImport() {
        consume(OnjTokenType.STRING)
        val pathToken = last()
        val path = pathToken.literal as String

        val normalized = Paths.get(path).normalize()
        if (normalized in previousFiles || normalized == Paths.get(filename).normalize()) {
            throw OnjParserException.fromErrorMessage(
                pathToken.char,
                code,
                "Import loop detect: file imports itself",
                filename
            )
        }

        consume(OnjTokenType.IDENTIFIER)
        if (last().literal as String != "as") {
            throw OnjParserException.fromErrorToken(last(), "'as'", code, filename)
        }
        consume(OnjTokenType.IDENTIFIER)

        val nameToken = last()
        val name = nameToken.literal as String

        val parser = OnjParser(previousFiles + Paths.get(filename).normalize())

        val code = try {
            File(Paths.get(path).toUri()).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            throw OnjParserException.fromErrorMessage(
                pathToken.char,
                code,
                "Couldn't open imported file '$path'",
                filename,
                e
            )
        }

        val result = parser.parse(OnjTokenizer().tokenize(code, path), code, path)

        if (name != "_") {
            if (variables.containsKey(name)) {
                throw OnjParserException.fromErrorMessage(
                    nameToken.char,
                    code,
                    "redefinition of variable '$name'",
                    filename
                )
            }
            variables[name] = result
        }

        for ((varName, variable) in parser.variables) if (varName in parser.exports) {
            if (variables.containsKey(varName)) {
                throw OnjParserException.fromErrorMessage(
                    nameToken.char,
                    code,
                    "Variable '$varName' is imported here, but was already defined",
                    filename
                )
            }
            variables[varName] = variable
        }
    }

    private fun parseVariableDeclaration(export: Boolean) {
        val identifier = if (tryConsume(OnjTokenType.IDENTIFIER)) {
            last().literal as String
        } else {
            throw OnjParserException.fromErrorToken(last(), OnjTokenType.IDENTIFIER, code, filename)
        }
        val identifierToken = last()
        consume(OnjTokenType.EQUALS)
        val value = parseValue()
        if (variables.containsKey(identifier)) {
            throw OnjParserException.fromErrorMessage(
                identifierToken.char,
                code,
                "redefinition of variable '$identifier'",
                filename
            )
        }
        if (export) exports.add(identifier)
        variables[identifier] = value
    }

    private fun parseObject(startToken: OnjToken?): OnjObject {

        val values: MutableMap<String, OnjValue> = mutableMapOf()

        while (!tryConsume(OnjTokenType.R_BRACE)) {

            if (tryConsume(OnjTokenType.EOF)) {
                throw OnjParserException.fromErrorMessage(
                    startToken!!.char, code,
                    "Object is opened but never closed!", filename
                )
            }

            val key: String

            if (tryConsume(OnjTokenType.IDENTIFIER)) key = last().literal as String
            else if (tryConsume(OnjTokenType.STRING)) key = last().literal as String
            else if (tryConsume(OnjTokenType.DOT)) {

                val result = doTripleDot()

                if (result !is OnjObject) throw OnjParserException.fromErrorMessage(
                    last().char, code,
                    "Variable included via triple dot is not of type 'Object'", filename
                )

                for (pair in result.value.entries) {
                    if (values.containsKey(pair.key)) {
                        throw OnjParserException.fromErrorMessage(
                            last().char, code,
                            "Key '${pair.key}' included via Triple-Dot is already defined.", filename
                        )
                    }
                    values[pair.key] = pair.value
                }

                continue

            } else {
                throw OnjParserException.fromErrorToken(current(), "Identifier or String-Identifier", code, filename)
            }

            if (values.containsKey(key)) {
                throw OnjParserException.fromErrorMessage(last().char, code, "Key '$key' is already defined.", filename)
            }

            consume(OnjTokenType.COLON)

            values[key] = parseValue()

            tryConsume(OnjTokenType.COMMA)

        }

        return OnjObject(values)
    }

    private fun parseArray(startToken: OnjToken): OnjArray {

        val values = mutableListOf<OnjValue>()

        while (!tryConsume(OnjTokenType.R_BRACKET)) {

            if (tryConsume(OnjTokenType.EOF))
                throw OnjParserException.fromErrorMessage(
                    startToken.char, code,
                    "Array is opened but never closed!", filename
                )

            if (tryConsume(OnjTokenType.DOT)) {

                val result = doTripleDot()

                if (result !is OnjArray) throw OnjParserException.fromErrorMessage(
                    last().char, code,
                    "Triple-Dot variable in array is not of type array.",
                    filename
                )

                for (dotValue in result.value) values.add(dotValue)
                continue
            }

            values.add(parseValue())

            tryConsume(OnjTokenType.COMMA)

        }

        return OnjArray(values)
    }


    private fun doTripleDot(): OnjValue {

        try {
            consume(OnjTokenType.DOT)
            consume(OnjTokenType.DOT)
        } catch (e: OnjParserException) {
            throw OnjParserException.fromErrorMessage(
                last().char, code,
                "Expected triple-dot or identifier.", filename
            )
        }

        try {
            consume(OnjTokenType.EXCLAMATION)
        } catch (e: OnjParserException) {
            throw OnjParserException.fromErrorMessage(
                last().char, code,
                "Expected Variable after triple-Dot.", filename
            )
        }

        consume(OnjTokenType.IDENTIFIER)

        return variables[last().literal as String] ?: throw OnjParserException.fromErrorMessage(
            last().char, code,
            "Variable '${last().literal as String}' isn't defined.", filename
        )
    }

    private fun parseValue(): OnjValue {
        return if (tryConsume(OnjTokenType.INT)) OnjInt(last().literal as Long)
        else if (tryConsume(OnjTokenType.FLOAT)) OnjFloat(last().literal as Double)
        else if (tryConsume(OnjTokenType.STRING)) OnjString(last().literal as String)
        else if (tryConsume(OnjTokenType.BOOLEAN)) OnjBoolean(last().literal as Boolean)
        else if (tryConsume(OnjTokenType.NULL)) OnjNull()
        else if (tryConsume(OnjTokenType.L_BRACE)) parseObject(last())
        else if (tryConsume(OnjTokenType.L_BRACKET)) parseArray(last())
        else if (tryConsume(OnjTokenType.EXCLAMATION)) parseVariable()
        else if (tryConsume(OnjTokenType.L_PAREN)) {
            val result = parseCalculation()
            consume(OnjTokenType.R_PAREN)
            result
        }
        else if (tryConsume(OnjTokenType.DOLLAR)) parseNamedObject()
        else if (tryConsume(OnjTokenType.IDENTIFIER)) {
            val functionToken = last()
            if (!tryConsume(OnjTokenType.L_PAREN)) {
                throw OnjParserException.fromErrorToken(tokens[next], "function call", code, filename)
            }
            parseFunctionCall(functionToken)
        }
        else if (tryConsume(OnjTokenType.MINUS)) {
            if (tryConsume(OnjTokenType.INT)) OnjInt(-(last().literal as Long))
            else if (tryConsume(OnjTokenType.FLOAT)) OnjFloat(-(last().literal as Double))
            else throw OnjParserException.fromErrorToken(
                current(),
                "number",
                code,
                filename
            )
        }
        else throw OnjParserException.fromErrorToken(current(), "Value", code, filename)
    }

    private fun parseNamedObject(): OnjValue {
        consume(OnjTokenType.IDENTIFIER)
        val nameToken = last()
        val name = nameToken.literal as String
        consume(OnjTokenType.L_BRACE)
        val body = parseObject(last())
        return OnjNamedObject(name, body.value)
    }

    private fun parseVariable(): OnjValue {

        if (!tryConsume(OnjTokenType.IDENTIFIER)) {
            throw OnjParserException.fromErrorToken(last(), OnjTokenType.IDENTIFIER, code, filename)
        }

        val name = last().literal as String

        var value = variables[name] ?: throw OnjParserException.fromErrorMessage(
            last().char,
            code,
            "Variable '$name' isn't defined.",
            filename
        )

        while (tryConsume(OnjTokenType.DOT)) {
            val start = next
            val calResult = if (tryConsume(OnjTokenType.L_PAREN)) {
                val result = parseCalculation()
                consume(OnjTokenType.R_PAREN)
                result
            } else null
            if (
                calResult is OnjString ||
                (calResult == null && (tryConsume(OnjTokenType.IDENTIFIER) || tryConsume(OnjTokenType.STRING)))
            ) {

                if (value !is OnjObject) throw OnjParserException.fromErrorMessage(
                    last().char,
                    code,
                    "Can only get an identifier from an object, but found ${value::class.simpleName}",
                    filename
                )

                val varName = if (calResult == null) last().literal as String else calResult.value as String

                value = value[varName] ?: throw OnjParserException.fromErrorMessage(
                    last().char,
                    code,
                    "Identifier $varName was not defined",
                    filename
                )

            } else if (
                calResult is OnjInt ||
                (calResult == null && tryConsume(OnjTokenType.INT))
            ) {

                if (value !is OnjArray) throw OnjParserException.fromErrorMessage(
                    last().char,
                    code,
                    "Can only get a number from an array, but found ${value::class.simpleName}",
                    filename
                )

                val index = if (calResult == null) last().literal as Long else calResult.value as Long

                if (index !in value.value.indices) throw OnjParserException.fromErrorMessage(
                    last().char,
                    code,
                    "Index $index is out of bounds for length ${value.value.size}",
                    filename
                )

                value = value[index.toInt()]

            } else {

                if (calResult != null) throw OnjParserException.fromErrorMessage(
                    tokens[start].char,
                    code,
                    "Result of calculation must either be a string or an int",
                    filename
                )

                next--
                break
            }
        }

        return value
    }

    private fun parseCalculation(): OnjValue {
        val res = parseTerm()
        return res
    }

    private fun parseFunctionCall(functionToken: OnjToken): OnjValue {
        val args = mutableListOf<OnjValue>()
        while (true) {
            args.add(parseCalculation())
            if (tryConsume(OnjTokenType.R_PAREN)) break
            consume(OnjTokenType.COMMA)
        }
        val function = OnjConfig.getFunction(functionToken.literal as String, args)
        function ?: throw OnjParserException.fromErrorMessage(
            functionToken.char,
            code,
            "Cannot find function ${functionToken.literal}",
            filename
        )
        try {
            return function(args)
        } catch (e: Throwable) {
            throw OnjParserException.fromErrorMessage(
                functionToken.char,
                code,
                "the jvm code of the function threw an error:\n${e.message}",
                filename
            )
        }
    }

    private fun parseTerm(): OnjValue {
        var left = parseFactor()
        while (true) {
            if (tryConsume(OnjTokenType.PLUS)) {

                val token = last()
                val right = parseFactor()
                val func = OnjConfig.getFunction("operator%plus", listOf(left, right)) ?:
                    throw OnjParserException.fromErrorMessage(
                        token.char,
                        code,
                        "cannot add types ${left::class.simpleName} and ${left::class.simpleName}",
                        filename
                    )

                left = func(listOf(left, right))

            } else if (tryConsume(OnjTokenType.MINUS)) {

                val token = last()
                val right = parseFactor()
                val func = OnjConfig.getFunction("operator%minus", listOf(left, right)) ?:
                    throw OnjParserException.fromErrorMessage(
                        token.char,
                        code,
                        "cannot subtract types ${left::class.simpleName} and ${left::class.simpleName}",
                        filename
                    )

                left = func(listOf(left, right))

            } else break
        }
        return left
    }

    private fun parseFactor(): OnjValue {
        var left = parseTypeConvert()
        while (true) {
            if (tryConsume(OnjTokenType.STAR)) {

                val token = last()
                val right = parseLiteral()
                val func = OnjConfig.getFunction("operator%mult", listOf(left, right)) ?:
                    throw OnjParserException.fromErrorMessage(
                        token.char,
                        code,
                        "cannot multiply types ${left::class.simpleName} and ${left::class.simpleName}",
                        filename
                    )

                left = func(listOf(left, right))


            } else if (tryConsume(OnjTokenType.DIV)) {

                val token = last()
                val right = parseLiteral()
                val func = OnjConfig.getFunction("operator%div", listOf(left, right)) ?:
                    throw OnjParserException.fromErrorMessage(
                        token.char,
                        code,
                        "cannot divide types ${left::class.simpleName} and ${left::class.simpleName}",
                        filename
                    )

                left = func(listOf(left, right))


            } else break
        }
        return left
    }

    private fun parseTypeConvert(): OnjValue {
        var toConvert = parseUnary()
        while (tryConsume(OnjTokenType.HASH)) {
            consume(OnjTokenType.IDENTIFIER)
            val convertTo = last()
            toConvert = when (convertTo.literal as String) {
                "i", "I" -> {
                    if (!toConvert.isInt() && !toConvert.isFloat()) throw OnjParserException.fromErrorMessage(
                        convertTo.char, code, "cannot convert ${toConvert::class.simpleName} to int", filename
                    )
                    OnjInt((toConvert.value as Number).toLong())
                }
                "f", "F" -> {
                    if (!toConvert.isInt() && !toConvert.isFloat()) throw OnjParserException.fromErrorMessage(
                        convertTo.char, code, "cannot convert ${toConvert::class.simpleName} to float", filename
                    )
                    OnjFloat((toConvert.value as Number).toDouble())
                }
                "s", "S" -> {
                    OnjString(toConvert.toString())
                }
                else -> throw OnjParserException.fromErrorMessage(
                    convertTo.char, code,
                    "unknown type specifier ${convertTo.literal}",
                    filename
                )
            }
        }
        return toConvert
    }

    private fun parseUnary(): OnjValue {
        if (!tryConsume(OnjTokenType.MINUS) && !tryConsume(OnjTokenType.PLUS)) return parseLiteral()
        val op = last()
        return when (val result = parseUnary()) {

            is OnjInt -> if (op.type == OnjTokenType.MINUS) OnjInt(-result.value) else result
            is OnjFloat -> if (op.type == OnjTokenType.MINUS) OnjFloat(-result.value) else result

            else -> throw OnjParserException.fromErrorMessage(
                op.char,
                code,
                "unary operators can only be applied to ints and floats, found ${result::class.simpleName}",
                filename
            )

        }
    }

    private fun parseLiteral(): OnjValue {
        return if (tryConsume(OnjTokenType.INT)) OnjInt(last().literal as Long)
        else if (tryConsume(OnjTokenType.FLOAT)) OnjFloat(last().literal as Double)
        else if (tryConsume(OnjTokenType.STRING)) OnjString(last().literal as String)
        else if (tryConsume(OnjTokenType.BOOLEAN)) OnjBoolean(last().literal as Boolean)
        else if (tryConsume(OnjTokenType.NULL)) OnjNull()
        else if (tryConsume(OnjTokenType.EXCLAMATION)) parseVariable()
        else if (tryConsume(OnjTokenType.IDENTIFIER)) {
            val functionToken = last()
            if (!tryConsume(OnjTokenType.L_PAREN)) {
                throw OnjParserException.fromErrorToken(tokens[next], "function call", code, filename)
            }
            parseFunctionCall(functionToken)
        }
        else if (tryConsume(OnjTokenType.L_PAREN)) {
            val toRet = parseTerm()
            consume(OnjTokenType.R_PAREN)
            toRet
        }
        else throw OnjParserException.fromErrorToken(current(), "Value", code, filename)
    }

    private fun consume(type: OnjTokenType) {
        if (current().isType(type)) next++
        else throw OnjParserException.fromErrorToken(tokens[next], type, code, filename)
    }

    private fun tryConsume(type: OnjTokenType): Boolean {
        return if (current().isType(type)) {
            next++
            true
        } else false
    }

    private fun last(): OnjToken = tokens[next - 1]

    private fun current(): OnjToken = tokens[next]

    companion object {

        /**
         * reads a file and parses it
         * @throws [java.io.IOException] [OnjParserException]
         * @return the parsed onj-structure
         */
        fun parseFile(path: String): OnjObject {
            val code = File(Paths.get(path).toUri()).bufferedReader().use { it.readText() }
            return OnjParser().parse(OnjTokenizer().tokenize(code, path), code, path)
        }

        /**
         * reads a file and parses it
         * @throws [java.io.IOException] [OnjParserException]
         * @return the parsed onj-structure
         */
        fun parseFile(file: File): OnjObject {
            val code = file.bufferedReader().use { it.readText() }
            return OnjParser().parse(OnjTokenizer().tokenize(code, file.canonicalPath), code, file.canonicalPath)
        }

        /**
         * parses a string
         * @throws [OnjParserException]
         * @return the parsed onj-structure
         */
        fun parse(code: String): OnjObject {
            return OnjParser().parse(OnjTokenizer().tokenize(code, "anonymous"), code, "anonymous")
        }

    }

}
