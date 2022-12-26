package onj.parser

import kotlin.math.pow

internal class OnjTokenizer {

    private var next: Int = 0
    private var start: Int = 0
    private val tokens: MutableList<OnjToken> = mutableListOf()
    private var code: String = ""
    private var filename: String = ""

    @Synchronized
    fun tokenize(code: String, filename: String): List<OnjToken> {
        this.code = code
        this.filename = filename

        while (next != code.length) {
            tokens.add(getCurrentToken() ?: continue)
        }

        tokens.add(OnjToken(OnjTokenType.EOF, null, code.length))
        return tokens
    }

    private fun getCurrentToken(): OnjToken? {
        return when(consume()) {
            '{' -> OnjToken(OnjTokenType.L_BRACE, null, next - 1)
            '}' -> OnjToken(OnjTokenType.R_BRACE, null, next - 1)
            '[' -> OnjToken(OnjTokenType.L_BRACKET, null, next - 1)
            ']' -> OnjToken(OnjTokenType.R_BRACKET, null, next - 1)
            '(' -> OnjToken(OnjTokenType.L_PAREN, null, next - 1)
            ')' -> OnjToken(OnjTokenType.R_PAREN, null, next - 1)
            '<' -> OnjToken(OnjTokenType.L_SHARP, null, next - 1)
            '>' -> OnjToken(OnjTokenType.R_SHARP, null, next - 1)
            ':' -> OnjToken(OnjTokenType.COLON, null, next - 1)
            ',' -> OnjToken(OnjTokenType.COMMA, null, next - 1)
            ';' -> OnjToken(OnjTokenType.SEMICOLON, null, next - 1)
            '!' -> OnjToken(OnjTokenType.EXCLAMATION, null, next - 1)
            '=' -> OnjToken(OnjTokenType.EQUALS, null, next - 1)
            '?' -> OnjToken(OnjTokenType.QUESTION, null, next - 1)
            '*' -> OnjToken(OnjTokenType.STAR, null, next - 1)
            '+' -> OnjToken(OnjTokenType.PLUS, null, next - 1)
            '-' -> OnjToken(OnjTokenType.MINUS, null, next - 1)
            '.' -> OnjToken(OnjTokenType.DOT, null, next - 1)
            '$' -> OnjToken(OnjTokenType.DOLLAR, null, next - 1)
            '#' -> OnjToken(OnjTokenType.HASH, null, next - 1)
            '"' -> getString('"')
            '\'' -> getString('\'')
            ' ', '\t', '\r', '\n' -> null

            else -> {
                if (last().isLetter() || last() == '_') getIdentifier()
                else if (last().isDigit() || code[next] == '-') getNumber()
                else if (last() == '/' && tryConsume('/')) {
                    comment()
                    null
                } else if (last() == '/' && tryConsume('*')) {
                    blockComment()
                    null
                } else if (last() == '/') {
                    OnjToken(OnjTokenType.DIV, null, next - 1)
                }
                else throw OnjParserException.fromErrorMessage(
                    next - 1,
                    code,
                    "Illegal Character '${code[next - 1]}'.",
                    filename
                )
            }
        }
    }

    private fun getString(endChar: Char): OnjToken {
        start = next
        val result: StringBuilder = StringBuilder()
        if (end()) {
            throw OnjParserException.fromErrorMessage(start, code, "String is opened but never closed!", filename)
        }
        while (consume() != endChar) {
            if (last() == '\\') {
                if (end()) {
                    throw OnjParserException.fromErrorMessage(start, code, "String is opened but never closed!", filename)
                }
                result.append(when (consume()) {
                    'n' -> "\n"
                    'r' -> "\r"
                    't' -> "\t"
                    '"' -> "\""
                    '\'' -> "\'"
                    '\\' -> "\\"
                    else -> throw OnjParserException.fromErrorMessage(next - 1, code,
                        "Unrecognized Escape-character '${last()}'", filename)
                })
            } else result.append(last())
            if (end() || (endChar != '\'' && last() == '\n')) {
                throw OnjParserException.fromErrorMessage(start, code, "String is opened but never closed!", filename)
            }
        }
        return OnjToken(OnjTokenType.STRING, result.toString(), start - 1)
    }

    private fun comment() {
        if (end()) throw OnjParserException.fromErrorMessage(next - 1, code,
            "Illegal Character '${code[next - 1]}!'", filename)
        while (!end() && code[next++] !in arrayOf('\n', '\r'));
    }

    private fun blockComment() {
        while(!end()) {
            if (end()) break
            if (consume() != '*') continue
            if (end()) break
            if (tryConsume('/')) break
        }
    }

    private fun getIdentifier(): OnjToken {
        start = next - 1
        while (!end() && (consume().isLetterOrDigit() || last() == '_'));
        if (!end()) next--
//        next--
        val identifier = code.substring(start, next)
        return when(identifier.uppercase()) {
            "TRUE" -> OnjToken(OnjTokenType.BOOLEAN, true, start)
            "FALSE" -> OnjToken(OnjTokenType.BOOLEAN, false, start)
            "NULL" -> OnjToken(OnjTokenType.NULL, null, start)
            "POS_INFINITY" -> OnjToken(OnjTokenType.FLOAT, Double.POSITIVE_INFINITY, start)
            "NEG_INFINITY" -> OnjToken(OnjTokenType.FLOAT, Double.NEGATIVE_INFINITY, start)
            "NAN" -> OnjToken(OnjTokenType.FLOAT, Double.NaN, start)
//            "EXPORT" -> onj.builder.OnjToken(onj.builder.OnjTokenType.EXPORT, identifier, start)
            "IMPORT" -> OnjToken(OnjTokenType.IMPORT, identifier, start)
            "VAR" -> OnjToken(OnjTokenType.VAR, identifier, start)
            else -> OnjToken(OnjTokenType.IDENTIFIER, identifier, start)
        }
    }

    private fun getNumber(): OnjToken {

        next--

        val start = next

        val isNegative = tryConsume('-')

        var radix = 10

        if (tryConsume('0')) {
            if (tryConsume('b')) radix = 2
            else if (tryConsume('o')) radix = 8
            else if (tryConsume('x')) radix = 16
            else next--
        }

        var num = 0L

        while (!end()) {
            if (consume() == '_') continue
            if (!last().isLetterOrDigit()) break
            num *= radix
            try {
                num += last().digitToInt(radix)
            } catch (e: NumberFormatException) {
                break
            }
        }

        if (!end()) next--

        if (end() || radix != 10 || !tryConsume('.')) {
            return OnjToken(OnjTokenType.INT, if (isNegative) -num else num, start)
        }

        val dotIndex = next - 1

        var afterComma = 0.0
        var numIts = 1
        var isFirstIt = true
        var wasntFloat = false
        while(!end()) {
            if (consume() == '_') continue
            if (!last().isDigit()) {
                if (isFirstIt) next--
                if (isFirstIt) wasntFloat = true
                break
            }
            isFirstIt = false
            afterComma += last().digitToInt(10) / 10.0.pow(numIts)
            numIts++
        }
        next--

        if (wasntFloat) {
            next = dotIndex
            afterComma = 0.0
        }

        val commaNum = num + afterComma
        return if (wasntFloat) {
            OnjToken(OnjTokenType.INT, if (isNegative) -commaNum.toLong() else commaNum.toLong(), start)
        } else {
            OnjToken(OnjTokenType.FLOAT, if (isNegative) -commaNum else commaNum, start)
        }

    }

    private fun last(): Char = code[next - 1]
    private fun next(): Char = code[next]
    private fun consume(): Char {
        next++
        return last()
    }
    private fun tryConsume(char: Char): Boolean {
        return if (code[next] == char) {
            next++
            true
        } else false
    }

    private fun tryConsume(vararg chars: Char): Boolean {
        for (char in chars) if (tryConsume(char)) return true
        return false
    }

    private fun end(): Boolean = next == code.length

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
    IMPORT, VAR,
    EOF
}