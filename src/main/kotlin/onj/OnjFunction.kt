package onj

data class OnjFunction(
    val name: String,
    val paramsSchema: OnjSchemaArray,
    private val function: (List<OnjValue>) -> OnjValue
) {

    operator fun invoke(params: List<OnjValue>): OnjValue {
        paramsSchema.assertMatches(OnjArray(params))
        return function(params)
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is OnjFunction) return false
        return other.name == this.name
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + paramsSchema.hashCode()
        result = 31 * result + function.hashCode()
        return result
    }

}