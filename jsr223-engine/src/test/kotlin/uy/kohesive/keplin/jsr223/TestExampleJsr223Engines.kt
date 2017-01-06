package uy.kohesive.keplin.jsr223

import org.junit.Test
import javax.script.ScriptEngineManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestExampleJsr223Engines {

    @Test
    fun testJsr223BasicEvalOnlyEngine() {
        val factory = ScriptEngineManager()
        val engine = factory.getEngineByName("keplin-kotin-eval-only")

        engine.put("z", 33)

        engine.eval("""val x = 10 + context.getAttribute("z") as Int""")
        val result = engine.eval("""x + 20""")
        assertEquals(63, result)

        val checkEngine = engine.eval("""kotlinScript != null""") as Boolean
        assertTrue(checkEngine)
        val result2 = engine.eval("""x + context.getAttribute("boundValue") as Int""", engine.createBindings().apply {
            put("boundValue", 100)
        })
        assertEquals(143, result2)
    }
}