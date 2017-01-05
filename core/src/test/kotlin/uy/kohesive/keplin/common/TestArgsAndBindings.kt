package uy.kohesive.keplin.common

import org.junit.Test
import uy.kohesive.keplin.kotlin.core.scripting.DefaultScriptDefinition
import uy.kohesive.keplin.kotlin.core.scripting.ResettableRepl
import uy.kohesive.keplin.kotlin.core.scripting.ScriptArgsWithTypes
import uy.kohesive.keplin.kotlin.core.scripting.templates.ScriptTemplateWithArgs
import uy.kohesive.keplin.kotlin.core.scripting.templates.ScriptTemplateWithBindings
import kotlin.test.assertEquals

class TestArgsAndBindings {
    @Test
    fun testUsingArgsForScript() {
        val scriptArgs = ScriptArgsWithTypes(arrayOf(arrayOf("100")), arrayOf(Array<String>::class))
        ResettableRepl(scriptDefinition = DefaultScriptDefinition(ScriptTemplateWithArgs::class, scriptArgs)).use { repl ->
            val result = repl.compileAndEval("args[0].toInt()")
            assertEquals(100, result.resultValue)
        }
    }

    @Test
    fun testOverridingArgsOnEachEval() {
        val scriptArgs = ScriptArgsWithTypes(arrayOf(arrayOf("100")), arrayOf(Array<String>::class))
        ResettableRepl(scriptDefinition = DefaultScriptDefinition(ScriptTemplateWithArgs::class, scriptArgs)).use { repl ->
            repl.compileAndEval("val y = args[0].toInt()")
            repl.compileAndEval("val x = args[0].toInt()", ScriptArgsWithTypes(arrayOf(arrayOf("200")), arrayOf(Array<String>::class)))
            val result = repl.compileAndEval("x + y + args[0].toInt() + args[1].toInt()",
                    ScriptArgsWithTypes(arrayOf(arrayOf("1", "2")), arrayOf(Array<String>::class)))
            assertEquals(303, result.resultValue)

            // TODO: decide if retaining last passed args to evaluator should be done (with the same stack that tracks current classloader,
            //       or if not passing in args goes back to the default or none.

            // test that we drop back to original args
            val result2 = repl.compileAndEval("args[0].toInt()")
            assertEquals(100, result2.resultValue)
        }
    }

    @Test
    fun testScriptDefinitionWithMapBindings() {
        val scriptArgs = ScriptArgsWithTypes(arrayOf(mapOf<String, Any?>("x" to 100, "y" to 50)), arrayOf(Map::class))
        ResettableRepl(scriptDefinition = DefaultScriptDefinition(ScriptTemplateWithBindings::class, scriptArgs)).use { repl ->
            repl.compileAndEval("""val y = bindings.get("y") as Int""")
            repl.compileAndEval("""val x = bindings.get("x") as Int""")
            val result = repl.compileAndEval("""x + y + (bindings.get("z") as String).toInt()""",
                    ScriptArgsWithTypes(arrayOf(mapOf<String, Any?>("z" to "3")), arrayOf(Map::class)))
            assertEquals(153, result.resultValue)
        }
    }
}