package uy.kohesive.keplin.common

import org.junit.Test
import uy.kohesive.keplin.kotlin.core.scripting.DefaultScriptDefinition
import uy.kohesive.keplin.kotlin.core.scripting.ResettableRepl
import uy.kohesive.keplin.kotlin.core.scripting.ScriptArgsWithTypes
import uy.kohesive.keplin.kotlin.util.scripting.findClassJars
import uy.kohesive.keplin.kotlin.util.scripting.findKotlinCompilerJars
import kotlin.test.assertEquals

class TestEngineRecursion {
    @Test
    fun testRecursingScriptsDifferentEngines() {
        val extraClasspath = findClassJars(ResettableRepl::class) +
                findKotlinCompilerJars(false)

        ResettableRepl(additionalClasspath = extraClasspath).use { repl ->
            val outerEval = repl.compileAndEval("""
                 import uy.kohesive.keplin.kotlin.core.scripting.ResettableRepl
                 import uy.kohesive.keplin.kotlin.util.scripting.*

                 val extraClasspath =  findClassJars(ResettableRepl::class) +
                                       findKotlinCompilerJars(false)
                 val result = ResettableRepl(additionalClasspath = extraClasspath).use { repl ->
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
        val extraClasspath = findClassJars(ResettableRepl::class) +
                findKotlinCompilerJars(false)
        ResettableRepl(scriptDefinition = DefaultScriptDefinition(TestRecursiveScriptContext::class, null),
                additionalClasspath = extraClasspath).apply {
            scriptArgs = ScriptArgsWithTypes(arrayOf(this, mapOf<String, Any?>("x" to 100, "y" to 50)),
                    arrayOf(ResettableRepl::class, Map::class))
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

abstract class TestRecursiveScriptContext(val kotlinScript: ResettableRepl, val bindings: Map<String, Any?>)
