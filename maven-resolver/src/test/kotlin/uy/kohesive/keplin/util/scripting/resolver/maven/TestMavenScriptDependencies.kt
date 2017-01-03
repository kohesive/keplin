package uy.kohesive.keplin.util.scripting.resolver.maven


import org.junit.Test
import uy.kohesive.keplin.kotlin.core.scripting.CompileErrorException
import uy.kohesive.keplin.kotlin.core.scripting.ResettableRepl
import uy.kohesive.keplin.kotlin.util.scripting.ScriptTemplateWithArgs
import uy.kohesive.keplin.kotlin.util.scripting.resolver.ConfigurableAnnotationBasedScriptDefinition
import uy.kohesive.keplin.kotlin.util.scripting.resolver.local.JarFileScriptDependenciesResolver
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class TestMavenScriptDependencies {
    @Test
    fun testWithMavenDependencies() {
        ResettableRepl(scriptDefinition = ConfigurableAnnotationBasedScriptDefinition(
                "varargTemplateWithMavenResolving",
                ScriptTemplateWithArgs::class,
                listOf(JarFileScriptDependenciesResolver(), MavenScriptDependenciesResolver()))
        ).use { repl ->
            repl.compileAndEval("""
                    @file:DependsOnMaven("junit:junit:4.12")
                    org.junit.Assert.assertTrue(true)
            """)
            repl.compileAndEval("""org.junit.Assert.assertEquals("123", "123")""")
        }
    }

    @Test
    fun testResolveLibWithExtensionFunctions() {
        ResettableRepl(scriptDefinition = ConfigurableAnnotationBasedScriptDefinition(
                "varargTemplateWithMavenResolving",
                ScriptTemplateWithArgs::class,
                listOf(JarFileScriptDependenciesResolver(), MavenScriptDependenciesResolver()))
        ).use { repl ->
            repl.compileAndEval("""@file:DependsOnMaven("uy.klutter:klutter-core-jdk6:1.20.1")""")
            repl.compileAndEval("""import uy.klutter.core.jdk.*""")
            val result = repl.compileAndEval("""10.minimum(100).maximum(50)""")
            assertEquals(50, result.resultValue)
        }
    }

    @Test
    fun testMavenWithAFewThreads() {
        ResettableRepl(scriptDefinition = ConfigurableAnnotationBasedScriptDefinition(
                "varargTemplateWithMavenResolving",
                ScriptTemplateWithArgs::class,
                listOf(JarFileScriptDependenciesResolver(), MavenScriptDependenciesResolver()))
        ).use { repl ->
            val countdown = CountDownLatch(3)
            val errors = ConcurrentLinkedQueue<Exception>()
            val results = ConcurrentHashMap<String, Any?>()

            fun runScriptThread(name: String, codeLines: List<String>) {
                Thread({
                    try {
                        repl.compileAndEval("""println("Thread $name is running...")""")
                        val evalResults = codeLines.map { repl.compileAndEval(it) }
                        results.put(name, evalResults.last().resultValue)
                    } catch (ex: Exception) {
                        errors.add(ex)
                    } finally {
                        countdown.countDown()
                    }
                }, name).start()
            }

            runScriptThread("junit 1", listOf(
                    """
                        @file:DependsOnMaven("junit:junit:4.12")
                        org.junit.Assert.assertTrue(true)
                    """,
                    """org.junit.Assert.assertEquals("123", "123")"""
            ))
            runScriptThread("junit 2", listOf(
                    """@file:DependsOnMaven("junit:junit:4.12")""",
                    """org.junit.Assert.assertTrue(true)""",
                    """org.junit.Assert.assertEquals("123", "123")"""
            ))
            val now = Instant.now().toEpochMilli()
            runScriptThread("junit 3", listOf(
                    """
                        @file:DependsOnMaven("junit:junit:4.12")
                        org.junit.Assert.assertTrue(true)
                        org.junit.Assert.assertEquals("123", "123")
                    """
            ))

            countdown.await()
            if (errors.isNotEmpty()) {
                errors.forEach { println("ERROR: $it") }
                fail("Test failed due to compiler errors during scripting")
            }
        }
    }

}
