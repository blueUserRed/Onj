package onj

import onj.parser.OnjParserException

data class OnjFunction(
    val name: String,
    val paramsSchema: OnjSchemaArray,
    val canBeUsedAsInfix: Boolean = false,
    private val function: (List<OnjValue>) -> OnjValue
) {

//    init {
//        if (canBeUsedAsInfix && paramsSchema.)
//    }

    internal operator fun invoke(
        params: List<OnjValue>,
        functionCallToken: OnjToken,
        code: String,
        fileName: String
    ): OnjValue {
        return try {
            function(params)
        } catch (e: Exception) {
            throw OnjParserException.fromErrorMessage(
                functionCallToken.char,
                code,
                "JVM-Code threw an exception when evaluation function $name",
                fileName,
                e
            )
        }
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