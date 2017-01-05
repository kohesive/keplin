package uy.kohesive.keplin.common

import org.junit.Test
import uy.kohesive.keplin.kotlin.core.scripting.*
import uy.kohesive.keplin.kotlin.core.scripting.templates.ScriptTemplateWithArgs
import uy.kohesive.keplin.kotlin.util.scripting.resolver.AnnotationTriggeredScriptDefinition
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.junit.JUnitAsserter

class TestResolversAndImports {
    @Test
    fun testWithoutDefaultImportsFails() {
        ResettableRepl(scriptDefinition = KotlinScriptDefinitionEx(ScriptTemplateWithArgs::class,
                ScriptArgsWithTypes(EMPTY_SCRIPT_ARGS, EMPTY_SCRIPT_ARGS_TYPES))).use { repl ->
            try {
                repl.compileAndEval("""val now = Instant.now()""")
                JUnitAsserter.fail("Expected compile error")
            } catch (ex: ReplCompilerException) {
                assertTrue("unresolved reference: Instant" in ex.message!!)
            }
        }
    }

    @Test
    fun testWithDefaultImports() {
        ResettableRepl(scriptDefinition = KotlinScriptDefinitionEx(ScriptTemplateWithArgs::class,
                ScriptArgsWithTypes(EMPTY_SCRIPT_ARGS, EMPTY_SCRIPT_ARGS_TYPES),
                defaultImports = listOf("java.time.*"))).use { repl ->
            val now = Instant.now()
            repl.compileAndEval("""val now = Instant.now()""")
            val result = repl.compileAndEval("""now""").resultValue as Instant
            assertTrue(result >= now)
        }
    }

    fun makeConfigurableEngine(defaultImports: List<String> = emptyList()): ResettableRepl =
            ResettableRepl(scriptDefinition = AnnotationTriggeredScriptDefinition(
                    "varargTemplateWithMavenResolving",
                    ScriptTemplateWithArgs::class,
                    ScriptArgsWithTypes(EMPTY_SCRIPT_ARGS, EMPTY_SCRIPT_ARGS_TYPES),
                    defaultImports = defaultImports))

    @Test
    fun testConfigurableResolversEmpty() {
        makeConfigurableEngine().use { repl ->
            repl.compileAndEval("""val x = 10 + 100""")
            assertEquals(110, repl.compileAndEval("""x""").resultValue)
        }
    }

    @Test
    fun testConfigurableResolversFailsWithoutCorrectImport() {
        makeConfigurableEngine().use { repl ->
            try {
                repl.compileAndEval("""val now = Instant.now()""")
                JUnitAsserter.fail("Expected compile error")
            } catch (ex: ReplCompilerException) {
                assertTrue("unresolved reference: Instant" in ex.message!!)
            }
        }
    }

    @Test
    fun testConfigurableResolversWithDefaultImports() {
        makeConfigurableEngine(defaultImports = listOf("java.time.*")).use { repl ->
            val now = Instant.now()
            repl.compileAndEval("""val now = Instant.now()""")
            val result = repl.compileAndEval("""now""").resultValue as Instant
            assertTrue(result >= now)
        }
    }
}