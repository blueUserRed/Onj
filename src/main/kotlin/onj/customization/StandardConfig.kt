package onj.customization

import kotlin.math.pow
import onj.customization.RegisterOnjFunction.*
import onj.value.*

object StandardConfig {

    internal fun bindDefaultVariables() {
        OnjConfig.bindGlobalVariable("true", OnjBoolean(true))
        OnjConfig.bindGlobalVariable("false", OnjBoolean(false))
        OnjConfig.bindGlobalVariable("NaN", OnjFloat(Double.NaN))
        OnjConfig.bindGlobalVariable("infinity", OnjFloat(Double.POSITIVE_INFINITY))
    }

    @RegisterOnjFunction(schema = "float[ 2 ]", type = OnjFunctionType.INFIX)
    fun pow(x: OnjFloat, to: OnjFloat): OnjFloat = OnjFloat(x.value.pow(to.value))

    @RegisterOnjFunction(schema = "[ float ]")
    fun sqrt(of: OnjFloat): OnjFloat = OnjFloat(kotlin.math.sqrt(of.value))

    @RegisterOnjFunction(schema = "[ *, *[] ]", type = OnjFunctionType.INFIX )
    fun `in`(search: OnjValue, arr: OnjArray): OnjBoolean = OnjBoolean(search in arr.value)

    @RegisterOnjFunction(schema = "int[ 2 ]", type = OnjFunctionType.OPERATOR)
    fun plus(a: OnjInt, b: OnjInt): OnjInt = OnjInt(a.value + b.value)

    @RegisterOnjFunction(schema = "[ int, float ]", type = OnjFunctionType.OPERATOR)
    fun plus(a: OnjInt, b: OnjFloat): OnjFloat = OnjFloat(a.value + b.value)

    @RegisterOnjFunction(schema = "[ float, int ]", type = OnjFunctionType.OPERATOR)
    fun plus(a: OnjFloat, b: OnjInt): OnjFloat = OnjFloat(a.value + b.value)

    @RegisterOnjFunction(schema = "float[ 2 ]", type = OnjFunctionType.OPERATOR)
    fun plus(a: OnjFloat, b: OnjFloat): OnjFloat = OnjFloat(a.value + b.value)


    @RegisterOnjFunction(schema = "int[ 2 ]", type = OnjFunctionType.OPERATOR)
    fun minus(a: OnjInt, b: OnjInt): OnjInt = OnjInt(a.value - b.value)

    @RegisterOnjFunction(schema = "[ int, float ]", type = OnjFunctionType.OPERATOR)
    fun minus(a: OnjInt, b: OnjFloat): OnjFloat = OnjFloat(a.value - b.value)

    @RegisterOnjFunction(schema = "[ float, int ]", type = OnjFunctionType.OPERATOR)
    fun minus(a: OnjFloat, b: OnjInt): OnjFloat = OnjFloat(a.value - b.value)

    @RegisterOnjFunction(schema = "float[ 2 ]", type = OnjFunctionType.OPERATOR)
    fun minus(a: OnjFloat, b: OnjFloat): OnjFloat = OnjFloat(a.value - b.value)


    @RegisterOnjFunction(schema = "int[ 2 ]", type = OnjFunctionType.OPERATOR)
    fun star(a: OnjInt, b: OnjInt): OnjInt = OnjInt(a.value * b.value)

    @RegisterOnjFunction(schema = "[ int, float ]", type = OnjFunctionType.OPERATOR)
    fun star(a: OnjInt, b: OnjFloat): OnjFloat = OnjFloat(a.value * b.value)

    @RegisterOnjFunction(schema = "[ float, int ]", type = OnjFunctionType.OPERATOR)
    fun star(a: OnjFloat, b: OnjInt): OnjFloat = OnjFloat(a.value * b.value)

    @RegisterOnjFunction(schema = "float[ 2 ]", type = OnjFunctionType.OPERATOR)
    fun star(a: OnjFloat, b: OnjFloat): OnjFloat = OnjFloat(a.value * b.value)


    @RegisterOnjFunction(schema = "int[ 2 ]", type = OnjFunctionType.OPERATOR)
    fun div(a: OnjInt, b: OnjInt): OnjInt = OnjInt(a.value / b.value)

    @RegisterOnjFunction(schema = "[ int, float ]", type = OnjFunctionType.OPERATOR)
    fun div(a: OnjInt, b: OnjFloat): OnjFloat = OnjFloat(a.value / b.value)

    @RegisterOnjFunction(schema = "[ float, int ]", type = OnjFunctionType.OPERATOR)
    fun div(a: OnjFloat, b: OnjInt): OnjFloat = OnjFloat(a.value / b.value)

    @RegisterOnjFunction(schema = "float[ 2 ]", type = OnjFunctionType.OPERATOR)
    fun div(a: OnjFloat, b: OnjFloat): OnjFloat = OnjFloat(a.value / b.value)


    @RegisterOnjFunction(schema = "[ * ]", type = OnjFunctionType.CONVERSION)
    fun string(toConvert: OnjValue): OnjString = OnjString(toConvert.toString())

    @RegisterOnjFunction(schema = "[ int ]", type = OnjFunctionType.CONVERSION)
    fun float(toConvert: OnjInt): OnjFloat = OnjFloat(toConvert.value.toDouble())

    @RegisterOnjFunction(schema = "[ float ]", type = OnjFunctionType.CONVERSION)
    fun int(toConvert: OnjFloat): OnjInt = OnjInt(toConvert.value.toLong())

}