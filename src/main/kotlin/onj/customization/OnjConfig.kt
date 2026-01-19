package onj.customization


import onj.builder.buildOnjObject
import onj.customization.OnjFunction.RegisterOnjFunction
import onj.customization.Namespace.OnjNamespace
import onj.customization.Namespace.OnjNamespaceVariables
import onj.customization.Namespace.OnjNamespaceDatatypes
import onj.customization.OnjFunction.RegisterOnjFunction.*
import onj.value.*
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.*
import kotlin.reflect.jvm.javaField

object OnjConfig {

    private val namespaces: MutableMap<String, Namespace> = mutableMapOf()

    init {
        registerNamespace("global", GlobalNamespace)
    }

    fun getGlobalNamespace(): Namespace? = namespaces["global"]

    fun getNamespace(name: String): Namespace? = namespaces[name]

    fun registerNamespace(name: String, obj: Any) {
        val clazz = obj::class
        val annotation = clazz.findAnnotation<OnjNamespace>()
        annotation ?: throw RuntimeException(
            "cannot register namespace $name because it dosen't have the OnjNamespace annotation"
        )
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
        val functions = getFunctions(obj, customDatatypes)

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

    private fun getFunctions(obj: Any, customTypes: Map<String, KClass<*>>): Set<OnjFunction> {
        val clazz = obj::class
        val functions = mutableSetOf<OnjFunction>()
        for (function in clazz.functions) {
            val annotation = function.findAnnotation<RegisterOnjFunction>() ?: continue
            val (paramNames, returnType) = assertThatFunctionCanBeRegistered(obj, annotation.type, function, customTypes)
            val onjFunction = OnjFunction(
                getRegistrationNameForFunction(annotation.type, function.name),
                annotation.schema,
                paramNames,
                returnType,
                annotation.type == OnjFunctionType.INFIX
            ) { function.call(obj, *it) as OnjValue }
            functions.add(onjFunction)
        }
        return functions
    }

    private fun assertThatFunctionCanBeRegistered(
        obj: Any,
        type: OnjFunctionType,
        function: KFunction<*>,
        customTypes: Map<String, KClass<*>>
    ): Pair<List<String>, String> {
        val onjValueType = OnjValue::class.createType()
        var isFirst = true
        var hasReceiver = false
        val parameterNames = mutableListOf<String>()
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
            parameterNames.add(it.name ?: "")
        }

        val paramsCount = if (hasReceiver) function.parameters.size - 1 else function.parameters.size

        if (
            (type == OnjFunctionType.INFIX || (type == OnjFunctionType.OPERATOR && function.name != "unaryMinus")) &&
            paramsCount != 2
        ) {
            throw RuntimeException(
                "could not register function ${function.name} " +
                "because it is marked as operator or infix but an amount of parameters different from 2"
            )
        } else if (type == OnjFunctionType.OPERATOR && function.name == "unaryMinus" && paramsCount != 1) {
            throw RuntimeException(
                "could not register function ${function.name} " +
                        "because it overloads unaryMinus and doesn't have one parameter"
            )
        } else if (type == OnjFunctionType.CONVERSION && paramsCount != 1) {
            throw RuntimeException(
                "could not register function ${function.name} " +
                "because it is marked as conversion but has more than one parameter"
            )
        }

        val returnType = function.returnType
        if (!returnType.isSubtypeOf(onjValueType)) throw RuntimeException(
            "could not register function ${function.name} because its return type doesn't extend OnjValue"
        )
        val returnTypeAsString = when {
            returnType.isSupertypeOf(onjValueType) && returnType.isSubtypeOf(onjValueType) -> "*"
            returnType.isSubtypeOf(OnjInt::class.createType()) -> "int"
            returnType.isSubtypeOf(OnjFloat::class.createType()) -> "float"
            returnType.isSubtypeOf(OnjString::class.createType()) -> "string"
            returnType.isSubtypeOf(OnjBoolean::class.createType()) -> "boolean"
            returnType.isSubtypeOf(OnjNull::class.createType()) -> "*"
            returnType.isSubtypeOf(OnjObject::class.createType()) -> "object"
            returnType.isSubtypeOf(OnjArray::class.createType()) -> "array"
            else -> {
                customTypes.entries.find { (_, clazz) ->
                    returnType.isSubtypeOf(clazz.createType())
                }?.key ?: throw RuntimeException(
                    "could not register function ${function.name} because it has a custom return type, that is registered with OnjNamespaceDataType"
                )
            }
        }
        return parameterNames to returnTypeAsString
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

    fun dumpOnjEnv(file: File) {
        val obj = buildOnjObject {
            "namespaces" with namespaces.values.map { it.asOnj() }
        }
        if (!file.exists()) file.createNewFile()
        file.writeText(obj.toString())
    }

}