package onj.customization

import onj.parser.OnjParserException
import onj.parser.OnjSchemaParser
import onj.parser.OnjToken
import onj.schema.OnjSchemaArray
import onj.schema.OnjSchemaObject
import onj.value.OnjValue

data class OnjFunction(
    val name: String,
    private val schema: String,
    val canBeUsedAsInfix: Boolean = false,
    private val function: (Array<OnjValue>) -> OnjValue
) {

    val paramsSchema: OnjSchemaArray by lazy {
        val schemaObj = try {
            OnjSchemaParser.parse(schema)
        } catch (e: OnjParserException) {
            throw RuntimeException("schema supplied by function $name has a syntax error", e)
        }
        schemaObj as OnjSchemaObject
        val schema = schemaObj.keys["params"]
        if (schema !is OnjSchemaArray) throw RuntimeException(
            "schema supplied by function $name must hava a key 'params' with a value of type OnjSchemaArray"
        )
        schema
    }

    internal operator fun invoke(
        params: Array<OnjValue>,
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

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + schema.hashCode()
        result = 31 * result + function.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as OnjFunction
        if (name != other.name) return false
        if (schema != other.schema) return false
        return true
    }


    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.FUNCTION)
    annotation class RegisterOnjFunction(
        val schema: String,
        val type: OnjFunctionType = OnjFunctionType.NORMAL
    ) {

        enum class OnjFunctionType {
            NORMAL, INFIX, CONVERSION, OPERATOR
        }
    }

}