package onj

import onj.*
import java.io.File
import java.nio.file.Paths

class OnjParser {

    private var next: Int = 0
    private var tokens: List<OnjToken> = listOf()
    private var code: String = ""
    private var filename: String = ""

    private val variables: MutableMap<String, Any> = mutableMapOf()

    @Synchronized
    private fun parse(tokens: List<OnjToken>, code: String, filename: String): OnjValue {
        next = 0
        this.tokens = tokens
        this.code = code
        this.filename = filename
        this.variables.clear()

        parseVariables(false)

        return  if (tryConsume(OnjTokenType.L_BRACE)) parseObject(false, tokens[next], false).first!!
                else if (tryConsume(OnjTokenType.L_BRACKET)) parseArray(tokens[next], false).first!!
                else parseObject(true, null, false).first!!
    }

    private fun parseSchema(tokens: List<OnjToken>, code: String, filename: String): OnjSchema {
        next = 0
        this.tokens = tokens
        this.code = code
        this.filename = filename
        this.variables.clear()

        parseVariables(true)

        if (tryConsume(OnjTokenType.L_BRACE)) return parseObject(false, tokens[next], true).second!!
        if (tryConsume(OnjTokenType.L_BRACKET)) return parseArray(tokens[next], true).second!!

        return parseObject(true, null, true).second!!
    }


    private fun parseVariables(isSchema: Boolean) {

        while (tryConsume(OnjTokenType.EXCLAMATION)) {

            val identifier = if (tryConsume(OnjTokenType.IDENTIFIER)) {
                last().literal as String
            } else throw OnjParserException.fromErrorToken(last(), OnjTokenType.IDENTIFIER, code, filename)

            consume(OnjTokenType.EQUALS)

            val value: Any = if (!isSchema) parseValue()
            else parseSchemaValue()

            variables[identifier] = value
        }
    }

    private fun parseObject(
        implicitEnd: Boolean,
        startToken: OnjToken?,
        isSchema: Boolean,
        nullable: Boolean = false
    ): Pair<OnjObject?, OnjSchema?> {

        val values: MutableMap<String, Any> = mutableMapOf()

        while (!tryConsume(OnjTokenType.R_BRACE)) {

            if (tryConsume(OnjTokenType.EOF)) {
                if (implicitEnd) break
                throw OnjParserException.fromErrorMessage(
                    startToken!!.char, code,
                    "Object is opened but never closed!", filename
                )
            }

            val key: String = if (tryConsume(OnjTokenType.IDENTIFIER)) last().literal as String
            else if (tryConsume(OnjTokenType.STRING)) last().literal as String
            else if (tryConsume(OnjTokenType.DOT)) {

                val result = doTripleDot()

                if (!isSchema) {
                    if (result !is OnjObject) throw OnjParserException.fromErrorMessage(
                        last().char, code,
                        "Variable included via triple dot is not of type 'Object'", filename
                    )
                    for (pair in result.value.entries) {

                        if (values.containsKey(pair.key))
                            throw OnjParserException.fromErrorMessage(
                                last().char, code,
                                "Key '${pair.key}' included via Triple-Dot is already defined.", filename
                            )

                        values[pair.key] = pair.value

                    }

                    continue
                } else {
                    if (result !is OnjSchemaObject) throw OnjParserException.fromErrorMessage(
                        last().char, code,
                        "Variable included via triple dot is not of type 'Object'", filename
                    )

                    for (pair in result.schema.entries) {

                        if (values.containsKey(pair.key))
                            throw OnjParserException.fromErrorMessage(
                                last().char, code,
                                "Key '${pair.key}' included via Triple-Dot is already defined.", filename
                            )

                        values[pair.key] = pair.value

                    }

                    continue
                }

            } else
                throw OnjParserException.fromErrorToken(last(), "Identifier or String-Identifier.", code, filename)

            if (values.containsKey(key))
                throw OnjParserException.fromErrorMessage(last().char, code, "Key '$key' is already defined.", filename)

            consume(OnjTokenType.COLON)

            if (!isSchema) values[key] = parseValue()
            else values[key] = parseSchemaValue()

            tryConsume(OnjTokenType.COMMA)

        }

        return if (!isSchema) Pair(OnjObject(values as Map<String, OnjValue>), null)
        else Pair(null, OnjSchemaObject(nullable, values as Map<String, OnjSchema>))
    }

    private fun parseArray(
        startToken: OnjToken,
        isSchema: Boolean,
        nullable: Boolean = false
    ): Pair<OnjArray?, OnjSchemaArray?> {

        val values: MutableList<Any> = mutableListOf()

        while (!tryConsume(OnjTokenType.R_BRACKET)) {

            if (tryConsume(OnjTokenType.EOF))
                throw OnjParserException.fromErrorMessage(
                    startToken.char, code,
                    "Array is opened but never closed!", filename
                )

            if (tryConsume(OnjTokenType.DOT)) {

                val result = doTripleDot()

                if (result !is OnjArray) //TODO: fix like object
                    throw OnjParserException.fromErrorMessage(
                        last().char, code,
                        "Triple-Dot variable in array is not of type array.", filename
                    )

                for (dotValue in result.value) values.add(dotValue)
                continue
            }

            if (!isSchema) values.add(parseValue())
            else values.add(parseSchemaValue())

            tryConsume(OnjTokenType.COMMA)

        }

        return if (!isSchema) Pair(OnjArray(values as List<OnjValue>), null)
        else Pair(null, OnjSchemaArray(nullable, values as List<OnjSchema>))
    }


    private fun doTripleDot(): Any {

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
        else if (tryConsume(OnjTokenType.L_BRACE)) parseObject(false, last(), false).first!!
        else if (tryConsume(OnjTokenType.L_BRACKET)) parseArray(last(), false).first!!
        else if (tryConsume(OnjTokenType.EXCLAMATION)) parseVariable()
        else throw OnjParserException.fromErrorToken(current(), "Value", code, filename)
    }

    private fun parseSchemaValue(): OnjSchema {
        if (tryConsume(OnjTokenType.IDENTIFIER)) {
            val type = last()

            val isNullable = tryConsume(OnjTokenType.QUESTION)

            val schema = when ((type.literal as String).lowercase()) {
                "boolean" -> OnjSchemaBoolean(isNullable)
                "int" -> OnjSchemaInt(isNullable)
                "float" -> OnjSchemaFloat(isNullable)
                "string" -> OnjSchemaString(isNullable)
                else ->
                    throw OnjParserException.fromErrorMessage(
                        type.char, code,
                        "Unknown type '${type.literal}'", filename
                    )
            }

            if (tryConsume(OnjTokenType.L_BRACKET)) {

                val amount = if (tryConsume(OnjTokenType.STAR)) -1
                else if (tryConsume(OnjTokenType.INT)) {
                    val res = last().literal as Int
                    if (res < 0) throw OnjParserException.fromErrorMessage(
                        last().char, code,
                        "Array-length must be positive.", filename
                    )
                    res
                } else throw OnjParserException.fromErrorMessage(
                    last().char, code,
                    "Expected number or star.", filename
                )

                consume(OnjTokenType.R_BRACKET)

                return if (tryConsume(OnjTokenType.QUESTION)) {
                    OnjSchemaArray(true, amount, schema)
                } else OnjSchemaArray(false, amount, schema)

            }
            return schema
        } else if (tryConsume(OnjTokenType.L_BRACE))
            return parseObject(false, last(), true).second!!
        else if (tryConsume(OnjTokenType.EXCLAMATION)) return parseSchemaVariable()
        else if (tryConsume(OnjTokenType.L_BRACKET)) return parseArray(last(), true).second!!
        else if (tryConsume(OnjTokenType.QUESTION)) {

            if (tryConsume(OnjTokenType.L_BRACE))
                return parseObject(false, last(), isSchema = true, nullable = true).second!!
            if (tryConsume(OnjTokenType.L_BRACKET))
                return parseArray(last(), isSchema = true, nullable = true).second!!
            throw OnjParserException.fromErrorMessage(
                last().char, code,
                "Expected start of object or array!", filename
            )
        }

        throw OnjParserException.fromErrorMessage(current().char, code, "Expected type.", filename)
    }

    private fun parseVariable(): OnjValue {

        if (!tryConsume(OnjTokenType.IDENTIFIER))
            throw OnjParserException.fromErrorToken(last(), OnjTokenType.IDENTIFIER, code, filename)

        val name = last().literal as String

        return (variables[name] as OnjValue?) ?: throw OnjParserException.fromErrorMessage(
            last().char, code,
            "Variable '$name' isn't defined.", filename
        )
    }

    private fun parseSchemaVariable(): OnjSchema {

        if (!tryConsume(OnjTokenType.IDENTIFIER))
            throw OnjParserException.fromErrorToken(last(), OnjTokenType.IDENTIFIER, code, filename)

        val name = last().literal as String

        var schema = (variables[name] as OnjSchema?) ?: throw OnjParserException.fromErrorMessage(
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

        fun parseSchemaFile(path: String): OnjSchema {
            val code = File(Paths.get(path).toUri()).bufferedReader().use { it.readText() }
            return OnjParser().parseSchema(OnjTokenizer().tokenize(code, path), code, path)
        }

        fun parseSchema(code: String): OnjSchema {
            return OnjParser().parseSchema(OnjTokenizer().tokenize(code, "anonymous"), code, "anonymous")
        }

        fun printTokens(path: String) {
            val code = File(Paths.get(path).toUri()).bufferedReader().use { it.readText() }
            val tokens = OnjTokenizer().tokenize(code, path)
            tokens.forEach { println(it) }
        }

    }

}

class OnjParserException(message: String, cause: Exception?) : RuntimeException(message, cause) {

    constructor(message: String) : this(message, null)

    companion object {

        fun fromErrorToken(
            errorToken: OnjToken,
            expectedTokenType: OnjTokenType,
            code: String,
            filename: String
        ): OnjParserException {
            return fromErrorMessage(
                errorToken.char, code,
                "Unexpected Token '${errorToken.type}', expected '${expectedTokenType}'.\u001B[0m\n", filename
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