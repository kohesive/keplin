package org.jetbrains.kotlin.script.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.common.repl.ReplCodeLine
import org.jetbrains.kotlin.cli.common.repl.ReplEvalResult
import org.jetbrains.kotlin.cli.jvm.config.jvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.repl.GenericRepl
import org.jetbrains.kotlin.script.KotlinScriptDefinition
import org.jetbrains.kotlin.script.KotlinScriptDefinitionFromAnnotatedTemplate
import org.jetbrains.kotlin.script.util.templates.StandardArgsScriptTemplateWithMavenResolving
import org.junit.Test
import java.net.URLClassLoader
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import kotlin.script.templates.standard.ScriptTemplateWithArgs
import kotlin.test.assertEquals
import kotlin.test.fail

class TestGenericEngine {
    fun makeGenericReplEngine(disposable: Disposable, annotatedTemplateClass: KClass<out Any>): GenericRepl {
        return makeGenericReplEngine(disposable, KotlinScriptDefinitionFromAnnotatedTemplate(annotatedTemplateClass, null, null, emptyMap()))
    }

    fun makeGenericReplEngine(disposable: Disposable, scriptDefinition: KotlinScriptDefinition): GenericRepl {
        val messageCollector = PrintingMessageCollector(System.err, MessageRenderer.PLAIN_FULL_PATHS, false)
        val configuration = makeTestConfiguration(scriptDefinition, listOf(GenericRepl::class))
        val baseClassloader = URLClassLoader(configuration.jvmClasspathRoots.map { it.toURI().toURL() }
                .toTypedArray(), Thread.currentThread().contextClassLoader)

        // println("ENGINE CLASSPATH: ${configuration.jvmClasspathRoots.joinToString("\n")}")
        return GenericRepl(disposable, scriptDefinition, configuration, messageCollector, baseClassloader,
                scriptArgs = arrayOf(emptyArray<String>()), scriptArgsTypes = arrayOf(Array<String>::class.java))
    }

    class QuickScriptDefinition(template: KClass<out Any>) : KotlinScriptDefinition(template)

    data class GenericReplMemory(val lastLine: AtomicInteger = AtomicInteger(0), var history: List<ReplCodeLine> = arrayListOf())

    val genericReplMemory = hashMapOf<GenericRepl, GenericReplMemory>()

    fun GenericRepl.runCode(code: String): Any? {
        val memory = genericReplMemory.getOrPut(this) { GenericReplMemory() }
        val codeLine = ReplCodeLine(memory.lastLine.incrementAndGet(), code)
        val evalResult = eval(codeLine, memory.history)

        return when (evalResult) {
            is ReplEvalResult.Error.CompileTime,
            is ReplEvalResult.HistoryMismatch,
            is ReplEvalResult.Incomplete -> fail("Compilation failed $evalResult")
            is ReplEvalResult.Error.Runtime -> fail("Runtime failed $evalResult")
            is ReplEvalResult.UnitResult,
            is ReplEvalResult.ValueResult -> {
                memory.history = evalResult.updatedHistory
                if (evalResult is ReplEvalResult.ValueResult) evalResult.value
                else Unit
            }
            else -> fail("unknown failure $evalResult")
        }
    }

    @Test
    fun testGenericEngineBasics() {
        val disposable = Disposer.newDisposable()
        try {
            val repl = makeGenericReplEngine(disposable, QuickScriptDefinition(ScriptTemplateWithArgs::class))
            repl.runCode("val x = 10")
            repl.runCode("val y = 33")
            val result = repl.runCode("x + y")
            assertEquals(43, result)
        } finally {
            disposable.dispose()
        }
    }

    @Test
    fun testGenericEngineMavenResolve() {
        val disposable = Disposer.newDisposable()
        try {
            val repl = makeGenericReplEngine(disposable, StandardArgsScriptTemplateWithMavenResolving::class)
            repl.runCode("""
                @file:DependsOn("uy.klutter:klutter-core-jdk8:1.20.1")
                import uy.klutter.core.jdk8.*
                println(utcNow().toIsoString())
            """)
        } finally {
            disposable.dispose()
        }
    }
}