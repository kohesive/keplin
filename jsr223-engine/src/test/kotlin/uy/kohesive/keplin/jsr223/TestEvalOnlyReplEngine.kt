package uy.kohesive.keplin.jsr223

import org.junit.Test
import java.io.StringWriter
import javax.script.ScriptEngineManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestEvalOnlyReplEngine {

    @Test
    fun testJsr223BasicEvalOnlyEngine() {
        val factory = ScriptEngineManager()
        val engine = factory.getEngineByName(EvalOnlyReplEngineFactory.jsr223EngineName)

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

}