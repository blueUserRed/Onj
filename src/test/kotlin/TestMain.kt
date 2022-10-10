import onj.*

fun main() {//TODO: proper tests

//    val onj = OnjParser.parseFile("src/test/res/Test.onj")
//    println(onj)
//    val onjSchema = OnjSchemaParser.parseFile("src/test/res/Test.onjschema")
//    onjSchema.assertMatches(onj)

//    println(onj)

    val obj = buildOnjObject {
        "test" with 234
        "test2" with 234.0
        "1234" with "haha"
        "sdf" with true
        "sfsd\nf" with null
        "arr" with arrayOf(
            true, 1.0, "kjsdf", null
        )
        "nested" with buildOnjObject {
            "nested1" with "hi"
            "nested2" with "hi2"
        }
    }

    println(obj)

    obj.ifHas<Long>("test") { println(it) }
    println(obj.getOr<Double>("idontexist", 11111.1))

//    println(buildOnjArray(arrayOf(
//        null, "test", 1.01
//    )))
}
