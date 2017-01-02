package uy.kohesive.keplin.util.scripting.resolver.maven


import org.junit.Test
import uy.kohesive.keplin.kotlin.core.scripting.ResettableRepl
import uy.kohesive.keplin.kotlin.util.scripting.ScriptTemplateWithArgs
import uy.kohesive.keplin.kotlin.util.scripting.containingClasspath
import uy.kohesive.keplin.kotlin.util.scripting.resolver.ConfigurableAnnotationBasedScriptDefinition
import uy.kohesive.keplin.kotlin.util.scripting.resolver.local.JarFileScriptDependenciesResolver
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestResettableReplEngine {
    @Test
    fun testWithMavenDependencies() {
        ResettableRepl(scriptDefinition = ConfigurableAnnotationBasedScriptDefinition(
                "varargTemplateWithMavenResolving",
                ScriptTemplateWithArgs::class,
                listOf(JarFileScriptDependenciesResolver(), MavenScriptDependenciesResolver()))
        ).use { repl ->
            val line1 = repl.nextCodeLine("val x = 10")
            val checkResult1 = repl.check(line1)

            assertEquals(line1, checkResult1.codeLine)
            assertTrue(checkResult1.isComplete)

            val compileResult1 = repl.compile(line1)

            assertEquals(line1, compileResult1.codeLine)
            assertFalse(compileResult1.compilerData.hasResult)

            val evalResult1 = repl.eval(compileResult1)

            assertEquals(line1, evalResult1.codeLine)
            assertEquals(Unit, evalResult1.resultValue)

            repl.eval(repl.compile(repl.nextCodeLine("""
                    @file:DependsOnMaven("junit:junit:4.12")
                    org.junit.Assert.assertTrue(true)
            """)))
            repl.eval(repl.compile(repl.nextCodeLine("""org.junit.Assert.assertEquals("123", "123")""")))
        }
    }
}
