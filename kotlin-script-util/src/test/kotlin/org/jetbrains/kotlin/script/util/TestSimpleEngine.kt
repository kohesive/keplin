package org.jetbrains.kotlin.script.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.common.repl.*
import org.jetbrains.kotlin.cli.jvm.config.jvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.repl.GenericReplCompiler
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.script.KotlinScriptDefinition
import org.jetbrains.kotlin.script.KotlinScriptDefinitionFromAnnotatedTemplate
import org.jetbrains.kotlin.script.util.templates.StandardArgsScriptTemplateWithMavenResolving
import org.junit.Test
import java.net.URLClassLoader
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import kotlin.script.templates.standard.ScriptTemplateWithArgs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestSimpleEngine {
    fun makeSimpleReplEngine(disposable: Disposable, annotatedTemplateClass: KClass<out Any>): SimpleReplEngine {
        return makeSimpleReplEngine(disposable, KotlinScriptDefinitionFromAnnotatedTemplate(annotatedTemplateClass, null, null, emptyMap()))
    }

    fun makeSimpleReplEngine(disposable: Disposable, scriptDefinition: KotlinScriptDefinition): SimpleReplEngine {
        val messageCollector = PrintingMessageCollector(System.err, MessageRenderer.PLAIN_FULL_PATHS, false)
        val configuration = makeTestConfiguration(scriptDefinition, listOf(SimpleReplEngine::class))

        println("ENGINE CLASSPATH: ${configuration.jvmClasspathRoots.joinToString("\n")}")
        return SimpleReplEngine(disposable, scriptDefinition, configuration, messageCollector,
                scriptArgs = arrayOf(emptyArray<String>()), scriptArgsTypes = arrayOf(Array<String>::class.java))
    }

    class SimpleReplEngine(disposable: Disposable, scriptDefinition: KotlinScriptDefinition, configuration: CompilerConfiguration, messageCollector: MessageCollector,
                           val scriptArgs: Array<Any?>? = null,
                           val scriptArgsTypes: Array<Class<*>>? = null) {
        val originalClasspath = configuration.jvmClasspathRoots
        private val baseClassloader = URLClassLoader(originalClasspath.map { it.toURI().toURL() }
                .toTypedArray(), Thread.currentThread().contextClassLoader)

        private val compiler = GenericReplCompiler(disposable, scriptDefinition, configuration, messageCollector)
        private val evaluator = GenericReplCompiledEvaluator(configuration.jvmClasspathRoots, baseClassloader, scriptArgs, scriptArgsTypes)

        val currentClasspath = originalClasspath.toMutableList()
        val history: MutableList<ReplCodeLine> = arrayListOf()
        val lastLineNumber = AtomicInteger(0)

        private fun makeCodeLine(code: String, lineNumber: Int = lastLineNumber.incrementAndGet()): ReplCodeLine {
            return ReplCodeLine(lineNumber, code)
        }

        fun compileAndEval(code: String): Pair<ReplCodeLine, ReplEvalResult> {
            val codeLine = ReplCodeLine(lastLineNumber.incrementAndGet(), code)

            val checkResult = compiler.check(codeLine, history)
            when (checkResult) {
                is ReplCheckResult.Ok -> DO_NOTHING()
                is ReplCheckResult.Incomplete -> return Pair(codeLine, ReplEvalResult.Incomplete(checkResult.updatedHistory))
                is ReplCheckResult.Error -> return Pair(codeLine, ReplEvalResult.Error.CompileTime(checkResult.updatedHistory, checkResult.message, checkResult.location))
            }

            val compileResult = compiler.compile(codeLine, history)
            when (compileResult) {
                is ReplCompileResult.Incomplete -> return Pair(codeLine, ReplEvalResult.Incomplete(compileResult.updatedHistory))
                is ReplCompileResult.HistoryMismatch -> return Pair(codeLine, ReplEvalResult.HistoryMismatch(compileResult.updatedHistory, compileResult.lineNo))
                is ReplCompileResult.Error -> return Pair(codeLine, ReplEvalResult.Error.CompileTime(compileResult.updatedHistory, compileResult.message, compileResult.location))
                is ReplCompileResult.CompiledClasses -> DO_NOTHING()
            }

            val eval = evaluator.eval(codeLine, history, compileResult.classes,
                    compileResult.hasResult,
                    compileResult.classpathAddendum)

            currentClasspath.addAll(compileResult.classpathAddendum)
            history.add(codeLine)
            return Pair(codeLine, eval)
        }
    }

    @Test
    fun testSimpleEngineBasics() {
        val disposable = Disposer.newDisposable()
        try {
            val repl = makeSimpleReplEngine(disposable, TestGenericEngine.QuickScriptDefinition(ScriptTemplateWithArgs::class))
            repl.compileAndEval("val x = 10")
            repl.compileAndEval("val y = 33")
            val (codeLine, result) = repl.compileAndEval("x + y")
            assertTrue(result is ReplEvalResult.ValueResult, "Unexpected eval result: $result")
            assertEquals(43, (result as ReplEvalResult.ValueResult).value)
        } finally {
            disposable.dispose()
        }
    }

    @Test
    fun testSimpleEngineMavenResolve() {
        val disposable = Disposer.newDisposable()
        try {
            val repl = makeSimpleReplEngine(disposable, StandardArgsScriptTemplateWithMavenResolving::class)
            val (codeLine, result) = repl.compileAndEval("""
                @file:DependsOn("uy.klutter:klutter-core-jdk8:1.20.1")
                import uy.klutter.core.jdk8.*
                println(utcNow().toIsoString())
            """)
            assertTrue(result is ReplEvalResult.UnitResult, "Unexpected eval result: $result")
        } finally {
            disposable.dispose()
        }
    }
}