package onj.serialization

import onj.value.*
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

object OnjDeserializer {

    inline fun <reified T : Any> deserialize(obj: OnjObject): T = deserialize(obj, T::class)

    fun <T : Any> deserialize(obj: OnjObject, clazz: KClass<T>): T = deserializeObject(obj, clazz)

    private fun <T : Any> deserializeObject(obj: OnjObject, clazz: KClass<T>): T {
        val annotation = clazz
            .annotations
            .filterIsInstance<OnjSerializable>()
            .firstOrNull()
            ?: throw OnjSerializationException("Class '${clazz.simpleName}' is not annotated with @OnjSerializable")
        val constructor = clazz.primaryConstructor
            ?: throw OnjSerializationException("Class '${clazz.simpleName}' needs a primary constructor to be deserialized")
        val params = obj.value.map { (name, value) ->
            val param = constructor.parameters.find { it.name == name }
                ?: throw OnjSerializationException("No parameter for key '$name' in constructor of class '${clazz.simpleName}'")
            val paramClass = param.type.classifier as? KClass<*>
                ?: throw OnjSerializationException("Parameter '$name' of class '${clazz.simpleName}' is not a concrete parameter")
            val deserialized = deserializeArbitraryValue(value)
            param to coerce(deserialized, paramClass, name)
        }.associate { it }
        return constructor.callBy(params)
    }

    private fun coerce(value: Any?, clazz: KClass<*>, paramName: String): Any? {
        if (clazz.isInstance(value)) return value
        return when {
            clazz == Int::class && value is Number -> value.toInt()
            clazz == Long::class && value is Number -> value.toLong()
            clazz == Float::class && value is Number -> value.toFloat()
            clazz == Double::class && value is Number -> value.toDouble()
            clazz == Short::class && value is Number -> value.toShort()
            clazz == Byte::class && value is Number -> value.toByte()
            else -> if (value != null) {
                throw OnjSerializationException("value '$value' passed for parameter '$paramName' of type '${value::class}' can't be coerced to '${clazz.simpleName}'")
            } else {
                throw OnjSerializationException("null passed for non-nullable parameter '$paramName'")
            }
        }
    }

    private fun deserializeArbitraryValue(value: OnjValue): Any? = when (value) {
        is OnjObject -> TODO()
        is OnjArray -> TODO()
        else -> value.value
    }

}
