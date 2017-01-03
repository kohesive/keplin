package org.jetbrains.kotlin.script.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.common.repl.ReplCheckResult
import org.jetbrains.kotlin.cli.common.repl.ReplCodeLine
import org.jetbrains.kotlin.cli.common.repl.ReplCompileResult
import org.jetbrains.kotlin.cli.common.repl.ReplEvalResult
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoot
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.config.jvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.repl.GenericRepl
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.script.KotlinScriptDefinition
import org.jetbrains.kotlin.script.KotlinScriptDefinitionFromAnnotatedTemplate
import org.jetbrains.kotlin.script.StandardScriptDefinition
import org.jetbrains.kotlin.script.util.templates.StandardArgsScriptTemplateWithMavenResolving
import org.jetbrains.kotlin.utils.PathUtil
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import kotlin.script.templates.standard.ScriptTemplateWithArgs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class TestGenericEngine {

    fun makeEngine(disposable: Disposable, annotatedTemplateClass: KClass<out Any>): GenericRepl {
        return makeEngine(disposable, KotlinScriptDefinitionFromAnnotatedTemplate(annotatedTemplateClass, null, null, emptyMap()))
    }

    fun makeEngine(disposable: Disposable, scriptDefinition: KotlinScriptDefinition): GenericRepl {
        val messageCollector = PrintingMessageCollector(System.err, MessageRenderer.PLAIN_FULL_PATHS, false)
        val configuration = CompilerConfiguration().apply {
            put(CommonConfigurationKeys.MODULE_NAME, "kotlin-script-util-test")

            addJvmClasspathRoots(PathUtil.getJdkClassesRoots())
            contextClasspath(PathUtil.KOTLIN_JAVA_RUNTIME_JAR, Thread.currentThread().contextClassLoader)?.let {
                addJvmClasspathRoots(it)
            }

            listOf(DependsOn::class, scriptDefinition.template, GenericRepl::class).forEach {
                PathUtil.getResourcePathForClass(it.java).let {
                    if (it.exists()) {
                        addJvmClasspathRoot(it)
                    } else {
                        // attempt to workaround some maven quirks
                        manifestClassPath(Thread.currentThread().contextClassLoader)?.let {
                            val files = it.filter { it.name.startsWith("kotlin-") }
                            addJvmClasspathRoots(files)
                        }
                    }
                }
            }
        }

        println("ENGINE CLASSPATH: ${configuration.jvmClasspathRoots.joinToString(",")}")
        return GenericRepl(disposable, scriptDefinition, configuration, messageCollector, null)
    }

    class QuickScriptDefinition(template: KClass<out Any>): KotlinScriptDefinition(template)

    data class ReplMemory(val lastLine: AtomicInteger = AtomicInteger(0), var history: List<ReplCodeLine> = arrayListOf())

    val replMemory = hashMapOf<GenericRepl, ReplMemory>()

    fun GenericRepl.runCode(code: String): Any? {
        val memory = replMemory.getOrPut(this) { ReplMemory() }
        val codeLine = ReplCodeLine(memory.lastLine.incrementAndGet(), code)
        val evalResult = eval(codeLine, memory.history)

        return when (evalResult) {
            is ReplEvalResult.Error.CompileTime,
                    is ReplEvalResult.HistoryMismatch,
                    is ReplEvalResult.Incomplete -> fail ("Compilation failed $evalResult")
            is ReplEvalResult.Error.Runtime -> fail("Runtime failed $evalResult")
            is  ReplEvalResult.UnitResult,
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
            val repl = makeEngine(disposable, QuickScriptDefinition(ScriptTemplateWithArgs::class))
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
            val repl = makeEngine(disposable, StandardArgsScriptTemplateWithMavenResolving::class)
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