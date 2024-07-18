package onj.serialization

import onj.value.*
import kotlin.reflect.full.primaryConstructor

object OnjSerializer {

    fun serialize(obj: Any): OnjValue = serializeArbitraryValue(obj)

    private fun serializeArbitraryValue(value: Any?): OnjValue = when (value) {

        null -> OnjNull()
        is OnjValue -> value
        is Int -> OnjInt(value.toLong())
        is Long -> OnjInt(value)
        is Float -> OnjFloat(value.toDouble())
        is Double -> OnjFloat(value)
        is Boolean -> OnjBoolean(value)
        is String -> OnjString(value)

        else -> serializeObject(value)
    }

    private fun serializeObject(obj: Any): OnjObject {
        val clazz = obj::class
        val annotation = clazz
            .annotations
            .filterIsInstance<OnjSerializable>()
            .firstOrNull()
            ?: throw OnjSerializationException("Class '${clazz.simpleName}' is not annotated with @OnjSerializable")
        val params = clazz
            .primaryConstructor
            .let { it ?: throw OnjSerializationException("Class '${clazz.simpleName}' needs a primary constructor to be serialized") }
            .parameters
            .map { it.name ?: throw RuntimeException("Constructor of class '${clazz.simpleName}' includes nameless parameter (e.g. Receiver)") }
        val values = params.map { param ->
            val field = clazz
                .java
                .declaredFields
                .find { it.name == param }
                ?: throw OnjSerializationException("Class '${clazz.simpleName}' has no field for constructor parameter '$param'")
            field.isAccessible = true
            param to field.get(obj)
        }
        return OnjObject(values.associate { (name, value) -> name to serializeArbitraryValue(value) })
    }

}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class OnjSerializable

class OnjSerializationException(message: String) : RuntimeException(message)
