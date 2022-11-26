import onj.*
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

fun main() {
    //TODO: proper tests
    //TODO: boolean calculations

    OnjFunction.addFunction(
        OnjFunction(
            "path",
            listOf(OnjString::class)
        ) {
            Test(Paths.get(it[0].value as String))
        }
    )

    println(OnjParser.parseFile("src/test/res/Test.onj"))

}

class Test(
    override val value: Path
) : OnjValue() {

    override fun toString(): String = "path('${value}')"
    override fun toString(indentationLevel: Int): String = toString()
    override fun toJsonString(): String = "'$value'"
    override fun toJsonString(indentationLevel: Int): String = toJsonString()


}
