import onj.customization.Namespace.OnjNamespace
import onj.customization.Namespace.OnjNamespaceDatatypes
import onj.customization.Namespace.OnjNamespaceVariables
import onj.customization.OnjFunction.RegisterOnjFunction
import onj.value.OnjString
import onj.value.OnjValue
import kotlin.reflect.KClass

@OnjNamespace
object TestNamespace {

    @OnjNamespaceDatatypes
    val datatypes: Map<String, KClass<*>> = mapOf(
        "TestType" to TestOnjValue::class
    )

    @OnjNamespaceVariables
    val variables: Map<String, OnjValue> = mapOf(
        "globalVar" to TestOnjValue("from variable")
    )

    @RegisterOnjFunction(schema = "params: [string]")
    fun testString(value: OnjString): OnjValue = TestOnjValue(value.value)

    @RegisterOnjFunction(schema = "use Test; params: [TestType]")
    fun testIdentity(value: TestOnjValue): TestOnjValue = value

}

class TestOnjValue(override val value: String) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        info.builder.append(value)
    }
}
