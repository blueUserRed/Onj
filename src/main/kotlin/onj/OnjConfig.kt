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
            }
//
        ))
    }

    fun <T> addCustomDataType(name: String, type: KClass<T>) where T : OnjValue {
        customDataTypes[name] = type
    }

    fun getCustomDataType(name: String): KClass<*>? = customDataTypes[name]

    fun addFunction(function: OnjFunction): Unit = run { functions.add(function) }

    fun getFunction(name: String, arity: Int): OnjFunction? {
        for (function in functions) {
            if (function.arity == arity && function.name == name) return function
        }
        return null
    }


}