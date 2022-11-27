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

}