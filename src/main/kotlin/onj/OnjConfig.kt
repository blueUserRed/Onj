package onj

import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.reflect.KClass

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

    fun addFunction(function: OnjFunction): Unit = run { functions.add(function) }

    fun getFunction(name: String, args: List<OnjValue>): OnjFunction? = functions.firstOrNull {
        return@firstOrNull it.name == name && it.paramsSchema.check(OnjArray(args)) == null
    }
    fun getInfixFunction(name: String, args: List<OnjValue>): OnjFunction? = functions
        .filter { it.canBeUsedAsInfix }
        .firstOrNull {
            return@firstOrNull it.name == name && it.paramsSchema.check(OnjArray(args)) == null
        }


}