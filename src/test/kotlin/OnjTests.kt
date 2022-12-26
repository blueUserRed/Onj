import onj.*
import onj.parser.OnjParser
import onj.parser.OnjParserException
import onj.parser.OnjSchemaParser
import onj.customization.OnjConfig.RegisterOnjFunction
import onj.schema.OnjSchema
import onj.schema.OnjSchemaException
import onj.value.*

object OnjTests : Test() {

    @JvmStatic
    fun main(args: Array<String>) {
//        OnjSchemaParser.parse("!test = int[*] test2: [ ...!test ]")
//        OnjSchemaParser.parse("test: [string]")
        run()
//        testImportLoops()
    }

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
        assertEquals(obj.get<Boolean>("containsTest"), false)
        assertEquals(obj.get<Boolean>("contains4"), true)
    }

    @TestCase
    fun testVarAccesses() {
        val obj = onjFile("varAccesses")
        assertEquals(obj.get<String>("hi"), "hi")
        assertEquals(obj.get<Long>("five"), 5L)
        assertEquals(obj.get<Long>("1"), 1L)
    }

    @TestCase
    fun testUnterminatedString() {
        expect<OnjParserException>()
        invalidOnjFile("unterminatedString")
    }

    @TestCase
    fun testDuplicateKey() {
        expect<OnjParserException>()
        invalidOnjFile("duplicateKey")
    }

    @TestCase
    fun testIncorrectSchema() {
        expect<OnjSchemaException>()
        invalidFileWithSchema("vars")
    }

    @TestCase
    fun testUnterminatedBlockComment() {
        onjFile("unterminatedBlockComment")
    }

    @TestCase
    fun testImports() {
        onjFile("imports")
    }

    @TestCase
    fun testImportLoops() {
        expect<OnjParserException>()
        onjFile("importLoop")
    }

    private fun onjFile(name: String): OnjObject = OnjParser.parseFile("src/test/res/files/$name.onj")
    private fun invalidOnjFile(name: String): OnjObject = OnjParser.parseFile("src/test/res/files/invalid/$name.onj")
    private fun onjSchemaFile(name: String): OnjSchema = OnjSchemaParser.parseFile("src/test/res/schemas/$name.onjschema")

    private fun fileWithSchema(name: String): OnjObject {
        val obj = onjFile(name)
        onjSchemaFile(name).assertMatches(obj)
        return obj
    }

    private fun invalidFileWithSchema(name: String): OnjObject {
        val obj = invalidOnjFile(name)
        onjSchemaFile(name).assertMatches(obj)
        return obj
    }

    @RegisterOnjFunction(schema = "[ int, string ]")
    fun myCoolOnjFunction(int: OnjInt, string: OnjString): OnjValue {
        repeat(int.value.toInt()) { println(string.value) }
        return OnjNull()
    }

}