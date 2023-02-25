package onj.builder

import onj.value.*

class OnjObjectBuilderDSL internal constructor() {

    private val values: MutableMap<String, OnjValue> = mutableMapOf()

    private var name: String? = null

    infix fun String.with(value: Any?) {
        if (values.containsKey(this)) throw OnjObjectBuilderDSLException("duplicate key '$this'")
        values[this] = convertToOnjValue(value)
    }

    fun includeAll(obj: OnjObject) {
        for ((key, value) in obj.value) {
            if (values.containsKey(key)) {
                throw OnjObjectBuilderDSLException("key '$key' included here was already defined")
            }
            values[key] = value
        }
    }

    fun includeAll(map: Map<String, OnjValue>) {
        for ((key, value) in map) {
            if (values.containsKey(key)) {
                throw OnjObjectBuilderDSLException("key '$key' included here was already defined")
            }
            values[key] = value
        }
    }

    fun name(name: String?) {
        this.name = name
    }

    internal fun build(): OnjObject {
        val name = name ?: return OnjObject(values)
        return OnjNamedObject(name, values)
    }

    class OnjObjectBuilderDSLException(message: String) : java.lang.RuntimeException(message)

}

private fun convertToOnjValue(value: Any?): OnjValue = when (value) {

    null -> OnjNull()
    is OnjValue -> value
    is Int -> OnjInt(value.toLong())
    is Long -> OnjInt(value)
    is Float -> OnjFloat(value.toDouble())
    is Double -> OnjFloat(value)
    is Boolean -> OnjBoolean(value)
    is String -> OnjString(value)
    is Collection<*> -> OnjArray(value.map { convertToOnjValue(it) })
    is Array<*> -> OnjArray(value.map { convertToOnjValue(it) })

    else -> throw OnjObjectBuilderDSL.OnjObjectBuilderDSLException("cant convert type '${value::class.simpleName}' to a OnjValue")
}

fun buildOnjObject(builder: OnjObjectBuilderDSL.() -> Unit): OnjObject {
    val builderDSL = OnjObjectBuilderDSL()
    builder(builderDSL)
    return builderDSL.build()
}

fun Collection<*>.toOnjArray(): OnjArray {
    return convertToOnjValue(this) as OnjArray
}

fun Array<*>.toOnjArray(): OnjArray {
    return convertToOnjValue(this) as OnjArray
}

fun Map<String, *>.toOnjObject(): OnjObject {
    return OnjObject(this.mapValues { convertToOnjValue(it.value) })
}
