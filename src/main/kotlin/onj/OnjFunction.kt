package onj

import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.reflect.KClass

data class OnjFunction<in T>(
    val name: String,
    private val params: List<KClass<OnjFloat>>,
    private val function: (List<T>) -> OnjValue
) where T : OnjValue {

    val arity = params.size

    operator fun invoke(params: List<T>): OnjValue {
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
        if (other !is OnjFunction<*>) return false
        return other.name == this.name && other.arity == this.arity
    }

    companion object {

        private val functions: MutableSet<OnjFunction<OnjValue>> = mutableSetOf()

        init {
            functions.addAll(arrayOf(

                OnjFunction("pow", listOf(OnjFloat::class, OnjFloat::class)) {
                    OnjFloat((it[0].value as Double).pow(it[1].value as Double))
                },

                OnjFunction("sqrt", listOf(OnjFloat::class)) {
                    OnjFloat(sqrt(it[0].value as Double))
                }

            ))
        }

        fun addFunction(function: OnjFunction<OnjValue>): Unit = run { functions.add(function) }

        fun getFunction(name: String, arity: Int): OnjFunction<OnjValue>? {
            for (function in functions) {
                if (function.arity == arity && function.name == name) return function
            }
            return null
        }

    }

}