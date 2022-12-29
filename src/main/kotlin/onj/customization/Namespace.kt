package onj.customization

import onj.value.OnjArray
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
