package onj

import onj.*
import java.lang.RuntimeException

abstract class OnjSchema(val nullable: Boolean) {

    fun assertMatches(onjValue: OnjValue) = match(onjValue, "")

    fun check(onjValue: OnjValue) = try {
        match(onjValue, "")
        true
    } catch (e: OnjSchemaException) {
        false
    }

    internal abstract fun match(onjValue: OnjValue, parentName: String)

    abstract fun getAsNullable(): OnjSchema
}

class OnjSchemaBoolean(nullable: Boolean) : OnjSchema(nullable) {

    override fun match(onjValue: OnjValue, parentName: String) {
        if (onjValue.isNull()) {
            if (nullable) return
            throw OnjSchemaException.fromNonNullable(parentName, "boolean")
        }
        if (!onjValue.isBoolean()) throw OnjSchemaException.fromTypeError(parentName, "boolean", getActualType(onjValue))
    }

    override fun getAsNullable(): OnjSchema {
        return OnjSchemaBoolean(true)
    }
}

class OnjSchemaInt(nullable: Boolean) : OnjSchema(nullable) {

    override fun match(onjValue: OnjValue, parentName: String) {
        if (onjValue.isNull()) {
            if (nullable) return
            throw OnjSchemaException.fromNonNullable(parentName, "int")
        }
        if (!onjValue.isInt())  throw OnjSchemaException.fromTypeError(parentName, "int", getActualType(onjValue))
    }

    override fun getAsNullable(): OnjSchema {
        return OnjSchemaInt(true)
    }
}

class OnjSchemaFloat(nullable: Boolean) : OnjSchema(nullable) {

    override fun match(onjValue: OnjValue, parentName: String) {
        if (onjValue.isNull()) {
            if (nullable) return
            throw OnjSchemaException.fromNonNullable(parentName, "float")
        }
        if (!onjValue.isFloat())  throw OnjSchemaException.fromTypeError(parentName, "float", getActualType(onjValue))
    }

    override fun getAsNullable(): OnjSchema {
        return OnjSchemaFloat(true)
    }
}

class OnjSchemaString(nullable: Boolean) : OnjSchema(nullable) {

    override fun match(onjValue: OnjValue, parentName: String) {
        if (onjValue.isNull()) {
            if (nullable) return
            throw OnjSchemaException.fromNonNullable(parentName, "string")
        }
        if (!onjValue.isString()) throw OnjSchemaException.fromTypeError(parentName, "string", getActualType(onjValue))
    }

    override fun getAsNullable(): OnjSchema {
        return OnjSchemaString(true)
    }
}

class OnjSchemaObject(nullable: Boolean, val schema: Map<String, OnjSchema>) : OnjSchema(nullable) {

    override fun match(onjValue: OnjValue, parentName: String) {

        if (onjValue.isNull()) {
            if (nullable) return
            throw OnjSchemaException.fromNonNullable(parentName, "object")
        }

        if (onjValue !is OnjObject)  throw OnjSchemaException.fromTypeError(parentName, "object", getActualType(onjValue))

        for (entry in schema.entries) {
            val part = onjValue[entry.key] ?: throw OnjSchemaException.fromMissingKey("$parentName->${entry.key}")
            entry.value.match(part, "$parentName->${entry.key}")
        }
    }

    override fun getAsNullable(): OnjSchema {
        return OnjSchemaObject(true, schema)
    }
}

class OnjSchemaArray private constructor(nullable: Boolean) : OnjSchema(nullable) {

    private var schema: List<OnjSchema>? = null
    private var size: Int? = null
    private var type: OnjSchema? = null

    constructor(nullable: Boolean, schema: List<OnjSchema>) : this(nullable) {
        this.schema = schema
    }

    constructor(nullable: Boolean, size: Int, type: OnjSchema) : this(nullable) {
        this.size = size
        this.type = type
    }

    override fun match(onjValue: OnjValue, parentName: String) {

        if (onjValue.isNull()) {
            if (nullable) return
            throw OnjSchemaException.fromNonNullable(parentName, "array")
        }

        if (onjValue !is OnjArray)
            throw OnjSchemaException.fromTypeError(parentName, "array", getActualType(onjValue))

        if (schema != null) {
            if (schema!!.size != onjValue.value.size)
                throw OnjSchemaException.fromWrongSize(parentName, schema!!.size, onjValue.value.size)
            for (i in onjValue.value.indices) {
                schema!![i].match(onjValue.value[i], "$parentName[$i]")
            }
            return
        }

        if (size != -1 && onjValue.value.size != size)
            throw OnjSchemaException.fromWrongSize(parentName, size!!, onjValue.value.size)
        for (i in onjValue.value.indices)
            type!!.match(onjValue.value[i], "$parentName[$i]")
    }

    override fun getAsNullable(): OnjSchema {
        val arr = OnjSchemaArray(true)
        arr.schema = this.schema
        arr.size = this.size
        arr.type = this.type
        return arr
    }
}

private fun getActualType(value: OnjValue): String {
    return when(value) {
        is OnjBoolean -> "boolean"
        is OnjInt -> "int"
        is OnjFloat -> "float"
        is OnjString -> "string"
        is OnjObject -> "object"
        is OnjArray -> "array"
        else -> ""
    }
}


class OnjSchemaException(message: String) : RuntimeException(message) {

    companion object {

        fun fromTypeError(key: String, expected: String, actual: String): OnjSchemaException {
            return OnjSchemaException("\u001B[37m\n\n'$key' expected to have type '$expected', but is '$actual'.\u001B[0m\n")
        }

        fun fromNonNullable(key: String, type: String): OnjSchemaException {
            return OnjSchemaException("\u001B[37m\n\n'$key' is null, but expected type '$type' is not nullable.\u001B[0m\n")
        }

        fun fromMissingKey(key: String): OnjSchemaException {
            return OnjSchemaException("\u001B[37m\n\n'$key' is not defined.\u001B[0m\n")
        }

        fun fromWrongSize(key: String, expected: Int, actual: Int): OnjSchemaException {
            return OnjSchemaException("\u001B[37m\n\n'$key' has length '$actual'," +
                    " but length '$expected' was expected.\u001B[0m\n")
        }
    }
}