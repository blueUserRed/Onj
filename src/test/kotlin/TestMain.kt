import onj.OnjParser
import onj.OnjSchemaException
import onj.OnjSchemaParser

fun main() {

    val onj = OnjParser.parseFile("src/test/res/Test.onj")
    val onjSchema = OnjSchemaParser.parseFile("src/test/res/Test.onjschema")
    onjSchema.assertMatches(onj)

    println(onj)

}
