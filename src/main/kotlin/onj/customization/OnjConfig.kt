package onj.customization


import onj.customization.OnjFunction.RegisterOnjFunction
import onj.customization.Namespace.OnjNamespace
import onj.customization.Namespace.OnjNamespaceVariables
import onj.customization.Namespace.OnjNamespaceDatatypes
import onj.customization.OnjFunction.RegisterOnjFunction.*
import onj.parser.OnjParserException
import onj.parser.OnjSchemaParser
import onj.schema.*
import onj.value.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.*
import kotlin.reflect.jvm.javaField

object OnjConfig {

    private val namespaces: MutableMap<String, Namespace> = mutableMapOf()

    init {
        registerNameSpace("global", GlobalNamespace)
    }

    fun getGlobalNamespace(): Namespace? = namespaces["global"]

    fun getNamespace(name: String): Namespace? = namespaces[name]

    fun registerNameSpace(name: String, obj: Any) {
        val clazz = obj::class
        val annotation = clazz.findAnnotation<OnjNamespace>()
        annotation ?: throw RuntimeException(
            "cannot register namespace $name because it dosen't have the OnjNamespace annotation"
        )
        val functions = getFunctions(obj)
        val variables = mutableMapOf<String, OnjValue>()
        val customDatatypes = mutableMapOf<String, KClass<*>>()

        clazz
            .memberProperties
            .find { it.javaField?.isAnnotationPresent(OnjNamespaceVariables::class.java) ?: false }
            ?.let {
                val vars = it.getter.call(obj)
                if (vars !is Map<*, *>) throw RuntimeException(
                    "property marked with OnjNamespaceVariable must be a Map"
                )
                for ((key, value) in vars) {
                    if (key !is String) throw RuntimeException(
                        "map marked with OnjNamespaceVariable must only have strings as key"
                    )
                    if (value !is OnjValue) throw RuntimeException(
                        "map marked with OnjNamespaceVariable must only have OnjValues as value"
                    )
                    variables[key] = value
                }
            }

        clazz
            .memberProperties
            .find { it.javaField?.isAnnotationPresent(OnjNamespaceDatatypes::class.java) ?: false }
            ?.let {
                val types = it.getter.call(obj)
                if (types !is Map<*, *>) throw RuntimeException(
                    "property marked with OnjNamespaceDatatypes must be a Map"
                )
                for ((key, value) in types) {
                    if (key !is String) throw RuntimeException(
                        "map marked with OnjNamespaceDatatypes must only have strings as key"
                    )
                    if (value !is KClass<*>) throw RuntimeException(
                        "map marked with OnjNamespaceDatatypes must only have KClasses as value"
                    )
                    if (!value.isSubclassOf(OnjValue::class)) throw RuntimeException(
                        "map marked with OnjNamespaceDatatypes must only have values that extend OnjValue"
                    )
                    customDatatypes[key] = value
                }
            }

        if (namespaces.containsKey(name)) throw RuntimeException(
            "cannot register namespace $name because a namespace with that name already exists"
        )
        namespaces[name] = Namespace(
            name,
            variables,
            functions,
            customDatatypes
        )
    }

    private fun getFunctions(obj: Any): Set<OnjFunction> {
        val clazz = obj::class
        val functions = mutableSetOf<OnjFunction>()
        for (function in clazz.functions) {
            val annotation = function.findAnnotation<RegisterOnjFunction>() ?: continue
            assertThatFunctionCanBeRegistered(obj, annotation.type, function)
            val onjFunction = OnjFunction(
                getRegistrationNameForFunction(annotation.type, function.name),
                annotation.schema,
                annotation.type == OnjFunctionType.INFIX
            ) { function.call(obj, *it) as OnjValue }
            functions.add(onjFunction)
        }
        return functions
    }

    private fun assertThatFunctionCanBeRegistered(obj: Any, type: OnjFunctionType, function: KFunction<*>) {
        val onjValueType = OnjValue::class.createType()
        var isFirst = true
        var hasReceiver = false
        function.parameters.forEach {

            if (isFirst) {
                isFirst = false
                if (it.kind != KParameter.Kind.VALUE) {
                    val objType = obj::class.starProjectedType
                    if (objType != it.type) throw RuntimeException(
                        "could not register function ${function.name} because it has a receiver or instance " +
                        "parameter that is not of the same type as the object used to register it"
                    )
                    hasReceiver = true
                    return@forEach
                }
            }

            if (!it.type.isSubtypeOf(onjValueType)) throw RuntimeException(
                "could not register function ${function.name} because" +
                " its parameters include types that don't extend OnjValue"
            )
        }

        val paramsCount = if (hasReceiver) function.parameters.size - 1 else function.parameters.size

        if (
            (type == OnjFunctionType.INFIX || (type == OnjFunctionType.OPERATOR && function.name != "unaryMinus")) &&
            paramsCount != 2
        ) {
            throw RuntimeException(
                "could not register function ${function.name} " +
                "because it is marked as operator or infix but has more than two parameters"
            )
        } else if (type == OnjFunctionType.CONVERSION && paramsCount != 1) {
            throw RuntimeException(
                "could not register function ${function.name} " +
                "because it is marked as conversion but has more than one parameter"
            )
        }

        if (!function.returnType.isSubtypeOf(onjValueType)) throw RuntimeException(
            "could not register function ${function.name} because its return type dosen't extend OnjValue"
        )
    }

    private fun getRegistrationNameForFunction(type: OnjFunctionType, name: String): String = when (type) {

        OnjFunctionType.NORMAL -> name
        OnjFunctionType.INFIX -> name
        OnjFunctionType.CONVERSION -> "convert%$name"
        OnjFunctionType.OPERATOR -> {
            if (name !in arrayOf("plus", "minus", "star", "div", "unaryMinus")) throw RuntimeException(
                "could not register function $name because it is marked as an operator but its name is not one of" +
                "'plus', 'minus', 'star', 'div', 'unaryMinus'"
            )
            "operator%$name"
        }

    }



}