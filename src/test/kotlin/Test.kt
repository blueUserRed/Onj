import kotlin.reflect.KClass
import kotlin.reflect.full.functions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

// There are libraries that do exactly this but a thousand times better, but that wouldn't be fun
abstract class Test {

    protected var toExpect: KClass<*>? = null

    protected inline fun <reified T : Throwable> expect() {
        toExpect = T::class
    }

    fun run() {

        val tests = this::class
            .functions
            .filter { test ->
                test.annotations.find { it is TestCase } != null
            }

        var failed = 0

        println("running ${tests.size} functions: \n")

        tests.forEach { test ->
            try {
                test.call(this)
                if (toExpect == null) {
                    println("${test.name}: successful")
                } else {
                    println("${test.name} didn't throw expected exception ${toExpect!!.simpleName}")
                    failed++
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

    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class TestCase

    fun assertEquals(first: Any?, second: Any?) {
        if (first != second) throw TestException("assertion failed: $first != $second")
    }

    class TestException(message: String? = null) : RuntimeException(message)

}