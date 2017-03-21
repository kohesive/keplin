package uy.kohesive.keplin.kotlin.script.jsr223

import org.junit.Test
import java.io.StringWriter
import javax.script.Compilable
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class TestCompilableReplEngine {

    fun forEachEngine(f: (ScriptEngine) -> Unit) {
        val factory = ScriptEngineManager()
        listOf(CompilableJsr223ReplEngineFactory.jsr223EngineName).forEach { engineName ->
            val engine = factory.getEngineByName(engineName)
            f(engine)
        }
    }

    @Test
    fun testJsr223CompilableEngineEvalOnlyParts() {
        forEachEngine { engine ->
            val capture = StringWriter()
            engine.context.writer = capture

            engine.put("z", 33)

            engine.eval("""println("Hello keplin-kotin-compilable engine")""")

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

            assertEquals("Hello keplin-kotin-compilable engine\n43\n", capture.toString())
        }
    }


    @Test
    fun testJsr223CompilableEngineExecuteManyTimes() {
        forEachEngine { engine ->
            if (engine is Compilable) {
                val compiler = engine as Compilable

                val capture = StringWriter()
                engine.context.writer = capture

                engine.eval("""println("Hello keplin-kotin-compilable engine")""")

                val compiled1 = compiler.compile("""listOf(1,2,3).joinToString(",")""")
                val compiled2 = compiler.compile("""val x = context.getAttribute("boundValue") as Int + context.getAttribute("z") as Int""")
                val compiled3 = compiler.compile("""x""")

                assertEquals("1,2,3", compiled1.eval())
                assertEquals("1,2,3", compiled1.eval())
                assertEquals("1,2,3", compiled1.eval())
                assertEquals("1,2,3", compiled1.eval())

                compiled2.eval(engine.createBindings().apply {
                    put("boundValue", 100)
                    put("z", 33)
                })
                assertEquals(133, compiled3.eval())
                assertEquals(133, compiled3.eval())
                assertEquals(133, compiled3.eval())

                assertEquals("1,2,3", compiled1.eval())

                engine.put("whatever", 999)
                assertEquals(999, engine.eval("""context.getAttribute("whatever")"""))

                compiled2.eval(engine.createBindings().apply {
                    put("boundValue", 200)
                    put("z", 33)
                })
                assertEquals(233, compiled3.eval())
            }
        }
    }
}