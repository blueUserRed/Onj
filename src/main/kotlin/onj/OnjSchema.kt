package onj

import java.lang.RuntimeException

/**
 * represents a parsed onjschema structure
 */
abstract class OnjSchema internal constructor(_nullable: Boolean) {

    /**
     * true if this part of the schema is nullable
     */
    var nullable: Boolean = _nullable
        internal set

    /**
     * asserts that an onj-structure matches an onj-schema
     * @throws [OnjSchemaException] when it doesn't match
     */
    fun assertMatches(onjValue: OnjValue) = match(onjValue, "root")

    /**
     * checks if an onj-structure matches an onj-schema
     * @return null if it matches, an error-message if it doesn't
     */
    fun check(onjValue: OnjValue): String? = try {
        match(onjValue, "root")
        null
    } catch (e: OnjSchemaException) {
        e.message
    }

    internal abstract fun match(onjValue: OnjValue, parentName: String)

    /**
     * returns a copy of this schema, but makes it nullable
     */
    abstract fun getAsNullable(): OnjSchema
}

/**
 * the schema of a boolean
 */
class OnjSchemaBoolean internal constructor(nullable: Boolean) : OnjSchema(nullable) {

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

/**
 * the schema of an Int
 */
class OnjSchemaInt internal constructor(nullable: Boolean) : OnjSchema(nullable) {

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

/**
 * the schema of a float
 */
class OnjSchemaFloat internal constructor(nullable: Boolean) : OnjSchema(nullable) {

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

/**
 * the schema of a string
 */
class OnjSchemaString internal constructor(nullable: Boolean) : OnjSchema(nullable) {

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

/**
 * the schema of an object
 * @param keys the (mandatory) keys of the object
 * @param optionalKeys the optional keys of the object
 * @param allowsAdditional true if keys that where not defined are allowed
 */
class OnjSchemaObject internal constructor(
    nullable: Boolean,
    val keys: Map<String, OnjSchema>,
    val optionalKeys: Map<String, OnjSchema>,
    val allowsAdditional: Boolean
) : OnjSchema(nullable) {

    override fun match(onjValue: OnjValue, parentName: String) {

        if (onjValue.isNull()) {
            if (nullable) return
            throw OnjSchemaException.fromNonNullable(parentName, "object")
        }

        if (onjValue !is OnjObject) throw OnjSchemaException.fromTypeError(parentName, "object", getActualType(onjValue))

        for ((key, value) in keys) {
            val part = onjValue[key] ?: throw OnjSchemaException.fromMissingKey("$parentName->$key")
            value.match(part, "$parentName->$key")
        }
        for ((key, value) in optionalKeys) {
            val part = onjValue[key] ?: continue
            value.match(part, "$parentName->$key")
        }
        if (!allowsAdditional) {
            for (key in onjValue.value.keys) if (key !in keys.keys && key !in optionalKeys.keys) {
                throw OnjSchemaException.fromUnknownKey("$parentName->$key")
            }
        }
    }

    override fun getAsNullable(): OnjSchema {
        return OnjSchemaObject(true, keys, optionalKeys, allowsAdditional)
    }
}

/**
 * the schema of an array
 */
class OnjSchemaArray private constructor(nullable: Boolean) : OnjSchema(nullable) {

    private var _schemas: List<OnjSchema>? = null
    private var size: Int? = null
    private var type: OnjSchema? = null

    /**
     * the list of schemas in the array
     */
    val schemas: List<OnjSchema>
        get() {
            if (_schemas != null) return _schemas!!
            return List(size!!) { type!! }
        }

    internal constructor(nullable: Boolean, schema: List<OnjSchema>) : this(nullable) {
        this._schemas = schema
    }

    internal constructor(nullable: Boolean, size: Int, type: OnjSchema) : this(nullable) {
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

        if (_schemas != null) {
            if (_schemas!!.size != onjValue.value.size)
                throw OnjSchemaException.fromWrongSize(parentName, _schemas!!.size, onjValue.value.size)
            for (i in onjValue.value.indices) {
                _schemas!![i].match(onjValue.value[i], "$parentName[$i]")
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
        arr._schemas = this._schemas
        arr.size = this.size
        arr.type = this.type
        return arr
    }
}

/**
 * the schema of an any-type
 */
class OnjSchemaAny : OnjSchema(true) {

    override fun match(onjValue: OnjValue, parentName: String) {
    }

    override fun getAsNullable(): OnjSchema {
        return this
    }
}

private fun getActualType(value: OnjValue): String {
    return when (value) {
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

    internal companion object {

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

        fun fromUnknownKey(key: String): OnjSchemaException {
            return OnjSchemaException("\u001B[37m\n\nUnknown key '$key'\u001B[0m\n")
        }
    }
}