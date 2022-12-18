import onj.OnjObject
import onj.OnjSchema
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser

object OnjTests : Test() {

    @JvmStatic
    fun main(args: Array<String>): Unit = run()

    @TestCase
    fun testBasic() {
        fileWithSchema("basic")
    }

    @TestCase
    fun testVars() {
        fileWithSchema("vars")
    }

    @TestCase
    fun testStringEscapes() {
        val obj = onjFile("stringEscapes")
        assertEquals(
            obj.get<String>("escapes"),
            "n: \n, r: \r, t: \t, \" \' \\"
        )
    }

    @TestCase
    fun testCalculations() {
        val obj = fileWithSchema("calculations")
        assertEquals(obj.get<Long>("is10"), 10L)
        assertEquals(obj.get<String>("string10"), "10")
        assertEquals(obj.get<String>("string8"), "8")
    }

    @TestCase
    fun testVarAccesses() {
        val obj = onjFile("varAccesses")
        assertEquals(obj.get<String>("hi"), "hi")
        assertEquals(obj.get<Long>("five"), 5L)
        assertEquals(obj.get<Long>("1"), 1L)
    }

    private fun onjFile(name: String): OnjObject = OnjParser.parseFile("src/test/res/files/$name.onj")
    private fun onjSchemaFile(name: String): OnjSchema = OnjSchemaParser.parseFile("src/test/res/schemas/$name.onjschema")

    private fun fileWithSchema(name: String): OnjObject {
        val obj = onjFile(name)
        onjSchemaFile(name).assertMatches(obj)
        return obj
    }

}