package onj.parser

import onj.*
import onj.OnjSchemaNamedObject
import onj.OnjToken
import onj.OnjTokenizer
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths


/**
 * used for parsing a .onjschema file
 */
class OnjSchemaParser private constructor(val previousFiles: List<Path> = listOf()) {

    private var next: Int = 0
    private var tokens: List<OnjToken> = listOf()
    private var code: String = ""
    private var filename: String = ""

    private val variables: MutableMap<String, OnjSchema> = mutableMapOf()
    private var namedObjects: MutableMap<String, List<OnjSchemaNamedObject>> = mutableMapOf()
    private val namedObjectGroupsToCheck: MutableList<OnjToken> = mutableListOf()
    private val allNamedObjectNames: MutableList<String> = mutableListOf()

    private val exports: MutableList<String> = mutableListOf()

    private fun parseSchema(tokens: List<OnjToken>, code: String, filename: String): OnjSchema {
        next = 0
        this.tokens = tokens
        this.code = code
        this.filename = filename
        this.variables.clear()

        parseNamedObjects()

        val onjSchema = parseTopLevel()

        checkAllNamedObjectGroups()

        return onjSchema
    }

    private fun parseTopLevel(): OnjSchemaObject {
        val values = mutableMapOf<String, OnjSchema>()
        val optionalValues = mutableMapOf<String, OnjSchema>()
        while (!tryConsume(OnjTokenType.EOF)) {

            if (tryConsume(OnjTokenType.EXCLAMATION)) parseVariableDeclaration(false)
            else if (tryConsume(OnjTokenType.IMPORT)) parseImport()
            else if (tryConsume(OnjTokenType.DOLLAR)) parseNamedObject()
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
                val optional = tryConsume(OnjTokenType.QUESTION)
                consume(OnjTokenType.COLON)
                if (optional) {
                    optionalValues[key] = parseValue()
                } else {
                    values[key] = parseValue()
                }
            }

        }
        return OnjSchemaObject(false, values, optionalValues, false)
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

        val parser = OnjSchemaParser(previousFiles + Paths.get(filename).normalize())

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

        val result = parser.parseSchema(OnjTokenizer().tokenize(code, path), code, path)

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

        val allNamedObjectNames = namedObjects.values.flatten().map { it.name }.toList()
        val groupNames = namedObjects.keys.toList()
        for ((group, objects) in parser.namedObjects) {
            objects.forEach {
                if (it.name in allNamedObjectNames) {
                    throw OnjParserException.fromErrorMessage(
                        pathToken.char,
                        code,
                        "Redefinition of named object ${it.name} in imported file. " +
                                "Note: Object names need to be distinct across groups.",
                        filename
                    )
                }
            }
            if (group in groupNames) {
                throw OnjParserException.fromErrorMessage(
                    pathToken.char,
                    code,
                    "Redefinition of named object group $group in imported file",
                    filename
                )
            }
            namedObjects[group] = objects
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

    private fun parseNamedObjects() {
        while (tryConsume(OnjTokenType.DOLLAR)) {
            parseNamedObject()
        }
    }

    private fun parseNamedObject() {
        consume(OnjTokenType.IDENTIFIER)
        val groupNameToken = last()
        val groupName = groupNameToken.literal as String
        if (this.namedObjects.containsKey(groupName)) {
            throw OnjParserException.fromErrorMessage(
                groupNameToken.char,
                code,
                "Group with name $groupName is already defined",
                filename
            )
        }
        val subObjects = mutableListOf<OnjSchemaNamedObject>()
        consume(OnjTokenType.L_BRACKET)
        while (!tryConsume(OnjTokenType.R_BRACKET)) {
            consume(OnjTokenType.DOLLAR)
            consume(OnjTokenType.IDENTIFIER)
            val nameToken = last()
            val name = nameToken.literal as String
            if (name in allNamedObjectNames) {
                throw OnjParserException.fromErrorMessage(
                    nameToken.char,
                    code,
                    "Named Object $name is already defined. (Note: names need to be unique even" +
                            " across different groups)",
                    filename
                )
            }
            allNamedObjectNames.add(name)
            consume(OnjTokenType.L_BRACE)
            val obj = parseObject(last(), false)
            subObjects.add(OnjSchemaNamedObject(name, obj))
        }
        namedObjects[groupName] = subObjects
    }

    private fun checkAllNamedObjectGroups() {
        for (nameToken in namedObjectGroupsToCheck) {
            val name = nameToken.literal as String
            if (!namedObjects.any { it.key == name }) {
                throw OnjParserException.fromErrorMessage(
                    nameToken.char,
                    code,
                    "No named object group with name $name",
                    filename
                )
            }
        }
    }

    private fun parseObject(startToken: OnjToken?, nullable: Boolean): OnjSchemaObject {

        val keys: MutableMap<String, OnjSchema> = mutableMapOf()
        val optionalKeys: MutableMap<String, OnjSchema> = mutableMapOf()
        var allowsAdditional = false

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

                for ((curKey, curValue) in result.keys.entries) {
                    if (keys.containsKey(curKey)) throw OnjParserException.fromErrorMessage(
                        last().char, code,
                        "Key '$curKey' included via Triple-Dot is already defined.", filename
                    )
                    keys[curKey] = curValue
                }

                for ((curKey, curValue) in result.optionalKeys.entries) {
                    if (keys.containsKey(curKey)) throw OnjParserException.fromErrorMessage(
                        last().char, code,
                        "Key '$curKey' included via Triple-Dot is already defined.", filename
                    )
                    optionalKeys[curKey] = curValue
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
        if (tryConsume(OnjTokenType.L_BRACE)) return parseObject(last(), false)
        else if (tryConsume(OnjTokenType.L_BRACKET)) return parseArray(last(), false)
        else if (tryConsume(OnjTokenType.QUESTION)) {
            if (tryConsume(OnjTokenType.L_BRACE)) return parseObject(last(), true)
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
                val size = last().literal as Long
                if (size < 0) throw OnjParserException.fromErrorMessage(
                    last().char,
                    code,
                    "Array size must be greater than zero",
                    filename
                )
                left = OnjSchemaArray(false, size.toInt(), left)
            }
            consume(OnjTokenType.R_BRACKET)
            if (tryConsume(OnjTokenType.QUESTION)) left.nullable = true
        }
        return left
    }

    private fun parseLiteral(): OnjSchema {

        if (tryConsume(OnjTokenType.EXCLAMATION)) return parseSchemaVariable()
        if (tryConsume(OnjTokenType.DOLLAR)) return parseNamedObjectRef()
        else if (tryConsume(OnjTokenType.STAR)) return OnjSchemaAny()

        consume(OnjTokenType.IDENTIFIER)
        val s = when ((last().literal as String).lowercase()) {
            "int" -> OnjSchemaInt(false)
            "float" -> OnjSchemaFloat(false)
            "string" -> OnjSchemaString(false)
            "boolean" -> OnjSchemaBoolean(false)

            else -> {
                val name = last().literal as String
                val type = OnjConfig.getCustomDataType(name)
                type ?: throw OnjParserException.fromErrorMessage(
                    last().char,
                    code,
                    "Unknown type ${last().literal}",
                    filename
                )
                OnjSchemaCustomDataType(name, type, false)
            }

        }
        if (tryConsume(OnjTokenType.QUESTION)) s.nullable = true
        return s
    }

    private fun parseNamedObjectRef(): OnjSchemaNamedObjectGroup {
        consume(OnjTokenType.IDENTIFIER)
        val nameToken = last()
        namedObjectGroupsToCheck.add(nameToken)
        return OnjSchemaNamedObjectGroup(nameToken.literal as String, tryConsume(OnjTokenType.QUESTION), namedObjects)
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

        /**
         * reads a file and parses it
         * @throws [java.io.IOException] [OnjParserException]
         * @return the parsed onj-structure
         */
        fun parseFile(path: String): OnjSchema {
            val code = File(Paths.get(path).toUri()).bufferedReader().use { it.readText() }
            return OnjSchemaParser().parseSchema(OnjTokenizer().tokenize(code, path), code, path)
        }

        /**
         * reads a file and parses it
         * @throws [java.io.IOException] [OnjParserException]
         * @return the parsed onj-structure
         */
        fun parseFile(file: File): OnjSchema {
            val code = file.bufferedReader().use { it.readText() }
            return OnjSchemaParser().parseSchema(OnjTokenizer().tokenize(code, file.canonicalPath), code, file.canonicalPath)
        }

        /**
         * parses a string
         * @throws [OnjParserException]
         * @return the parsed onj-structure
         */
        fun parse(code: String): OnjSchema {
            return OnjSchemaParser().parseSchema(OnjTokenizer().tokenize(code, "anonymous"), code, "anonymous")
        }

    }

}
