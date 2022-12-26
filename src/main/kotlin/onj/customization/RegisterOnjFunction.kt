package onj.customization

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

