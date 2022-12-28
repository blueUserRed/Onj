package onj.customization


import onj.customization.RegisterOnjFunction.*
import onj.parser.OnjParserException
import onj.parser.OnjSchemaParser
import onj.schema.*
import onj.value.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.createType
import kotlin.reflect.full.functions
import kotlin.reflect.full.starProjectedType

object OnjConfig {

    private val globalFunctions: MutableSet<OnjFunction> = mutableSetOf()
    private val globalCustomDataTypes: MutableMap<String, KClass<*>> = mutableMapOf()
    private val globalVariables: MutableMap<String, OnjValue> = mutableMapOf()

    init {
        StandardConfig.bindDefaultVariables()
        registerGlobalFunctions(StandardConfig)
    }

    fun <T> addGlobalCustomDataType(name: String, type: KClass<T>) where T : OnjValue {
        globalCustomDataTypes[name] = type
    }

    fun getGlobalCustomDataType(name: String): KClass<*>? = globalCustomDataTypes[name]

    fun bindGlobalVariable(name: String, value: OnjValue) {
        globalVariables[name] = value
    }

    fun addGlobalFunction(function: OnjFunction): Unit = run { globalFunctions.add(function) }

    fun registerGlobalFunctions(obj: Any) {
        val clazz = obj::class
        for (function in clazz.functions) {
            val annotation = function.annotations.find { it is RegisterOnjFunction } ?: continue
            annotation as RegisterOnjFunction
            assertThatFunctionCanBeRegistered(obj, annotation.type, function)
            val schemaObj = try {
                OnjSchemaParser.parse("params: ${annotation.schema}")
            } catch (e: OnjParserException) {
                throw RuntimeException("schema supplied by function ${function.name} has a syntax error", e)
            }
            schemaObj as OnjSchemaObject
            val schema = schemaObj.keys["params"]
            if (schema !is OnjSchemaArray) throw RuntimeException(
                "schema must be an array!"
            )
            val onjFunction = OnjFunction(
                getRegistrationNameForFunction(annotation.type, function.name),
                schema,
                annotation.type == OnjFunctionType.INFIX
            ) { function.call(obj, *it) as OnjValue }
            addGlobalFunction(onjFunction)
        }
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
            (type == OnjFunctionType.INFIX || type == OnjFunctionType.OPERATOR) &&
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
            if (name !in arrayOf("plus", "minus", "star", "div")) throw RuntimeException(
                "could not register function $name because it is marked as an operator but its name is not one of" +
                "'plus', 'minus', 'star', 'div"
            )
            "operator%$name"
        }

    }

    fun getGlobalVariable(name: String): OnjValue? = globalVariables[name]

    fun getFunction(name: String, args: Array<OnjValue>): OnjFunction? = globalFunctions.firstOrNull {
        return@firstOrNull it.name == name && it.paramsSchema.check(OnjArray(args.toList())) == null
    }

    fun getInfixFunction(name: String, args: Array<OnjValue>): OnjFunction? = globalFunctions
        .filter { it.canBeUsedAsInfix }
        .firstOrNull {
            return@firstOrNull it.name == name && it.paramsSchema.check(OnjArray(args.toList())) == null
        }

}