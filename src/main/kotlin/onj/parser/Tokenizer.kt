package onj.parser

import kotlin.math.pow

internal class Tokenizer(
    private val code: String,
    private val fileName: String,
    private val isSchema: Boolean
) {

    private var next = 0

    fun tokenize(): List<OnjToken> {
        val tokens = mutableListOf<OnjToken>()
        while (!end()) next()?.let { tokens.add(it) }
        tokens.add(OnjToken(OnjTokenType.EOF, null, code.length))
        return tokens
    }

    private fun next(): OnjToken? = when (consume()) {

        ' ', '\t', '\n', '\r' -> null
        '+' -> OnjToken(OnjTokenType.PLUS, last().toString(), next - 1)
        '-' -> OnjToken(OnjTokenType.MINUS, last().toString(), next - 1)
        '*' -> OnjToken(OnjTokenType.STAR, last().toString(), next - 1)
        '#' -> OnjToken(OnjTokenType.HASH, last().toString(), next - 1)
        '$' -> OnjToken(OnjTokenType.DOLLAR, last().toString(), next - 1)
        '!' -> OnjToken(OnjTokenType.EXCLAMATION, last().toString(), next - 1)
        '=' -> OnjToken(OnjTokenType.EQUALS, last().toString(), next - 1)
        '?' -> OnjToken(OnjTokenType.QUESTION, last().toString(), next - 1)
        ':' -> OnjToken(OnjTokenType.COLON, last().toString(), next - 1)
        '.' -> OnjToken(OnjTokenType.DOT, last().toString(), next - 1)
        ',' -> OnjToken(OnjTokenType.COMMA, last().toString(), next - 1)
        ';' -> OnjToken(OnjTokenType.SEMICOLON, last().toString(), next - 1)
        '(' -> OnjToken(OnjTokenType.L_PAREN, last().toString(), next - 1)
        ')' -> OnjToken(OnjTokenType.R_PAREN, last().toString(), next - 1)
        '{' -> OnjToken(OnjTokenType.L_BRACE, last().toString(), next - 1)
        '}' -> OnjToken(OnjTokenType.R_BRACE, last().toString(), next - 1)
        '[' -> OnjToken(OnjTokenType.L_BRACKET, last().toString(), next - 1)
        ']' -> OnjToken(OnjTokenType.R_BRACKET, last().toString(), next - 1)
        '<' -> OnjToken(OnjTokenType.L_SHARP, last().toString(), next - 1)
        '>' -> OnjToken(OnjTokenType.R_SHARP, last().toString(), next - 1)
        '\'', '"' -> string(last())

        '/' -> {
            if (tryConsume('/')) {
                lineComment()
                null
            } else if (tryConsume('*')) {
                blockComment()
                null
            } else {
                OnjToken(OnjTokenType.DIV, last().toString(), next - 1)
            }
        }

        else -> {
            if (last().isDigit()) {
                number()
            } else if (last().isLetter() || last() == '_') {
                identifier()
            } else throw OnjParserException.fromErrorMessage(
                next - 1,
                code,
                "Illegal Character '${code[next - 1]}'.",
                fileName
            )
        }

    }

    private fun identifier(): OnjToken {
        val start = next - 1
        while (!end()) {
            val next = consume()
            if (!next.isLetterOrDigit() && next != '_') {
                backtrack()
                break
            }
        }
        val end = next
        val identifier = code.substring(start, end)

        if (isSchema) when (identifier.lowercase()) {
            "int" -> return OnjToken(OnjTokenType.T_INT, null, start)
            "float" -> return OnjToken(OnjTokenType.T_FLOAT, null, start)
            "boolean" -> return OnjToken(OnjTokenType.T_BOOLEAN, null, start)
            "string" -> return OnjToken(OnjTokenType.T_STRING, null, start)
        }

        return when (identifier.lowercase()) {
            "var" -> OnjToken(OnjTokenType.VAR, null, start)
            "import" -> OnjToken(OnjTokenType.IMPORT, null, start)
            "null" -> OnjToken(OnjTokenType.NULL, null, start)
            "use" -> OnjToken(OnjTokenType.USE, null, start)
            else -> OnjToken(OnjTokenType.IDENTIFIER, identifier, start)
        }
    }

    private fun number(): OnjToken {
        backtrack()
        val start = next
        var radix = 10
        if (tryConsume('0')) {
            if (tryConsume('x')) {
                radix = 16
            } else if (tryConsume('b')) {
                radix = 2
            } else if (tryConsume('o')) {
                radix = 8
            } else {
                backtrack()
            }
        }
        var num = 0L
        while (!end()) {
            val next = consume()
            if (next == '_') continue
            val digit = try {
                next.digitToInt(radix)
            } catch (e: IllegalArgumentException) {
                backtrack()
                break
            }
            num *= radix
            num += digit
        }
        if (radix != 10 || !tryConsume('.')) return OnjToken(OnjTokenType.INT, num, start)
        var afterComma = 0.0
        var digitCount = 1
        var isFirstIteration = true
        while (!end()) {
            val next = consume()
            if (next == '_') continue
            val digit = try {
                next.digitToInt(10)
            } catch (e: IllegalArgumentException) {
                if (isFirstIteration) {
                    backtrack()
                    backtrack()
                    return OnjToken(OnjTokenType.INT, num, start)
                }
                backtrack()
                return OnjToken(OnjTokenType.FLOAT, num + afterComma, start)
            }
            afterComma += digit / (10.0.pow(digitCount))
            digitCount++
            isFirstIteration = false
        }
        return OnjToken(OnjTokenType.FLOAT, num + afterComma, start)
    }

    private fun string(endChar: Char): OnjToken {
        val start = next - 1
        val string = StringBuilder()

        if (end()) {
            throw OnjParserException.fromErrorMessage(
                start, code,
                "String is opened but never closed!",
                fileName
            )
        }
        while (!tryConsume(endChar)) {
            consume()
            if (last() == '\\') {
                if (end()) {
                    throw OnjParserException.fromErrorMessage(
                        start, code,
                        "String is opened but never closed!",
                        fileName
                    )
                }
                string.append(when (consume()) {
                    'n' -> "\n"
                    'r' -> "\r"
                    't' -> "\t"
                    '"' -> "\""
                    '\'' -> "\'"
                    '\\' -> "\\"
                    else -> throw OnjParserException.fromErrorMessage(
                        next - 1, code,
                        "Unrecognized Escape-character '${last()}'",
                        fileName
                    )
                })
            } else string.append(last())
            if (end()) {
                throw OnjParserException.fromErrorMessage(
                    start, code,
                    "String is opened but never closed!",
                    fileName
                )
            }
        }
        return OnjToken(OnjTokenType.STRING, string.toString(), start)
    }

    private fun lineComment() {
        while (!end() && !tryConsume('\n') && !tryConsume('\r')) consume()
    }

    private fun blockComment() {
        while (!end()) {
            if (!tryConsume('*')) {
                consume()
                continue
            }
            if (!tryConsume('/')) {
                consume()
                continue
            }
            return
        }
    }


    private fun backtrack(): Unit = run { next-- }

    private fun consume(): Char = code[next++]

    private fun end(): Boolean = next >= code.length

    private fun tryConsume(c: Char): Boolean {
        if (peek() != c) return false
        consume()
        return true
    }

    private fun last(): Char = code[next - 1]

    private fun peek(): Char = code[next]

}


internal data class OnjToken(val type: OnjTokenType, val literal: Any?, val char: Int) {

    fun isType(type: OnjTokenType): Boolean = this.type == type

    fun isType(vararg types: OnjTokenType): Boolean = type in types

    override fun toString(): String {
        return "TOKEN($type, $literal @ $char)"
    }

}

enum class OnjTokenType {
    L_BRACE, R_BRACE, L_PAREN, R_PAREN, L_BRACKET, R_BRACKET, L_SHARP, R_SHARP,
    COMMA, COLON, EQUALS, EXCLAMATION, QUESTION, STAR, DOT, PLUS, MINUS, DIV, DOLLAR, HASH, SEMICOLON,
    IDENTIFIER, STRING, INT, FLOAT, BOOLEAN, NULL,
    T_INT, T_BOOLEAN, T_STRING, T_FLOAT,
    IMPORT, VAR, USE,
    EOF
}
