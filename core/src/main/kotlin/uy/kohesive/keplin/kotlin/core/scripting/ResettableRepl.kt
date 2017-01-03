package uy.kohesive.keplin.kotlin.core.scripting

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.common.repl.InvokeWrapper
import org.jetbrains.kotlin.cli.common.repl.ReplCodeLine
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.config.jvmClasspathRoots
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.script.KotlinScriptDefinition
import org.jetbrains.kotlin.utils.PathUtil
import uy.kohesive.keplin.kotlin.core.scripting.templates.ScriptTemplateWithArgs
import uy.kohesive.keplin.kotlin.util.scripting.findRequiredScriptingJarFiles
import java.io.Closeable
import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.reflect.KClass

class ResettableRepl(val moduleName: String = "kotlin-script-module-${System.currentTimeMillis()}",
                     val additionalClasspath: List<File> = emptyList(),
                     val scriptDefinition: KotlinScriptDefinition = DefaultScriptDefinition(uy.kohesive.keplin.kotlin.core.scripting.templates.ScriptTemplateWithArgs::class),
                     val stateLock: ReentrantReadWriteLock = ReentrantReadWriteLock()) : Closeable {
    private val disposable = Disposer.newDisposable()

    private val messageCollector = PrintingMessageCollector(System.out, MessageRenderer.WITHOUT_PATHS, false)

    private val compilerConfiguration = CompilerConfiguration().apply {
        addJvmClasspathRoots(PathUtil.getJdkClassesRoots())
        addJvmClasspathRoots(findRequiredScriptingJarFiles(scriptDefinition.template,
                includeScriptEngine = false,
                includeKotlinCompiler = false,
                includeStdLib = true,
                includeRuntime = true))
        addJvmClasspathRoots(additionalClasspath)
        put(CommonConfigurationKeys.MODULE_NAME, moduleName)
        put<MessageCollector>(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
        put(JVMConfigurationKeys.INCLUDE_RUNTIME, true)
    }

    private val compilerClasspath = compilerConfiguration.jvmClasspathRoots
    private val _currentClassPath = compilerClasspath.toMutableList()

    /*
       For reporting to the user the current known classpath as modified by evaluations
     */
    val currentClassPath: List<File> get() = stateLock.read { _currentClassPath }

    private val baseClassloader = URLClassLoader(compilerClasspath.map { it.toURI().toURL() }
            .toTypedArray(), Thread.currentThread().contextClassLoader)

    private val compiler: DefaultResettableReplCompiler by lazy {
        DefaultResettableReplCompiler(disposable, scriptDefinition, compilerConfiguration,
                PrintingMessageCollector(System.out, MessageRenderer.WITHOUT_PATHS, false),
                stateLock = stateLock)
    }

    private val evaluator: DefaultResettableReplEvaluator by lazy {
        DefaultResettableReplEvaluator(compilerConfiguration.jvmClasspathRoots, baseClassloader,
                arrayOf(emptyArray<String>()),
                stateLock = stateLock)
    }

    var codeLineNumber = AtomicInteger(0)

    fun nextCodeLine(code: String) = stateLock.write { ReplCodeLine(codeLineNumber.incrementAndGet(), code) }

    /***
     * Resets back to a give line number, dropping higher lines
     * Returns the removed lines.
     */
    fun resetToLine(lineNumber: Int): List<ReplCodeLine> {
        stateLock.write {
            // TODO: thread safety across compiler and evaluator
            codeLineNumber.set(lineNumber)
            val removedCompiledLines = compiler.resetToLine(lineNumber)
            val removedEvaluatorLines = evaluator.resetToLine(lineNumber)

            removedCompiledLines.zip(removedEvaluatorLines).forEach {
                if (it.first != it.second) {
                    throw IllegalStateException("History mistmatch when resetting lines")
                }
            }

            return removedCompiledLines
        }
    }

    fun resetToLine(line: ReplCodeLine): List<ReplCodeLine> = resetToLine(line.no)

    fun compilationHistory(): List<ReplCodeLine> = stateLock.read { compiler.compilationHistory }
    fun evaluationHistory(): List<ReplCodeLine> = stateLock.read { evaluator.evaluationHistory }

    fun check(codeLine: ReplCodeLine): CheckResult {
        stateLock.write {
            val result = compiler.check(codeLine)
            return when (result) {
                is ResettableReplChecker.Response.Error -> throw CompileErrorException(result)
                is ResettableReplChecker.Response.Ok -> CheckResult(codeLine, true)
                is ResettableReplChecker.Response.Incomplete -> CheckResult(codeLine, false)
                else -> throw IllegalStateException("Unknown check result type ${result}")
            }
        }
    }

    fun compileAndEval(codeLine: ReplCodeLine): EvalResult {
        return stateLock.write { eval(compile(codeLine)) }
    }

    fun compileAndEval(code: String): EvalResult {
        return stateLock.write { eval(compile(nextCodeLine(code))) }
    }

    fun compile(codeLine: ReplCodeLine): CompileResult {
        stateLock.write {
            val result = compiler.compile(codeLine, null)
            return when (result) {
                is ResettableReplCompiler.Response.Error -> throw CompileErrorException(result)
                is ResettableReplCompiler.Response.HistoryMismatch -> throw CompileErrorException(result)
                is ResettableReplCompiler.Response.Incomplete -> throw CompileErrorException(result)
                is ResettableReplCompiler.Response.CompiledClasses -> {
                    CompileResult(codeLine, result)
                }
                else -> throw IllegalStateException("Unknown compiler result type ${result}")
            }
        }
    }

    fun eval(compileResult: CompileResult): EvalResult {
        stateLock.write {
            val result = evaluator.eval(compileResult.compilerData, EvalInvoker())
            return when (result) {
                is ResettableReplEvaluator.Response.Error.CompileTime -> throw CompileErrorException(result)
                is ResettableReplEvaluator.Response.Error.Runtime -> throw EvalRuntimeException(result)
                is ResettableReplEvaluator.Response.HistoryMismatch -> throw CompileErrorException(result)
                is ResettableReplEvaluator.Response.Incomplete -> throw CompileErrorException(result)
                is ResettableReplEvaluator.Response.UnitResult -> {
                    _currentClassPath.addAll(compileResult.compilerData.classpathAddendum)
                    EvalResult(compileResult.codeLine, Unit)
                }
                is ResettableReplEvaluator.Response.ValueResult -> {
                    _currentClassPath.addAll(compileResult.compilerData.classpathAddendum)
                    EvalResult(compileResult.codeLine, result.value)
                }
                else -> throw IllegalStateException("Unknown compiler result type ${result}")
            }
        }
    }

    private class EvalInvoker : InvokeWrapper {
        override fun <T> invoke(body: () -> T): T {
            return body()
        }
    }

    override fun close() {
        disposable.dispose()
    }
}

class CompileErrorException(val errorResult: ResettableReplCompiler.Response.Error) : Exception(errorResult.message) {
    constructor (checkResult: ResettableReplChecker.Response.Error) : this(ResettableReplCompiler.Response.Error(emptyList(), checkResult.message, checkResult.location))
    constructor (incompleteResult: ResettableReplCompiler.Response.Incomplete) : this(ResettableReplCompiler.Response.Error(incompleteResult.compiledHistory, "Incomplete Code", CompilerMessageLocation.NO_LOCATION))
    constructor (historyMismatchResult: ResettableReplCompiler.Response.HistoryMismatch) : this(ResettableReplCompiler.Response.Error(historyMismatchResult.compiledHistory, "History Mismatch", CompilerMessageLocation.create(null, historyMismatchResult.lineNo, 0, null)))
    constructor (checkResult: ResettableReplEvaluator.Response.Error.CompileTime) : this(ResettableReplCompiler.Response.Error(checkResult.completedEvalHistory, checkResult.message, checkResult.location))
    constructor (incompleteResult: ResettableReplEvaluator.Response.Incomplete) : this(ResettableReplCompiler.Response.Error(incompleteResult.completedEvalHistory, "Incomplete Code", CompilerMessageLocation.NO_LOCATION))
    constructor (historyMismatchResult: ResettableReplEvaluator.Response.HistoryMismatch) : this(ResettableReplCompiler.Response.Error(historyMismatchResult.completedEvalHistory, "History Mismatch", CompilerMessageLocation.create(null, historyMismatchResult.lineNo, 0, null)))
}

class EvalRuntimeException(val errorResult: ResettableReplEvaluator.Response.Error.Runtime) : Exception(errorResult.message, errorResult.cause)

data class CheckResult(val codeLine: ReplCodeLine, val isComplete: Boolean = true)
data class CompileResult(val codeLine: ReplCodeLine,
                         val compilerData: ResettableReplCompiler.Response.CompiledClasses)

data class EvalResult(val codeLine: ReplCodeLine, val resultValue: Any?)

class DefaultScriptDefinition(template: KClass<out Any>) : KotlinScriptDefinition(template)