import onj.customization.OnjConfig
import onj.parser.OnjParser
import onj.parser.OnjParserException
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.schema.OnjSchemaException
import onj.value.*

object OnjTests : Test() {

    @JvmStatic
    fun main(args: Array<String>) {
        OnjConfig.registerNameSpace("Test", TestNamespace)
        run()
    }

    @TestCase
    fun testBasic() {
        val obj = fileWithSchema("basic")
        OnjParser.parse(obj.toString())
    }

    @TestCase
    fun testVars() {
        val obj = fileWithSchema("vars")
        OnjParser.parse(obj.toString())
    }

    @TestCase
    fun testStringEscapes() {
        val obj = onjFile("stringEscapes")
        obj as OnjObject
        assertEquals(
            obj.get<String>("escapes"),
            "n: \n, r: \r, t: \t, \" \' \\"
        )
    }

    @TestCase
    fun testCalculations() {
        val obj = fileWithSchema("calculations")
        obj as OnjObject
        assertEquals(obj.get<Long>("is10"), 10L)
        assertEquals(obj.get<String>("string10"), "10")
        assertEquals(obj.get<String>("string8"), "8")
        assertEquals(obj.get<Boolean>("containsTest"), false)
        assertEquals(obj.get<Boolean>("contains4"), true)
    }

    @TestCase
    fun testVarAccesses() {
        val obj = onjFile("varAccesses")
        obj as OnjObject
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

    @TestCase
    fun testNamedObjects() {
        fileWithSchema("namedObjects")
    }

    @TestCase
    fun testGlobalInclude() {
        val obj = fileWithSchema("globalInclude")
        obj as OnjObject
        assertEquals(
            obj.get<String>("test"),
            "abc"
        )
    }

    @TestCase
    fun testNamespace() {
        fileWithSchema("namespace")
    }

    private fun onjFile(name: String): OnjValue = OnjParser.parseFile("src/test/res/files/$name.onj")
    private fun invalidOnjFile(name: String): OnjValue = OnjParser.parseFile("src/test/res/files/invalid/$name.onj")
    private fun onjSchemaFile(name: String): OnjSchema = OnjSchemaParser.parseFile("src/test/res/schemas/$name.onjschema")

    private fun fileWithSchema(name: String): OnjValue {
        val obj = onjFile(name)
        onjSchemaFile(name).assertMatches(obj)
        return obj
    }

    private fun invalidFileWithSchema(name: String): OnjValue {
        val obj = invalidOnjFile(name)
        onjSchemaFile(name).assertMatches(obj)
        return obj
    }

}