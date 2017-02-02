package uy.kohesive.keplin.kotlin.script


import org.jetbrains.kotlin.cli.common.repl.ScriptArgsWithTypes
import org.junit.Test
import uy.kohesive.keplin.kotlin.script.util.findClassJars
import uy.kohesive.keplin.kotlin.script.util.findKotlinCompilerJars
import kotlin.test.assertEquals


class TestSimpleReplEngineRecursion {
    @Test
    fun testRecursingScriptsDifferentEngines() {
        val extraClasspath = findClassJars(SimplifiedRepl::class) +
                findKotlinCompilerJars(false)

        SimplifiedRepl(additionalClasspath = extraClasspath).use { repl ->
            val outerEval = repl.compileAndEval("""
                 import uy.kohesive.keplin.kotlin.script.SimplifiedRepl
                 import uy.kohesive.keplin.kotlin.script.util.*

                 val extraClasspath =  findClassJars(SimplifiedRepl::class) +
                                       findKotlinCompilerJars(false)
                 val result = SimplifiedRepl(additionalClasspath = extraClasspath).use { repl ->
                    val innerEval = repl.compileAndEval("println(\"inner world\"); 100")
                    innerEval.resultValue
                 }
                 result
            """)
            assertEquals(100, outerEval.resultValue)
        }
    }

    @Test
    fun testRecursingScriptsSameEngines() {
        val extraClasspath = findClassJars(SimplifiedRepl::class) +
                findKotlinCompilerJars(false)
        SimplifiedRepl(scriptDefinition = KotlinScriptDefinitionEx(TestRecursiveScriptContext::class, null),
                additionalClasspath = extraClasspath,
                sharedHostClassLoader = Thread.currentThread().contextClassLoader).apply {
            fallbackArgs = ScriptArgsWithTypes(arrayOf(this, mapOf<String, Any?>("x" to 100, "y" to 50)),
                    arrayOf(SimplifiedRepl::class, Map::class))
        }.use { repl ->
            val outerEval = repl.compileAndEval("""
                 val x = bindings.get("x") as Int
                 val y = bindings.get("y") as Int
                 kotlinScript.compileAndEval("println(\"inner world\"); ${"$"}x+${"$"}y").resultValue
            """)
            assertEquals(150, outerEval.resultValue)
        }
    }
}

abstract class TestRecursiveScriptContext(val kotlinScript: SimplifiedRepl, val bindings: Map<String, Any?>)
