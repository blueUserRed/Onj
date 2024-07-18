import onj.serialization.OnjSerializable

@OnjSerializable
data class Cat(
    val name: String,
    val age: Int,
    val color: String,
)
