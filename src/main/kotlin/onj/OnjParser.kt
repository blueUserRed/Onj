package onj

import java.io.File
import java.nio.file.Paths

class OnjParser {

    private var next: Int = 0
    private var tokens: List<OnjToken> = listOf()
    private var code: String = ""
    private var filename: String = ""

    private val variables: MutableMap<String, OnjValue> = mutableMapOf()

    private fun parse(tokens: List<OnjToken>, code: String, filename: String): OnjValue {
        next = 0
        this.tokens = tokens
        this.code = code
        this.filename = filename
        this.variables.clear()

        parseVariables()

        return  if (tryConsume(OnjTokenType.L_BRACE)) parseObject(false, tokens[next])
                else if (tryConsume(OnjTokenType.L_BRACKET)) parseArray(tokens[next])
                else parseObject(true, null)
    }

    private fun parseVariables() {
        while (tryConsume(OnjTokenType.EXCLAMATION)) {

            val identifier = if (tryConsume(OnjTokenType.IDENTIFIER)) {
                last().literal as String
            } else throw OnjParserException.fromErrorToken(last(), OnjTokenType.IDENTIFIER, code, filename)

            consume(OnjTokenType.EQUALS)

            val value = parseValue()

            variables[identifier] = value
        }
    }

    private fun parseObject(implicitEnd: Boolean, startToken: OnjToken?): OnjObject {

        val values: MutableMap<String, OnjValue> = mutableMapOf()

        while (!tryConsume(OnjTokenType.R_BRACE)) {

            if (tryConsume(OnjTokenType.EOF)) {
                if (implicitEnd) break
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
                throw OnjParserException.fromErrorToken(last(), "Identifier or String-Identifier.", code, filename)
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
        else if (tryConsume(OnjTokenType.L_BRACE)) parseObject(false, last())
        else if (tryConsume(OnjTokenType.L_BRACKET)) parseArray(last())
        else if (tryConsume(OnjTokenType.EXCLAMATION)) parseVariable()
        else if (tryConsume(OnjTokenType.L_SHARP)) parseCalculation()
        else throw OnjParserException.fromErrorToken(current(), "Value", code, filename)
    }

    private fun parseVariable(): OnjValue {

        if (!tryConsume(OnjTokenType.IDENTIFIER))
            throw OnjParserException.fromErrorToken(last(), OnjTokenType.IDENTIFIER, code, filename)

        val name = last().literal as String

        return variables[name] ?: throw OnjParserException.fromErrorMessage(
            last().char,
            code,
            "Variable '$name' isn't defined.",
            filename
        )
    }

    private fun parseCalculation(): OnjValue {
        val res = parseTerm()
        consume(OnjTokenType.R_SHARP)
        return res
    }

    private fun parseTerm(): OnjValue {
        var left = parseFactor()
        while (true) {
            if (tryConsume(OnjTokenType.PLUS)) {
                val right = parseFactor()
                if (left.isInt() && right.isInt()) left = OnjInt((left as OnjInt).value + (right as OnjInt).value)
                else if (left.isFloat() && right.isFloat()) left = OnjFloat((left as OnjFloat).value + (right as OnjFloat).value)
                else if (left.isInt() && right.isFloat()) left = OnjFloat((left as OnjInt).value + (right as OnjFloat).value)
                else if (left.isFloat() && right.isInt()) left = OnjFloat((left as OnjFloat).value + (right as OnjInt).value)
            } else if (tryConsume(OnjTokenType.MINUS)) {
                val right = parseFactor()
                if (left.isInt() && right.isInt()) left = OnjInt((left as OnjInt).value - (right as OnjInt).value)
                else if (left.isFloat() && right.isFloat()) left = OnjFloat((left as OnjFloat).value - (right as OnjFloat).value)
                else if (left.isInt() && right.isFloat()) left = OnjFloat((left as OnjInt).value - (right as OnjFloat).value)
                else if (left.isFloat() && right.isInt()) left = OnjFloat((left as OnjFloat).value - (right as OnjInt).value)
            } else break
        }
        return left
    }

    private fun parseFactor(): OnjValue {
        var left = parseLiteral()
        while (true) {
            //TODO: this can probably be done better
            if (tryConsume(OnjTokenType.STAR)) {
                val right = parseLiteral()
                if (left.isInt() && right.isInt()) left = OnjInt((left as OnjInt).value * (right as OnjInt).value)
                else if (left.isFloat() && right.isFloat()) left = OnjFloat((left as OnjFloat).value * (right as OnjFloat).value)
                else if (left.isInt() && right.isFloat()) left = OnjFloat((left as OnjInt).value * (right as OnjFloat).value)
                else if (left.isFloat() && right.isInt()) left = OnjFloat((left as OnjFloat).value * (right as OnjInt).value)
            } else if (tryConsume(OnjTokenType.DIV)) {
                val right = parseLiteral()
                if (left.isInt() && right.isInt()) left = OnjInt((left as OnjInt).value / (right as OnjInt).value)
                else if (left.isFloat() && right.isFloat()) left = OnjFloat((left as OnjFloat).value / (right as OnjFloat).value)
                else if (left.isInt() && right.isFloat()) left = OnjFloat((left as OnjInt).value / (right as OnjFloat).value)
                else if (left.isFloat() && right.isInt()) left = OnjFloat((left as OnjFloat).value / (right as OnjInt).value)
            } else break
        }
        return left
    }

    private fun parseLiteral(): OnjValue {
        return if (tryConsume(OnjTokenType.INT)) OnjInt(last().literal as Long)
        else if (tryConsume(OnjTokenType.FLOAT)) OnjFloat(last().literal as Double)
        else if (tryConsume(OnjTokenType.STRING)) OnjString(last().literal as String)
        else if (tryConsume(OnjTokenType.BOOLEAN)) OnjBoolean(last().literal as Boolean)
        else if (tryConsume(OnjTokenType.NULL)) OnjNull()
        else if (tryConsume(OnjTokenType.EXCLAMATION)) parseVariable()
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

        fun parseFile(path: String): OnjValue {
            val code = File(Paths.get(path).toUri()).bufferedReader().use { it.readText() }
            return OnjParser().parse(OnjTokenizer().tokenize(code, path), code, path)
        }

        fun parse(code: String): OnjValue {
            return OnjParser().parse(OnjTokenizer().tokenize(code, "anonymous"), code, "anonymous")
        }

    }

}

class OnjSchemaParser {

    private var next: Int = 0
    private var tokens: List<OnjToken> = listOf()
    private var code: String = ""
    private var filename: String = ""

    private val variables: MutableMap<String, OnjSchema> = mutableMapOf()

    private fun parseSchema(tokens: List<OnjToken>, code: String, filename: String): OnjSchema {
        next = 0
        this.tokens = tokens
        this.code = code
        this.filename = filename
        this.variables.clear()

        parseVariables()

        if (tryConsume(OnjTokenType.L_BRACE)) return parseObject(false, tokens[next], false)
        if (tryConsume(OnjTokenType.L_BRACKET)) return parseArray(tokens[next], false)

        return parseObject(true, null, true)
    }


    private fun parseVariables() {

        while (tryConsume(OnjTokenType.EXCLAMATION)) {

            val identifier = if (tryConsume(OnjTokenType.IDENTIFIER)) {
                last().literal as String
            } else throw OnjParserException.fromErrorToken(last(), OnjTokenType.IDENTIFIER, code, filename)

            consume(OnjTokenType.EQUALS)

            val value = parseValue()

            variables[identifier] = value
        }
    }

    private fun parseObject(implicitEnd: Boolean, startToken: OnjToken?, nullable: Boolean): OnjSchema {

        val keys: MutableMap<String, OnjSchema> = mutableMapOf()
        val optionalKeys: MutableMap<String, OnjSchema> = mutableMapOf()
        var allowsAdditional = false

        while (!tryConsume(OnjTokenType.R_BRACE)) {

            if (tryConsume(OnjTokenType.EOF)) {
                if (implicitEnd) break
                throw OnjParserException.fromErrorMessage(
                    startToken!!.char, code,
                    "Object is opened but never closed!", filename
                )
            }

            val key: String
            if (tryConsume(OnjTokenType.IDENTIFIER)) key = last().literal as String
            else if (tryConsume(OnjTokenType.STRING)) key = last().literal as String
            else if (tryConsume(OnjTokenType.DOT)) {

                val pref = next
                if (
                    tryConsume(OnjTokenType.DOT) &&
                    tryConsume(OnjTokenType.DOT) &&
                    tryConsume(OnjTokenType.STAR)
                ) {
                    allowsAdditional = true
                    continue
                }
                next = pref

                val result = doTripleDot()

                if (result !is OnjSchemaObject) throw OnjParserException.fromErrorMessage(
                    last().char, code,
                    "Variable included via triple dot is not of type 'Object'", filename
                )

                for (pair in result.keys.entries) {
                    if (keys.containsKey(pair.key)) throw OnjParserException.fromErrorMessage(
                        last().char, code,
                        "Key '${pair.key}' included via Triple-Dot is already defined.", filename
                    )
                    keys[pair.key] = pair.value
                }

                continue

            } else {
                throw OnjParserException.fromErrorToken(last(), "Identifier or String-Identifier", code, filename)
            }

            if (keys.containsKey(key)) {
                throw OnjParserException.fromErrorMessage(last().char, code, "Key '$key' is already defined", filename)
            }

            val isOptional = tryConsume(OnjTokenType.QUESTION)

            consume(OnjTokenType.COLON)

            if (isOptional) optionalKeys[key] = parseValue() else keys[key] = parseValue()

            tryConsume(OnjTokenType.COMMA)

        }

        return OnjSchemaObject(nullable, keys, optionalKeys, allowsAdditional)
    }

    private fun parseArray(startToken: OnjToken, nullable: Boolean): OnjSchemaArray {

        val values = mutableListOf<OnjSchema>()

        while (!tryConsume(OnjTokenType.R_BRACKET)) {

            if (tryConsume(OnjTokenType.EOF))
                throw OnjParserException.fromErrorMessage(
                    startToken.char, code,
                    "Array is opened but never closed!", filename
                )

            if (tryConsume(OnjTokenType.DOT)) {

                val result = doTripleDot()

                if (result !is OnjSchemaArray) throw OnjParserException.fromErrorMessage(
                    last().char, code,
                    "Triple-Dot variable in array is not of type array.", filename
                )

                for (dotValue in result.schemas) values.add(dotValue)
                continue
            }

            values.add(parseValue())

            tryConsume(OnjTokenType.COMMA)

        }

        return OnjSchemaArray(nullable, values)
    }


    private fun doTripleDot(): OnjSchema {

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

        val toRet = variables[last().literal as String] ?: throw OnjParserException.fromErrorMessage(
            last().char, code,
            "Variable '${last().literal as String}' isn't defined.", filename
        )

        if (toRet.nullable) throw OnjParserException.fromErrorMessage(
            last().char,
            code,
            "Variable included via triple dot is not allowed to be nullable",
            filename
        )

        return toRet
    }

    private fun parseValue(): OnjSchema {
        if (tryConsume(OnjTokenType.L_BRACE)) return parseObject(false, last(), false)
        else if (tryConsume(OnjTokenType.L_BRACKET)) return parseArray(last(), false)
        else if (tryConsume(OnjTokenType.QUESTION)) {
            if (tryConsume(OnjTokenType.L_BRACE)) return parseObject(false, last(), true)
            else if (tryConsume(OnjTokenType.L_BRACKET)) return parseArray(last(), true)
            else throw OnjParserException.fromErrorToken(last(), "object or array", code, filename)
        }
        else return parseArrayDec()
    }

    private fun parseArrayDec(): OnjSchema {
        var left = parseLiteral()
        while (tryConsume(OnjTokenType.L_BRACKET)) {
            if (tryConsume(OnjTokenType.STAR)) {
                left = OnjSchemaArray(false, -1, left)
            } else {
                consume(OnjTokenType.INT)
                val size = last().literal as Int
                if (size < 0) throw OnjParserException.fromErrorMessage(
                    last().char,
                    code,
                    "Array size must be greater than zero",
                    filename
                )
                left = OnjSchemaArray(false, size, left)
            }
            consume(OnjTokenType.R_BRACKET)
            if (tryConsume(OnjTokenType.QUESTION)) left.nullable = true
        }
        return left
    }

    private fun parseLiteral(): OnjSchema {

        if (tryConsume(OnjTokenType.EXCLAMATION)) return parseSchemaVariable()
        else if (tryConsume(OnjTokenType.STAR)) return OnjSchemaAny()

        consume(OnjTokenType.IDENTIFIER)
        val s = when ((last().literal as String).lowercase()) {
            "int" -> OnjSchemaInt(false)
            "float" -> OnjSchemaFloat(false)
            "string" -> OnjSchemaString(false)
            "boolean" -> OnjSchemaBoolean(false)
            else -> throw OnjParserException.fromErrorToken(last(), "type", code, filename)
        }
        if (tryConsume(OnjTokenType.QUESTION)) s.nullable = true
        return s
    }

    private fun parseSchemaVariable(): OnjSchema {

        if (!tryConsume(OnjTokenType.IDENTIFIER))
            throw OnjParserException.fromErrorToken(last(), OnjTokenType.IDENTIFIER, code, filename)

        val name = last().literal as String

        var schema = variables[name] ?: throw OnjParserException.fromErrorMessage(
            last().char, code,
            "Variable '$name' isn't defined.", filename
        )

        if (tryConsume(OnjTokenType.QUESTION)) schema = schema.getAsNullable()


        if (tryConsume(OnjTokenType.L_BRACKET)) {
            val amount = if (tryConsume(OnjTokenType.STAR)) -1
            else if (tryConsume(OnjTokenType.INT)) last().literal as Int
            else throw OnjParserException.fromErrorMessage(
                last().char, code,
                "Expected number or star.", filename
            )

            consume(OnjTokenType.R_BRACKET)

            schema = if (tryConsume(OnjTokenType.QUESTION)) OnjSchemaArray(true, amount, schema)
            else OnjSchemaArray(false, amount, schema)

        }

        return schema
    }

    private fun consume(type: OnjTokenType) {
        if (current().isType(type)) next++
        else throw OnjParserException.fromErrorToken(tokens[next], type, code, filename)
    }

    private fun consume(): OnjToken {
        next++
        return last()
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

        fun parseFile(path: String): OnjSchema {
            val code = File(Paths.get(path).toUri()).bufferedReader().use { it.readText() }
            return OnjSchemaParser().parseSchema(OnjTokenizer().tokenize(code, path), code, path)
        }

        fun parse(code: String): OnjSchema {
            return OnjSchemaParser().parseSchema(OnjTokenizer().tokenize(code, "anonymous"), code, "anonymous")
        }

    }

}

class OnjParserException(message: String, cause: Exception?) : RuntimeException(message, cause) {

    internal constructor(message: String) : this(message, null)

    internal companion object {

        fun fromErrorToken(
            errorToken: OnjToken,
            expectedTokenType: OnjTokenType,
            code: String,
            filename: String
        ): OnjParserException {
            return fromErrorMessage(
                errorToken.char, code,
                "Unexpected Token '${errorToken.type}', expected '${expectedTokenType}'\u001B[0m\n", filename
            )
        }

        fun fromErrorToken(errorToken: OnjToken, expected: String, code: String, filename: String): OnjParserException {
            return fromErrorMessage(
                errorToken.char, code,
                "Unexpected Token '${errorToken.type}', expected $expected.\u001B[0m\n", filename
            )
        }

        fun fromErrorMessage(charPos: Int, code: String, message: String, filename: String): OnjParserException {
            val messageBuilder = StringBuilder()
            val result = getLine(charPos, code)
            messageBuilder
                .append("\u001B[37m\n\nError in file $filename on line ${result.second}, on position: ${result.third}\n")
                .append(result.first)
                .append("\n")
            for (i in 1 until result.third) messageBuilder.append(" ")
            messageBuilder.append(" ^------ $message\u001B[0m\n")
            return OnjParserException(messageBuilder.toString())
        }


        private fun getLine(charPos: Int, code: String): Triple<String, Int, Int> {
            val c = code + "\n" //lol
            var lineCount = 0
            var cur = 0
            var lastLineStart = 0
            var searchedLineStart = -1
            var searchedLineEnd = 0
            while (cur < c.length) {
                if (cur >= charPos) searchedLineStart = lastLineStart
                if (c[cur] == '\r') {
                    cur++
                    if (cur < c.length && c[cur] == '\n') {
                        cur++
                        lineCount++
                        lastLineStart = cur
                        if (searchedLineStart != -1) {
                            searchedLineEnd = cur - 2
                            break
                        }
                        continue
                    }
                    lineCount++
                    lastLineStart = cur
                    if (searchedLineStart != -1) {
                        searchedLineEnd = cur - 1
                        break
                    }
                }
                if (c[cur] == '\n') {
                    cur++
                    lineCount++
                    lastLineStart = cur
                    if (searchedLineStart != -1) {
                        searchedLineEnd = cur - 1
                        break
                    }
                }
                cur++
            }
            return Triple(c.substring(searchedLineStart, searchedLineEnd), lineCount, charPos - searchedLineStart)
        }
    }

}
