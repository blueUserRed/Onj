package onj

import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.reflect.KClass

data class OnjFunction(
    val name: String,
    private val params: List<KClass<*>>,
    private val function: (List<OnjValue>) -> OnjValue
) {

    val arity = params.size

    operator fun invoke(params: List<OnjValue>): OnjValue {
        if (params.size != arity) {
            throw RuntimeException("function takes $arity arguments, but was called with ${params.size}")
        }
        for (i in params.indices) {
            if (!this.params[i].isInstance(params[i])) {
                throw RuntimeException("operand at position ${i + 1} is expected to have type" +
                        "${this.params[i].simpleName} but is ${params[i]::class.simpleName}")
            }
        }
        return function(params)
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is OnjFunction) return false
        return other.name == this.name && other.arity == this.arity
    }

    companion object {

        private val functions: MutableSet<OnjFunction> = mutableSetOf()

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

        fun addFunction(function: OnjFunction): Unit = run { functions.add(function) }

        fun getFunction(name: String, arity: Int): OnjFunction? {
            for (function in functions) {
                if (function.arity == arity && function.name == name) return function
            }
            return null
        }

    }

}