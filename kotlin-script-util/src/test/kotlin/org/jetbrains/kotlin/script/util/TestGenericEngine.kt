package org.jetbrains.kotlin.script.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.common.repl.ReplCodeLine
import org.jetbrains.kotlin.cli.common.repl.ReplCompileResult
import org.jetbrains.kotlin.cli.common.repl.ReplEvalResult
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoot
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.repl.GenericRepl
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.script.KotlinScriptDefinition
import org.jetbrains.kotlin.script.KotlinScriptDefinitionFromAnnotatedTemplate
import org.jetbrains.kotlin.script.StandardScriptDefinition
import org.jetbrains.kotlin.script.util.templates.StandardArgsScriptTemplateWithMavenResolving
import org.jetbrains.kotlin.utils.PathUtil
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.script.templates.standard.ScriptTemplateWithArgs
import kotlin.test.assertTrue
import kotlin.test.fail

class TestGenericEngine {

    fun makeEngine(disposable: Disposable, annotatedTemplateClass: KClass<out Any>): GenericRepl {
        return makeEngine(disposable, KotlinScriptDefinitionFromAnnotatedTemplate(annotatedTemplateClass, null, null, null))
    }

    fun makeEngine(disposable: Disposable, scriptDefinition: KotlinScriptDefinition): GenericRepl {
        val messageCollector = PrintingMessageCollector(System.err, MessageRenderer.PLAIN_FULL_PATHS, false)
        val configuration = CompilerConfiguration().apply {
            put(CommonConfigurationKeys.MODULE_NAME, "kotlin-script-util-test")

            addJvmClasspathRoots(PathUtil.getJdkClassesRoots())
            contextClasspath(PathUtil.KOTLIN_JAVA_RUNTIME_JAR, Thread.currentThread().contextClassLoader)?.let {
                addJvmClasspathRoots(it)
            }

            listOf(DependsOn::class, scriptDefinition.template).forEach {
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

        return GenericRepl(disposable, scriptDefinition, configuration, messageCollector, null)
    }

    @Test
    fun testGenericEngineBasics() {
        val disposable = Disposer.newDisposable()
        try {
            val repl = makeEngine(disposable, StandardScriptDefinition)
            val line1 = ReplCodeLine(1, "val x = 10")
            val compileResult = repl.compile(line1, emptyList())
            if (compileResult is ReplCompileResult.CompiledClasses) {
                val evalResult = repl.eval(line1, compileResult.updatedHistory.dropLast(1))
                assertTrue(evalResult is ReplEvalResult.UnitResult, "Eval failed $evalResult")
            } else {
                fail("Error $compileResult")
            }
        } finally {
            disposable.dispose()
        }
    }

    @Test
    fun testGenericEngineMavenResolve() {
        val disposable = Disposer.newDisposable()
        try {
            val repl = makeEngine(disposable, StandardArgsScriptTemplateWithMavenResolving::class)
            val line1 = ReplCodeLine(1, """
                @file:DependsOn("uy.klutter:klutter-core-jdk8:1.20.1")
                import uy.klutter.core.jdk8.*
                println(utcNow().toIsoString())
            """)
            val compileResult = repl.compile(line1, emptyList())
            if (compileResult is ReplCompileResult.CompiledClasses) {
                val evalResult = repl.eval(line1, compileResult.updatedHistory.dropLast(1))
                assertTrue(evalResult is ReplEvalResult.UnitResult, "Eval failed $evalResult")
            } else {
                fail("Error $compileResult")
            }
        } finally {
            disposable.dispose()
        }
    }
}