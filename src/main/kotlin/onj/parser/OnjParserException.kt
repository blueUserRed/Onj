package onj.parser

import onj.OnjToken
import onj.OnjTokenType


class OnjParserException internal constructor(message: String, cause: Exception?) : RuntimeException(message, cause) {

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

        fun fromErrorMessage(
            charPos: Int,
            code: String,
            message: String,
            filename: String,
            cause: Exception? = null
        ): OnjParserException {
            val messageBuilder = StringBuilder()
            val result = getLine(charPos, code)
            messageBuilder
                .append("\u001B[37m\n\nError in file $filename on line ${result.second}, on position: ${result.third}\n")
                .append(result.first)
                .append("\n")
            for (i in 1..result.third) messageBuilder.append(" ")
            messageBuilder.append("^------ $message\u001B[0m\n")
            return OnjParserException(messageBuilder.toString(), cause)
        }


        private fun getLine(charPos: Int, code: String): Triple<String, Int, Int> {
            //TODO: judging by the code it was probably 3am when i wrote this

            val c = code + "\n" //lol
            var lineCount = 0
            var cur = 0
            var lastLineStart = 0
            var searchedLineStart = -1
            var searchedLineEnd = 0
            // this gives me nightmares every time I open this file
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