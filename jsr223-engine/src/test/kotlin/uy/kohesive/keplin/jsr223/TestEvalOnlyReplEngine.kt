package uy.kohesive.keplin.jsr223

import org.junit.Ignore
import org.junit.Test
import java.io.StringWriter
import javax.script.Invocable
import javax.script.ScriptEngineManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestEvalOnlyReplEngine {

    @Test
    fun testJsr223BasicEvalOnlyEngine() {
        val factory = ScriptEngineManager()
        val engine = factory.getEngineByName("keplin-kotin")

        val capture = StringWriter()
        engine.context.writer = capture

        engine.put("z", 33)

        engine.eval("""println("Hello keplin-kotin-eval-only engine")""")

        engine.eval("""val x = 10 + context.getAttribute("z") as Int""")
        engine.eval("""println(x)""")
        val result = engine.eval("""x + 20""")
        assertEquals(63, result)

        val checkEngine = engine.eval("""kotlinScript != null""") as Boolean
        assertTrue(checkEngine)
        val result2 = engine.eval("""x + context.getAttribute("boundValue") as Int""", engine.createBindings().apply {
            put("boundValue", 100)
        })
        assertEquals(143, result2)

        assertEquals("Hello keplin-kotin-eval-only engine\n43\n", capture.toString())
    }

    @Test
    fun testJsr223InvocableFunctionAndMethod() {
        val factory = ScriptEngineManager()
        val engine = factory.getEngineByName("keplin-kotin")
        val invoker = engine as Invocable

        val capture = StringWriter()
        engine.context.writer = capture

        engine.eval("""fun foo(x: Int) = x + 20""")
        assertEquals(50, invoker.invokeFunction("foo", 30))

        engine.eval("""class Bar(val base: Int) { fun foo(x: Int) = x + base; }""")
        val barInstance = engine.eval("""Bar(50)""")
        assertEquals(75, engine.invokeMethod(barInstance, "foo", 25))
    }

    @Test
    @Ignore("Not yet implemented")
    fun testJsr223InvocableInterface() {
        val factory = ScriptEngineManager()
        val engine = factory.getEngineByName("keplin-kotin")
        val invoker = engine as Invocable

        val capture = StringWriter()
        engine.context.writer = capture

        engine.eval("""fun run() { println("running") }""")
        invoker.getInterface(Runnable::class.java).run()
        assertEquals("running", capture.toString())

    }
}