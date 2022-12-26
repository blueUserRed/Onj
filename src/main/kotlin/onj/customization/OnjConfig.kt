package onj.customization

import onj.parser.OnjParserException
import onj.parser.OnjSchemaParser
import onj.schema.*
import onj.value.*
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.functions
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.createType
import kotlin.reflect.full.starProjectedType

object OnjConfig {

    private val functions: MutableSet<OnjFunction> = mutableSetOf()
    private val customDataTypes: MutableMap<String, KClass<*>> = mutableMapOf()

    init {
        functions.addAll(arrayOf(

            OnjFunction("pow", listOf(OnjSchemaFloat(false), OnjSchemaFloat(false)).toSchemaArray()) {
                OnjFloat((it[0].value as Double).pow(it[1].value as Double))
            },

            OnjFunction("sqrt", listOf(OnjSchemaFloat(false)).toSchemaArray()) {
                OnjFloat(sqrt(it[0].value as Double))
            },

            OnjFunction("operator%plus", listOf(OnjSchemaInt(false), OnjSchemaInt(false)).toSchemaArray(), true) { OnjInt(it[0].value as Long + it[1].value as Long) },
            OnjFunction("operator%plus", listOf(OnjSchemaInt(false), OnjSchemaFloat(false)).toSchemaArray(), true) { OnjFloat(it[0].value as Long + it[1].value as Double) },
            OnjFunction("operator%plus", listOf(OnjSchemaFloat(false), OnjSchemaInt(false)).toSchemaArray(), true) { OnjFloat(it[0].value as Double + it[1].value as Long) },
            OnjFunction("operator%plus", listOf(OnjSchemaFloat(false), OnjSchemaFloat(false)).toSchemaArray(), true) { OnjFloat(it[0].value as Double + it[1].value as Double) },

            OnjFunction("operator%minus", listOf(OnjSchemaInt(false), OnjSchemaInt(false)).toSchemaArray(), true) { OnjInt(it[0].value as Long - it[1].value as Long) },
            OnjFunction("operator%minus", listOf(OnjSchemaInt(false), OnjSchemaFloat(false)).toSchemaArray(), true) { OnjFloat(it[0].value as Long - it[1].value as Double) },
            OnjFunction("operator%minus", listOf(OnjSchemaFloat(false), OnjSchemaInt(false)).toSchemaArray(), true) { OnjFloat(it[0].value as Double - it[1].value as Long) },
            OnjFunction("operator%minus", listOf(OnjSchemaFloat(false), OnjSchemaFloat(false)).toSchemaArray(), true) { OnjFloat(it[0].value as Double - it[1].value as Double) },

            OnjFunction("operator%star", listOf(OnjSchemaInt(false), OnjSchemaInt(false)).toSchemaArray(), true) { OnjInt(it[0].value as Long * it[1].value as Long) },
            OnjFunction("operator%star", listOf(OnjSchemaInt(false), OnjSchemaFloat(false)).toSchemaArray(), true) { OnjFloat(it[0].value as Long * it[1].value as Double) },
            OnjFunction("operator%star", listOf(OnjSchemaFloat(false), OnjSchemaInt(false)).toSchemaArray(), true) { OnjFloat(it[0].value as Double * it[1].value as Long) },
            OnjFunction("operator%star", listOf(OnjSchemaFloat(false), OnjSchemaFloat(false)).toSchemaArray(), true) { OnjFloat(it[0].value as Double * it[1].value as Double) },

            OnjFunction("operator%div", listOf(OnjSchemaInt(false), OnjSchemaInt(false)).toSchemaArray(), true) { OnjInt(it[0].value as Long / it[1].value as Long) },
            OnjFunction("operator%div", listOf(OnjSchemaInt(false), OnjSchemaFloat(false)).toSchemaArray(), true) { OnjFloat(it[0].value as Long / it[1].value as Double) },
            OnjFunction("operator%div", listOf(OnjSchemaFloat(false), OnjSchemaInt(false)).toSchemaArray(), true) { OnjFloat(it[0].value as Double / it[1].value as Long) },
            OnjFunction("operator%div", listOf(OnjSchemaFloat(false), OnjSchemaFloat(false)).toSchemaArray(), true) { OnjFloat(it[0].value as Double / it[1].value as Double) },

            OnjFunction("convert%string", listOf(OnjSchemaAny()).toSchemaArray()) { OnjString(it[0].toString()) },
            OnjFunction("convert%int", listOf(OnjSchemaFloat(false)).toSchemaArray()) { OnjInt((it[0].value as Double).toLong()) },
            OnjFunction("convert%float", listOf(OnjSchemaInt(false)).toSchemaArray()) { OnjFloat((it[0].value as Long).toDouble()) },

            OnjFunction("in", listOf(OnjSchemaAny(), OnjSchemaArray(false, -1, OnjSchemaAny())).toSchemaArray(), true) {
                OnjBoolean(it[0] in (it[1] as OnjArray).value)
            }

        ))
    }

    fun <T> addCustomDataType(name: String, type: KClass<T>) where T : OnjValue {
        customDataTypes[name] = type
    }

    fun getCustomDataType(name: String): KClass<*>? = customDataTypes[name]

    fun addGlobalFunction(function: OnjFunction): Unit = run { functions.add(function) }

//    fun registerGlobalFunctions(obj: Any) {
//        val clazz = obj::class
//        for (function in clazz.functions) {
//            val annotation = function.annotations.find { it is RegisterOnjFunction } ?: continue
//            annotation as RegisterOnjFunction
//            assertThatFunctionCanBeRegistered(obj, function)
//            val schemaObj = try {
//                OnjSchemaParser.parse("params: ${annotation.schema}")
//            } catch (e: OnjParserException) {
//                throw RuntimeException("schema supplied by function ${function.name} has a syntax error", e)
//            }
//            schemaObj as OnjSchemaObject
//            val schema = schemaObj.keys["params"]
//            if (schema !is OnjSchemaArray) throw RuntimeException(
//                "schema must be an array!"
//            )
//            val onjFunction = OnjFunction(
//                function.name,
//                schema
//            ) { function.call(obj, *it) as OnjValue }
//            addGlobalFunction(onjFunction)
//        }
//    }

    private fun assertThatFunctionCanBeRegistered(obj: Any, function: KFunction<*>) {
        val onjValueType = OnjValue::class.createType()
        var isFirst = true
        function.parameters.forEach {

            if (isFirst) {
                isFirst = false
                if (it.kind != KParameter.Kind.VALUE) {
                    val objType = obj::class.starProjectedType
                    if (objType != it.type) throw RuntimeException(
                        "could not register function ${function.name} because it has a receiver or instance " +
                        "parameter that is not of the same type as the object used to register it"
                    )
                    return@forEach
                }
            }

            if (!it.type.isSubtypeOf(onjValueType)) throw RuntimeException(
                "could not register function ${function.name} because" +
                " its parameters include types that don't extend OnjValue"
            )
        }
        if (!function.returnType.isSubtypeOf(onjValueType)) throw RuntimeException(
            "could not register function ${function.name} because its return type dosen't extend OnjValue"
        )
    }

    fun getFunction(name: String, args: Array<OnjValue>): OnjFunction? = functions.firstOrNull {
        return@firstOrNull it.name == name && it.paramsSchema.check(OnjArray(args.toList())) == null
    }
    fun getInfixFunction(name: String, args: Array<OnjValue>): OnjFunction? = functions
        .filter { it.canBeUsedAsInfix }
        .firstOrNull {
            return@firstOrNull it.name == name && it.paramsSchema.check(OnjArray(args.toList())) == null
        }

    @RegisterOnjFunction(schema = "")
    fun test() {

    }

    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.FUNCTION)
    annotation class RegisterOnjFunction(
        val schema: String
    )

}