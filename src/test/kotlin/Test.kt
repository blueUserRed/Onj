import kotlin.reflect.KClass
import kotlin.reflect.full.functions

// There are libraries that do exactly this but a thousand times better, but that wouldn't be fun
abstract class Test {

    private var toExpect: KClass<*>? = null

    fun <T : Throwable> expect(t: KClass<T>) {
        toExpect = t
    }

    fun run() {

        val tests = this::class
            .functions
            .filter { it.name.startsWith("test") }

        var failed = 0

        println("running ${tests.size} functions: \n")

        tests.forEach { test ->
            try {
                test.call(this)
                if (toExpect == null) {
                    println("${test.name}: successful")
                } else {
                    println("${test.name}, but expected exception ${toExpect!!.simpleName}")
                }
            } catch (t: Throwable) {
                val e = t.cause
                if (toExpect == null) {
                    failed++
                    println("${test.name} threw exception:\n ${e?.stackTraceToString()}")
                } else if (toExpect!!.isInstance(e)) {
                    println("${test.name}: successful")
                    toExpect = null
                } else {
                    println("${test.name}: expected exception of type ${toExpect!!.simpleName}, " +
                            "but got ${e?.let { it::class.simpleName }}")
                    failed++
                    toExpect = null
                }
            }
        }

        println("$failed/${tests.size} failed")

    }

    fun assertEquals(first: Any?, second: Any?) {
        if (first != second) throw TestException()
    }

    class TestException : RuntimeException()

}