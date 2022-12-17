package onj

import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.reflect.KClass

object OnjConfig {

    private val functions: MutableSet<OnjFunction> = mutableSetOf()
    private val customDataTypes: MutableMap<String, KClass<*>> = mutableMapOf()

    init {
        functions.addAll(arrayOf(

            OnjFunction("pow", listOf(OnjFloat::class, OnjFloat::class)) {
                OnjFloat((it[0].value as Double).pow(it[1].value as Double))
            },

            OnjFunction("sqrt", listOf(OnjFloat::class)) {
                OnjFloat(sqrt(it[0].value as Double))
            },

            OnjFunction("operator%plus", listOf(OnjInt::class, OnjInt::class)) { OnjInt(it[0].value as Long + it[1].value as Long) },
            OnjFunction("operator%plus", listOf(OnjInt::class, OnjFloat::class)) { OnjFloat(it[0].value as Long + it[1].value as Double) },
            OnjFunction("operator%plus", listOf(OnjFloat::class, OnjInt::class)) { OnjFloat(it[0].value as Double + it[1].value as Long) },
            OnjFunction("operator%plus", listOf(OnjFloat::class, OnjFloat::class)) { OnjFloat(it[0].value as Double + it[1].value as Double) },

            OnjFunction("operator%minus", listOf(OnjInt::class, OnjInt::class)) { OnjInt(it[0].value as Long - it[1].value as Long) },
            OnjFunction("operator%minus", listOf(OnjInt::class, OnjFloat::class)) { OnjFloat(it[0].value as Long - it[1].value as Double) },
            OnjFunction("operator%minus", listOf(OnjFloat::class, OnjInt::class)) { OnjFloat(it[0].value as Double - it[1].value as Long) },
            OnjFunction("operator%minus", listOf(OnjFloat::class, OnjFloat::class)) { OnjFloat(it[0].value as Double - it[1].value as Double) },

            OnjFunction("operator%mult", listOf(OnjInt::class, OnjInt::class)) { OnjInt(it[0].value as Long * it[1].value as Long) },
            OnjFunction("operator%mult", listOf(OnjInt::class, OnjFloat::class)) { OnjFloat(it[0].value as Long * it[1].value as Double) },
            OnjFunction("operator%mult", listOf(OnjFloat::class, OnjInt::class)) { OnjFloat(it[0].value as Double * it[1].value as Long) },
            OnjFunction("operator%mult", listOf(OnjFloat::class, OnjFloat::class)) { OnjFloat(it[0].value as Double * it[1].value as Double) },

            OnjFunction("operator%div", listOf(OnjInt::class, OnjInt::class)) { OnjInt(it[0].value as Long / it[1].value as Long) },
            OnjFunction("operator%div", listOf(OnjInt::class, OnjFloat::class)) { OnjFloat(it[0].value as Long / it[1].value as Double) },
            OnjFunction("operator%div", listOf(OnjFloat::class, OnjInt::class)) { OnjFloat(it[0].value as Double / it[1].value as Long) },
            OnjFunction("operator%div", listOf(OnjFloat::class, OnjFloat::class)) { OnjFloat(it[0].value as Double / it[1].value as Double) },
//
        ))
    }

    fun <T> addCustomDataType(name: String, type: KClass<T>) where T : OnjValue {
        customDataTypes[name] = type
    }

    fun getCustomDataType(name: String): KClass<*>? = customDataTypes[name]

    fun addFunction(function: OnjFunction): Unit = run { functions.add(function) }

    fun getFunction(name: String, args: List<OnjValue>): OnjFunction? = functions.firstOrNull {
        if (it.name != name || it.arity != args.size) return@firstOrNull false

        val params = it.params
        for (i in args.indices) {
            if (!params[i].isInstance(args[i])) return@firstOrNull false
        }

        return@firstOrNull true
    }


}