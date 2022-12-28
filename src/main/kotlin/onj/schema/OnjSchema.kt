package onj.schema

import onj.value.*
import java.lang.RuntimeException
import kotlin.reflect.KClass

/**
 * represents a parsed onjschema structure
 */
abstract class OnjSchema internal constructor(_nullable: Boolean) {

    /**
     * true if this part of the schema is nullable
     */
    var nullable: Boolean = _nullable
        private set

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
class OnjSchemaBoolean(nullable: Boolean) : OnjSchema(nullable) {

    override fun match(onjValue: OnjValue, parentName: String) {
        if (onjValue.isNull()) {
            if (nullable) return
            throw OnjSchemaException.fromNonNullable(parentName, "boolean")
        }
        if (!onjValue.isBoolean()) throw OnjSchemaException.fromTypeError(
            parentName,
            "boolean",
            getActualType(onjValue)
        )
    }

    override fun getAsNullable(): OnjSchema {
        return OnjSchemaBoolean(true)
    }
}

/**
 * the schema of an Int
 */
class OnjSchemaInt(nullable: Boolean) : OnjSchema(nullable) {

    override fun match(onjValue: OnjValue, parentName: String) {
        if (onjValue.isNull()) {
            if (nullable) return
            throw OnjSchemaException.fromNonNullable(parentName, "int")
        }
        if (!onjValue.isInt()) throw OnjSchemaException.fromTypeError(
            parentName,
            "int",
            getActualType(onjValue)
        )
    }

    override fun getAsNullable(): OnjSchema {
        return OnjSchemaInt(true)
    }
}

/**
 * the schema of a float
 */
class OnjSchemaFloat(nullable: Boolean) : OnjSchema(nullable) {

    override fun match(onjValue: OnjValue, parentName: String) {
        if (onjValue.isNull()) {
            if (nullable) return
            throw OnjSchemaException.fromNonNullable(parentName, "float")
        }
        if (!onjValue.isFloat())  throw OnjSchemaException.fromTypeError(
            parentName,
            "float",
            getActualType(onjValue)
        )
    }

    override fun getAsNullable(): OnjSchema {
        return OnjSchemaFloat(true)
    }
}

/**
 * the schema of a string
 */
class OnjSchemaString(nullable: Boolean) : OnjSchema(nullable) {

    override fun match(onjValue: OnjValue, parentName: String) {
        if (onjValue.isNull()) {
            if (nullable) return
            throw OnjSchemaException.fromNonNullable(parentName, "string")
        }
        if (!onjValue.isString()) throw OnjSchemaException.fromTypeError(
            parentName,
            "string",
            getActualType(onjValue)
        )
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

        if (onjValue !is OnjObject) throw OnjSchemaException.fromTypeError(
            parentName,
            "object",
            getActualType(onjValue)
        )

        for ((key, value) in keys) {
            val part = onjValue[key] ?: throw OnjSchemaException.fromMissingKey(
                "$parentName->$key"
            )
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
abstract class OnjSchemaArray(nullable: Boolean) : OnjSchema(nullable)

class TypeBasedOnjSchemaArray(
    private val type: OnjSchema,
    private val size: Int?,
    nullable: Boolean
) : OnjSchemaArray(nullable) {

    override fun match(onjValue: OnjValue, parentName: String) {
        if (!onjValue.isOnjArray()) throw OnjSchemaException.fromTypeError(
            parentName,
            "array",
            getActualType(onjValue)
        )
        val values = (onjValue as OnjArray).value
        if (size != null && values.size != size) throw OnjSchemaException.fromWrongSize(
            parentName,
            size,
            onjValue.value.size
        )
        for (i in values.indices) {
            type.match(values[i], "$parentName[$i]")
        }
    }

    override fun getAsNullable(): OnjSchema = TypeBasedOnjSchemaArray(type, size, true)
}

class LiteralOnjSchemaArray(
    val schemas: List<OnjSchema>,
    nullable: Boolean
) : OnjSchemaArray(nullable) {

    override fun match(onjValue: OnjValue, parentName: String) {
        if (!onjValue.isOnjArray()) throw OnjSchemaException.fromTypeError(
            parentName,
            "array",
            getActualType(onjValue)
        )
        onjValue as OnjArray
        if (schemas.size != onjValue.value.size) throw OnjSchemaException.fromWrongSize(
            parentName,
            schemas.size,
            onjValue.value.size
        )
        for (i in schemas.indices) {
            schemas[i].match(onjValue.value[i], "$parentName[$i]")
        }
    }

    override fun getAsNullable(): OnjSchema = LiteralOnjSchemaArray(schemas, nullable)
}

/**
 * the schema of an any-type
 */
class OnjSchemaAny internal constructor() : OnjSchema(true) {

    override fun match(onjValue: OnjValue, parentName: String) {
    }

    override fun getAsNullable(): OnjSchema {
        return OnjSchemaAny()
    }
}

class OnjSchemaNamedObjectGroup internal constructor(
    val name: String,
    nullable: Boolean,
    private val namedObjects: Map<String, List<OnjSchemaNamedObject>>
) : OnjSchema(nullable) {

    override fun match(onjValue: OnjValue, parentName: String) {
        if (onjValue.isNull()) {
            if (nullable) return
            throw OnjSchemaException.fromNonNullable(parentName, "named object")
        }

        if (onjValue !is OnjNamedObject) {
            throw OnjSchemaException.fromTypeError(
                parentName,
                "named object",
                getActualType(onjValue)
            )
        }

        val obj = namedObjects[name]?.filter { it.name == onjValue.name }?.getOrNull(0)?.obj ?: run {
            throw OnjSchemaException.fromUnknownObjectName(
                parentName,
                onjValue.name,
                name
            )
        }

        obj.match(onjValue, parentName)
    }

    override fun getAsNullable(): OnjSchema {
        return OnjSchemaNamedObjectGroup(name, true, namedObjects)
    }
}

class OnjSchemaCustomDataType internal constructor(
    private val name: String,
    private val type: KClass<*>,
    nullable: Boolean
) : OnjSchema(nullable) {

    override fun match(onjValue: OnjValue, parentName: String) {
        if (onjValue.isNull()) {
            if (nullable) return
            throw OnjSchemaException.fromNonNullable(
                parentName,
                "custom($name)"
            )
        }
        if (!type.isInstance(onjValue)) {
            throw OnjSchemaException.fromTypeError(
                parentName,
                "custom($name)",
                getActualType(onjValue)
            )
        }
    }

    override fun getAsNullable(): OnjSchema {
        return OnjSchemaCustomDataType(name, type, true)
    }


}

private fun getActualType(value: OnjValue): String {
    return when (value) {
        is OnjBoolean -> "boolean"
        is OnjInt -> "int"
        is OnjFloat -> "float"
        is OnjString -> "string"
        is OnjNamedObject -> "named object"
        is OnjObject -> "object"
        is OnjArray -> "array"
        else -> value::class.simpleName ?: ""
    }
}

class OnjSchemaNamedObject(val name: String, val obj: OnjSchemaObject)

fun List<OnjSchema>.toSchemaArray(): OnjSchemaArray = LiteralOnjSchemaArray(this, false)

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
            return OnjSchemaException(
                "\u001B[37m\n\n'$key' has length '$actual'," +
                        " but length '$expected' was expected.\u001B[0m\n"
            )
        }

        fun fromUnknownKey(key: String): OnjSchemaException {
            return OnjSchemaException("\u001B[37m\n\nUnknown key '$key'\u001B[0m\n")
        }

        fun fromUnknownObjectName(key: String, name: String, group: String): OnjSchemaException {
            return OnjSchemaException("\u001B[37m\n\n'$key': Unknown object name '$name' in group '$group'\u001B[0m\n")
        }
    }
}
