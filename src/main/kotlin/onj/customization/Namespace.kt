package onj.customization

import onj.builder.buildOnjObject
import onj.value.OnjArray
import onj.value.OnjObject
import onj.value.OnjValue
import kotlin.reflect.KClass

data class Namespace(
    val name: String,
    val variables: Map<String, OnjValue>,
    val functions: Set<OnjFunction>,
    val customDataTypes: Map<String, KClass<*>>
) {

    fun getVariable(name: String): OnjValue? = variables[name]

    fun getCustomDataType(name: String): KClass<*>? = customDataTypes[name]

    fun getFunction(name: String, args: Array<OnjValue>): OnjFunction? = functions.firstOrNull {
        return@firstOrNull it.name == name && it.paramsSchema.check(OnjArray(args.toList())) == null
    }

    fun getInfixFunction(name: String, args: Array<OnjValue>): OnjFunction? = functions
        .filter { it.canBeUsedAsInfix }
        .firstOrNull {
            return@firstOrNull it.name == name && it.paramsSchema.check(OnjArray(args.toList())) == null
        }

    fun asOnj(): OnjObject = buildOnjObject {
        "name" with name
        "functions" with functions.map { it.asOnjObject() }
        "variables" with variables.map { (name, value) -> buildOnjObject {
            "name" with name
            "type" with when {
                value.isInt() -> "int"
                value.isFloat() -> "float"
                value.isString() -> "string"
                value.isBoolean() -> "boolean"
                value.isNull() -> "*"
                value.isOnjArray() -> "array"
                value.isOnjObject() -> "object"
                else -> {
                    customDataTypes.entries.find { it.value.isInstance(value) }?.key ?: "*"
                }
            }
        } }
    }

    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.CLASS)
    annotation class OnjNamespace

    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.FIELD)
    annotation class OnjNamespaceVariables

    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.FIELD)
    annotation class OnjNamespaceDatatypes

}
