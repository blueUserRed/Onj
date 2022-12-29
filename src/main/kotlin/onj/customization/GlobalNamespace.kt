package onj.customization

import kotlin.math.pow
import onj.customization.OnjFunction.RegisterOnjFunction
import onj.customization.Namespace.OnjNamespace
import onj.customization.OnjFunction.RegisterOnjFunction.*
import onj.value.*
import onj.customization.Namespace.OnjNamespaceVariables
import onj.customization.Namespace.OnjNamespaceDatatypes
import kotlin.reflect.KClass

@OnjNamespace
object GlobalNamespace {

    @OnjNamespaceVariables
    val variables: Map<String, OnjValue> = mapOf(
        "true" to OnjBoolean(true),
        "false" to OnjBoolean(false),
        "NaN" to OnjFloat(Double.NaN),
        "infinity" to OnjFloat(Double.POSITIVE_INFINITY),
    )

    @OnjNamespaceDatatypes
    val datatypes: Map<String, KClass<*>> = mapOf(
        "Test" to OnjString::class
    )


    @RegisterOnjFunction(schema = "params: float[2]", type = OnjFunctionType.INFIX)
    fun pow(x: OnjFloat, to: OnjFloat): OnjFloat = OnjFloat(x.value.pow(to.value))

    @RegisterOnjFunction(schema = "params: [float]")
    fun sqrt(of: OnjFloat): OnjFloat = OnjFloat(kotlin.math.sqrt(of.value))

    @RegisterOnjFunction(schema = "params: [*, *[]]", type = OnjFunctionType.INFIX )
    fun `in`(search: OnjValue, arr: OnjArray): OnjBoolean = OnjBoolean(search in arr.value)

    @RegisterOnjFunction(schema = "params: int[2]", type = OnjFunctionType.OPERATOR)
    fun plus(a: OnjInt, b: OnjInt): OnjInt = OnjInt(a.value + b.value)

    @RegisterOnjFunction(schema = "params: [int, float]", type = OnjFunctionType.OPERATOR)
    fun plus(a: OnjInt, b: OnjFloat): OnjFloat = OnjFloat(a.value + b.value)

    @RegisterOnjFunction(schema = "params: [float, int]", type = OnjFunctionType.OPERATOR)
    fun plus(a: OnjFloat, b: OnjInt): OnjFloat = OnjFloat(a.value + b.value)

    @RegisterOnjFunction(schema = "params: float[2]", type = OnjFunctionType.OPERATOR)
    fun plus(a: OnjFloat, b: OnjFloat): OnjFloat = OnjFloat(a.value + b.value)


    @RegisterOnjFunction(schema = "params: int[2]", type = OnjFunctionType.OPERATOR)
    fun minus(a: OnjInt, b: OnjInt): OnjInt = OnjInt(a.value - b.value)

    @RegisterOnjFunction(schema = "params: [int, float]", type = OnjFunctionType.OPERATOR)
    fun minus(a: OnjInt, b: OnjFloat): OnjFloat = OnjFloat(a.value - b.value)

    @RegisterOnjFunction(schema = "params: [float, int]", type = OnjFunctionType.OPERATOR)
    fun minus(a: OnjFloat, b: OnjInt): OnjFloat = OnjFloat(a.value - b.value)

    @RegisterOnjFunction(schema = "params: float[2]", type = OnjFunctionType.OPERATOR)
    fun minus(a: OnjFloat, b: OnjFloat): OnjFloat = OnjFloat(a.value - b.value)


    @RegisterOnjFunction(schema = "params: int[2]", type = OnjFunctionType.OPERATOR)
    fun star(a: OnjInt, b: OnjInt): OnjInt = OnjInt(a.value * b.value)

    @RegisterOnjFunction(schema = "params: [int, float]", type = OnjFunctionType.OPERATOR)
    fun star(a: OnjInt, b: OnjFloat): OnjFloat = OnjFloat(a.value * b.value)

    @RegisterOnjFunction(schema = "params: [float, int]", type = OnjFunctionType.OPERATOR)
    fun star(a: OnjFloat, b: OnjInt): OnjFloat = OnjFloat(a.value * b.value)

    @RegisterOnjFunction(schema = "params: float[2]", type = OnjFunctionType.OPERATOR)
    fun star(a: OnjFloat, b: OnjFloat): OnjFloat = OnjFloat(a.value * b.value)


    @RegisterOnjFunction(schema = "params: int[2]", type = OnjFunctionType.OPERATOR)
    fun div(a: OnjInt, b: OnjInt): OnjInt = OnjInt(a.value / b.value)

    @RegisterOnjFunction(schema = "params: [int, float]", type = OnjFunctionType.OPERATOR)
    fun div(a: OnjInt, b: OnjFloat): OnjFloat = OnjFloat(a.value / b.value)

    @RegisterOnjFunction(schema = "params: [float, int]", type = OnjFunctionType.OPERATOR)
    fun div(a: OnjFloat, b: OnjInt): OnjFloat = OnjFloat(a.value / b.value)

    @RegisterOnjFunction(schema = "params: float[2]", type = OnjFunctionType.OPERATOR)
    fun div(a: OnjFloat, b: OnjFloat): OnjFloat = OnjFloat(a.value / b.value)


    @RegisterOnjFunction(schema = "params: [int]", type = OnjFunctionType.OPERATOR)
    fun unaryMinus(a: OnjInt): OnjInt = OnjInt(-a.value)

    @RegisterOnjFunction(schema = "params: [float]", type = OnjFunctionType.OPERATOR)
    fun unaryMinus(a: OnjFloat): OnjFloat = OnjFloat(-a.value)


    @RegisterOnjFunction(schema = "params: [*]", type = OnjFunctionType.CONVERSION)
    fun string(toConvert: OnjValue): OnjString = OnjString(toConvert.toString())

    @RegisterOnjFunction(schema = "params: [int]", type = OnjFunctionType.CONVERSION)
    fun float(toConvert: OnjInt): OnjFloat = OnjFloat(toConvert.value.toDouble())

    @RegisterOnjFunction(schema = "params: [float]", type = OnjFunctionType.CONVERSION)
    fun int(toConvert: OnjFloat): OnjInt = OnjInt(toConvert.value.toLong())

}