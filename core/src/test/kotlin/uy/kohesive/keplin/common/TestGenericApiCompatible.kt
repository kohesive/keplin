package uy.kohesive.keplin.common

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import junit.framework.Assert.assertEquals
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.common.repl.*
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoot
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.config.jvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.repl.GenericRepl
import org.jetbrains.kotlin.cli.jvm.repl.GenericReplCompiler
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.script.KotlinScriptDefinition
import org.jetbrains.kotlin.utils.PathUtil
import org.junit.Test
import java.net.URLClassLoader
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import kotlin.script.templates.standard.ScriptTemplateWithArgs
import kotlin.test.fail
import uy.kohesive.keplin.kotlin.core.scripting.GenericRepl as NewGenericRepl
import uy.kohesive.keplin.kotlin.core.scripting.GenericReplCompiledEvaluator as NewGenericReplCompiledEvaluator
import uy.kohesive.keplin.kotlin.core.scripting.GenericReplCompiler as NewGenericReplCompiler

class TestGenericApiCompatible {
    fun commonGenericParts(scriptDefinition: KotlinScriptDefinition, replClasses: List<KClass<out Any>>): Triple<MessageCollector, CompilerConfiguration, URLClassLoader> {
        val messageCollector = PrintingMessageCollector(System.err, MessageRenderer.PLAIN_FULL_PATHS, false)
        val configuration = makeTestConfiguration(scriptDefinition, replClasses)
        val baseClassloader = URLClassLoader(configuration.jvmClasspathRoots.map { it.toURI().toURL() }
                .toTypedArray(), Thread.currentThread().contextClassLoader)
        return Triple(messageCollector, configuration, baseClassloader)
    }

    fun makeGenericReplEngine(disposable: Disposable, scriptDefinition: KotlinScriptDefinition): GenericRepl {
        val (messageCollector, configuration, baseClassloader) = commonGenericParts(scriptDefinition, listOf(GenericRepl::class))
        return GenericRepl(disposable, scriptDefinition, configuration, messageCollector, baseClassloader,
                scriptArgs = arrayOf(emptyArray<String>()), scriptArgsTypes = arrayOf(Array<String>::class.java))
    }

    fun makeNewGenericReplEngine(disposable: Disposable, scriptDefinition: KotlinScriptDefinition): NewGenericRepl {
        val (messageCollector, configuration, baseClassloader) = commonGenericParts(scriptDefinition, listOf(NewGenericRepl::class))
        return NewGenericRepl(disposable, scriptDefinition, configuration, messageCollector, baseClassloader,
                scriptArgs = arrayOf(emptyArray<String>()), scriptArgsTypes = arrayOf(Array<String>::class.java))
    }

    class QuickScriptDefinition(template: KClass<out Any>) : KotlinScriptDefinition(template)

    data class GenericReplMemory(val lastLine: AtomicInteger = AtomicInteger(0), var history: List<ReplCodeLine> = arrayListOf())

    val genericReplMemory = hashMapOf<ReplEvaluator, GenericReplMemory>()

    fun ReplEvaluator.runCode(code: String): Any? {
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
    fun testOldGenericReplEngine() {
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
    fun testOldGenericReplEngineSeparate() {
        val disposable = Disposer.newDisposable()
        try {
            val repl = makeGenericReplEngine(disposable, QuickScriptDefinition(ScriptTemplateWithArgs::class))
            val line1 = ReplCodeLine(1, "val x = 10")
            val line2 = ReplCodeLine(2, "val y = 33")

            val check1 = repl.check(line1, emptyList()) as ReplCheckResult.Ok
            // eval does both ... val compile1 = repl.compile(line1, check1.updatedHistory) as ReplCompileResult.CompiledClasses
            val eval1 = repl.eval(line1, check1.updatedHistory.dropLast(1)) as ReplEvalResult.UnitResult

            val check2 = repl.check(line2, eval1.updatedHistory) as ReplCheckResult.Ok
            // eval does both ... val compile2 = repl.compile(line2, check2.updatedHistory) as ReplCompileResult.CompiledClasses
            val eval2 = repl.eval(line2, check2.updatedHistory.dropLast(1)) as ReplEvalResult.UnitResult

        } finally {
            disposable.dispose()
        }
    }

    @Test
    fun testOldGenericReplParts() {
        val disposable = Disposer.newDisposable()
        try {
            val scriptDefinition = QuickScriptDefinition(ScriptTemplateWithArgs::class)
            val (messageCollector, configuration, baseClassloader) = commonGenericParts(scriptDefinition, emptyList())

            val compiler = GenericReplCompiler(disposable, scriptDefinition, configuration, messageCollector)
            val evaluator = GenericReplCompiledEvaluator(configuration.jvmClasspathRoots, baseClassloader, scriptArgs = arrayOf(emptyArray<String>()), scriptArgsTypes = arrayOf(Array<String>::class.java))

            val line1 = ReplCodeLine(1, "val x = 10")
            val line2 = ReplCodeLine(2, "val y = 33")

            val check1 = compiler.check(line1, emptyList()) as ReplCheckResult.Ok
            val compile1 = compiler.compile(line1, check1.updatedHistory) as ReplCompileResult.CompiledClasses
            val eval1 = evaluator.eval(line1, compile1.updatedHistory.dropLast(1), compile1.classes, compile1.hasResult, compile1.classpathAddendum) as ReplEvalResult.UnitResult

            val check2 = compiler.check(line2, eval1.updatedHistory) as ReplCheckResult.Ok
            val compile2 = compiler.compile(line2, check2.updatedHistory) as ReplCompileResult.CompiledClasses
            val eval2 = evaluator.eval(line2, compile2.updatedHistory.dropLast(1), compile2.classes, compile2.hasResult, compile2.classpathAddendum) as ReplEvalResult.UnitResult
        } finally {
            disposable.dispose()
        }
    }

    @Test
    fun testNewGenericReplEngine() {
        val disposable = Disposer.newDisposable()
        try {
            val repl = makeNewGenericReplEngine(disposable, QuickScriptDefinition(ScriptTemplateWithArgs::class))
            repl.runCode("val x = 10")
            repl.runCode("val y = 33")
            val result = repl.runCode("x + y")
            assertEquals(43, result)
        } finally {
            disposable.dispose()
        }
    }

    @Test
    fun testNewGenericReplEngineSeparate() {
        val disposable = Disposer.newDisposable()
        try {
            val repl = makeNewGenericReplEngine(disposable, QuickScriptDefinition(ScriptTemplateWithArgs::class))
            val line1 = ReplCodeLine(1, "val x = 10")
            val line2 = ReplCodeLine(2, "val y = 33")

            val check1 = repl.check(line1, emptyList()) as ReplCheckResult.Ok
            val compile1 = repl.compile(line1, check1.updatedHistory) as ReplCompileResult.CompiledClasses
            val eval1 = repl.eval(line1, compile1.updatedHistory.dropLast(1), compile1.classes, compile1.hasResult, compile1.classpathAddendum) as ReplEvalResult.UnitResult

            val check2 = repl.check(line2, eval1.updatedHistory) as ReplCheckResult.Ok
            val compile2 = repl.compile(line2, check2.updatedHistory) as ReplCompileResult.CompiledClasses
            val eval2 = repl.eval(line2, compile2.updatedHistory.dropLast(1), compile2.classes, compile2.hasResult, compile2.classpathAddendum) as ReplEvalResult.UnitResult

            // and then the compile+eval variation
            val line3 = ReplCodeLine(3, "val z = 100")
            val eval3 = repl.eval(line3, eval2.updatedHistory)
        } finally {
            disposable.dispose()
        }
    }

    @Test
    fun testNewGenericReplParts() {
        val disposable = Disposer.newDisposable()
        try {
            val scriptDefinition = QuickScriptDefinition(ScriptTemplateWithArgs::class)
            val (messageCollector, configuration, baseClassloader) = commonGenericParts(scriptDefinition, emptyList())

            val compiler = NewGenericReplCompiler(disposable, scriptDefinition, configuration, messageCollector)
            val evaluator = NewGenericReplCompiledEvaluator(configuration.jvmClasspathRoots, baseClassloader, scriptArgs = arrayOf(emptyArray<String>()), scriptArgsTypes = arrayOf(Array<String>::class.java))

            val line1 = ReplCodeLine(1, "val x = 10")
            val line2 = ReplCodeLine(2, "val y = 33")

            val check1 = compiler.check(line1, emptyList()) as ReplCheckResult.Ok
            val compile1 = compiler.compile(line1, check1.updatedHistory) as ReplCompileResult.CompiledClasses
            val eval1 = evaluator.eval(line1, compile1.updatedHistory.dropLast(1), compile1.classes, compile1.hasResult, compile1.classpathAddendum) as ReplEvalResult.UnitResult

            val check2 = compiler.check(line2, eval1.updatedHistory) as ReplCheckResult.Ok
            val compile2 = compiler.compile(line2, check2.updatedHistory) as ReplCompileResult.CompiledClasses
            val eval2 = evaluator.eval(line2, compile2.updatedHistory.dropLast(1), compile2.classes, compile2.hasResult, compile2.classpathAddendum) as ReplEvalResult.UnitResult
        } finally {
            disposable.dispose()
        }
    }
}

fun makeTestConfiguration(scriptDefinition: KotlinScriptDefinition, extraClasses: List<KClass<out Any>>): CompilerConfiguration {
    return CompilerConfiguration().apply {
        put(CommonConfigurationKeys.MODULE_NAME, "kotlin-script-util-test")
        addJvmClasspathRoots(PathUtil.getJdkClassesRoots())
        (listOf(scriptDefinition.template, Pair::class, JvmName::class) + extraClasses).forEach {
            PathUtil.getResourcePathForClass(it.java).let {
                addJvmClasspathRoot(it)
            }
        }
    }
}