import onj.OnjInt
import onj.OnjObject
import onj.OnjParser

fun main() {

    val schemaString = """
        
        !myObj = {
            key1: string?
        }
        
        
        someInt: int
        someFloat: float
        someBool: boolean
        someNullable: boolean?
        myObjects: !myObj[*]
        
    """.trimIndent()

    OnjParser.parse("key: 234")

    val onjObj = OnjParser.parseFile("src/test/res/Test.onj")
    val schema = OnjParser.parseSchema(schemaString)

    println(onjObj)

    schema.assertMatches(onjObj)

    onjObj as OnjObject
    val int = onjObj["someInt"]
    int as OnjInt
    println(int.value)

}
