import onj.OnjObject
import onj.OnjSchema
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser

object OnjTests : Test() {

    @JvmStatic
    fun main(args: Array<String>): Unit = run()

    fun testBasic() {
        fileWithSchema("basic")
    }

    fun testVars() {
        fileWithSchema("vars")
    }

    fun testStringEscapes() {
        val obj = onjFile("stringEscapes")
        assertEquals(
            obj.get<String>("escapes"),
            "n: \n, r: \r, t: \t, \" \' \\"
        )
    }

    private fun onjFile(name: String): OnjObject = OnjParser.parseFile("src/test/res/files/$name.onj")
    private fun onjSchemaFile(name: String): OnjSchema = OnjSchemaParser.parseFile("src/test/res/schemas/$name.onjschema")

    private fun fileWithSchema(name: String): OnjObject {
        val obj = onjFile(name)
        onjSchemaFile(name).assertMatches(obj)
        return obj
    }

}