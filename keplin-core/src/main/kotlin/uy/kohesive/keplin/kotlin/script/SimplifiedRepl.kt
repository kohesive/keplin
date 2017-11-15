package uy.kohesive.keplin.kotlin.script

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.common.repl.*
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.config.jvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.repl.GenericReplCompiler
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.script.KotlinScriptDefinition
import org.jetbrains.kotlin.utils.PathUtil
import uy.kohesive.keplin.util.ClassPathUtils
import java.io.Closeable
import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.reflect.KClass
import kotlin.script.templates.standard.ScriptTemplateWithArgs

private val EMPTY_SCRIPT_ARGS: Array<out Any?> = arrayOf(emptyArray<String>())
private val EMPTY_SCRIPT_ARGS_TYPES: Array<out KClass<out Any>> = arrayOf(Array<String>::class)

open class SimplifiedRepl protected constructor(protected val disposable: Disposable,
                                                protected val scriptDefinition: KotlinScriptDefinition,
                                                protected val compilerConfiguration: CompilerConfiguration,
                                                protected val repeatingMode: ReplRepeatingMode = ReplRepeatingMode.NONE,
                                                protected val sharedHostClassLoader: ClassLoader? = null,
                                                protected val emptyArgsProvider: ScriptTemplateEmptyArgsProvider,
                                                protected val useDaemonCompiler: Boolean = false,
                                                protected val stateLock: ReentrantReadWriteLock = ReentrantReadWriteLock()) : Closeable {

    constructor(disposable: Disposable = Disposer.newDisposable(),
                moduleName: String = "kotlin-script-module-${System.currentTimeMillis()}",
                additionalClasspath: List<File> = emptyList(),
                scriptDefinition: KotlinScriptDefinitionEx = KotlinScriptDefinitionEx(ScriptTemplateWithArgs::class, ScriptArgsWithTypes(EMPTY_SCRIPT_ARGS, EMPTY_SCRIPT_ARGS_TYPES)),
                messageCollector: MessageCollector = PrintingMessageCollector(System.out, MessageRenderer.WITHOUT_PATHS, false),
                repeatingMode: ReplRepeatingMode = ReplRepeatingMode.NONE,
                sharedHostClassLoader: ClassLoader? = null) : this(disposable,
            compilerConfiguration = CompilerConfiguration().apply {
                addJvmClasspathRoots(PathUtil.getJdkClassesRootsFromCurrentJre())
                addJvmClasspathRoots(ClassPathUtils.findRequiredScriptingJarFiles(scriptDefinition.template,
                        includeScriptEngine = false,
                        includeKotlinCompiler = false,
                        includeStdLib = true,
                        includeRuntime = true))
                addJvmClasspathRoots(additionalClasspath)
                put(CommonConfigurationKeys.MODULE_NAME, moduleName)
                put<MessageCollector>(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
            },
            repeatingMode = repeatingMode,
            sharedHostClassLoader = sharedHostClassLoader,
            scriptDefinition = scriptDefinition,
            emptyArgsProvider = scriptDefinition)

    private val baseClassloader = URLClassLoader(compilerConfiguration.jvmClasspathRoots.map { it.toURI().toURL() }
            .toTypedArray(), sharedHostClassLoader)

    var fallbackArgs: ScriptArgsWithTypes? = emptyArgsProvider.defaultEmptyArgs
        get() = stateLock.read { field }
        set(value) = stateLock.write { field = value }

    private val compiler: ReplCompiler by lazy {
        GenericReplCompiler(disposable, scriptDefinition, compilerConfiguration,
                PrintingMessageCollector(System.out, MessageRenderer.WITHOUT_PATHS, false))
    }

    private val evaluator: ReplFullEvaluator by lazy {
        GenericReplCompilingEvaluator(compiler, compilerConfiguration.jvmClasspathRoots, baseClassloader, fallbackArgs, repeatingMode)
    }

    private val state = evaluator.createState(stateLock)
    private val nextLine: AtomicInteger = AtomicInteger(0)

    fun nextCodeLine(code: String) = stateLock.write { ReplCodeLine(nextLine.incrementAndGet(), state.currentGeneration, code) }

    fun resetToLine(lineNumber: Int): List<ILineId> {
        stateLock.write {
            val removedList = state.history.last { it.id.no == lineNumber }.let { state.history.resetTo(it.id) }.toList()
            nextLine.set(state.getNextLineNo() - 1)
            return removedList
        }
    }

    fun resetToLine(line: ReplCodeLine): List<ILineId> = resetToLine(line.no)

    val currentEvalClassPath: List<File> get() = stateLock.read { state.asState(GenericReplEvaluatorState::class.java).currentClasspath }
    val lastEvaluatedScripts: List<ReplHistoryRecord<EvalClassWithInstanceAndLoader>> get() = stateLock.read { state.asState(GenericReplEvaluatorState::class.java).history.toList() }

    fun check(codeLine: ReplCodeLine): CheckResult {
        val result = compiler.check(state, codeLine)
        return when (result) {
            is ReplCheckResult.Error -> throw ReplCompilerException(result)
            is ReplCheckResult.Ok -> CheckResult(LineId(codeLine), true)
            is ReplCheckResult.Incomplete -> CheckResult(LineId(codeLine), false)
            else -> throw IllegalStateException("Unknown check result type ${result}")
        }
    }

    fun compileAndEval(codeLine: ReplCodeLine,
                       overrideScriptArgs: ScriptArgsWithTypes? = null,
                       invokeWrapper: InvokeWrapper? = null): EvalResult {
        return evaluator.compileAndEval(state, codeLine, overrideScriptArgs ?: fallbackArgs, invokeWrapper).toResult(LineId(codeLine))
    }

    fun compileAndEval(code: String,
                       overrideScriptArgs: ScriptArgsWithTypes? = null,
                       verifyHistory: List<ReplCodeLine>? = null,
                       invokeWrapper: InvokeWrapper? = null): EvalResult {
        return compileAndEval(nextCodeLine(code), overrideScriptArgs ?: fallbackArgs, invokeWrapper)
    }

    @Deprecated("Unsafe to use individual compile/eval methods which may leave history state inconsistent across threads.", ReplaceWith("compileAndEval(codeLine)"))
    fun compile(codeLine: ReplCodeLine): CompileResult {
        return compiler.compile(state, codeLine).toResult()
    }

    @Deprecated("Unsafe to use individual compile/eval methods which may leave history state inconsistent across threads.", ReplaceWith("compileAndEval(codeLine)"))
    fun eval(compileResult: CompileResult,
             overrideScriptArgs: ScriptArgsWithTypes? = null,
             invokeWrapper: InvokeWrapper? = null): EvalResult {
        return try {
            evaluator.eval(state, compileResult.compilerData, overrideScriptArgs ?: fallbackArgs, invokeWrapper).toResult(compileResult.codeLine)
        } catch (ex: Throwable) {
            throw ReplException("Repl eval failed due to ${ex.message}", ex)
        }
    }

    fun compileToEvaluable(codeLine: ReplCodeLine): Evaluable {
        val compiled = compiler.compile(state, codeLine)
        return if (compiled is ReplCompileResult.CompiledClasses) {
            Evaluable(state, compiled, evaluator, fallbackArgs)
        } else {
            compiled.toResult()
            throw IllegalStateException("Unknown compiler result type ${this}")
        }
    }

    override fun close() {
        disposable.dispose()
    }
}

class Evaluable(val state: IReplStageState<*>,
                val compiledCode: ReplCompileResult.CompiledClasses,
                private val evaluator: ReplEvaluator,
                private val fallbackArgs: ScriptArgsWithTypes? = null) {
    fun eval(scriptArgs: ScriptArgsWithTypes? = null, invokeWrapper: InvokeWrapper? = null): EvalResult {
        return evaluator.eval(state, compiledCode, scriptArgs ?: fallbackArgs, invokeWrapper).toResult(compiledCode.lineId)
    }
}

private fun ReplCompileResult.toResult(): CompileResult {
    return when (this) {
        is ReplCompileResult.Error -> throw ReplCompilerException(this)
        is ReplCompileResult.Incomplete -> throw ReplCompilerException(this)
        is ReplCompileResult.CompiledClasses -> {
            CompileResult(this.lineId, this)
        }
        else -> throw IllegalStateException("Unknown compiler result type ${this}")
    }
}

private fun ReplEvalResult.toResult(codeLine: LineId): EvalResult {
    return when (this) {
        is ReplEvalResult.Error.CompileTime -> throw ReplCompilerException(this)
        is ReplEvalResult.Error.Runtime -> throw ReplEvalRuntimeException(this)
        is ReplEvalResult.Incomplete -> throw ReplCompilerException(this)
        is ReplEvalResult.HistoryMismatch -> throw ReplCompilerException(this)
        is ReplEvalResult.UnitResult -> {
            EvalResult(codeLine, Unit)
        }
        is ReplEvalResult.ValueResult -> {
            EvalResult(codeLine, this.value)
        }
        else -> throw IllegalStateException("Unknown eval result type ${this}")
    }
}

class ReplCompilerException(val errorResult: ReplCompileResult.Error) : ReplException(errorResult.message) {
    constructor (checkResult: ReplCheckResult.Error) : this(ReplCompileResult.Error(checkResult.message, checkResult.location))
    constructor (incompleteResult: ReplCompileResult.Incomplete) : this(ReplCompileResult.Error("Incomplete Code", null))
    constructor (checkResult: ReplEvalResult.Error.CompileTime) : this(ReplCompileResult.Error(checkResult.message, checkResult.location))
    constructor (incompleteResult: ReplEvalResult.Incomplete) : this(ReplCompileResult.Error("Incomplete Code", null))
    constructor (historyMismatchResult: ReplEvalResult.HistoryMismatch) : this(ReplCompileResult.Error("History Mismatch", CompilerMessageLocation.create(null, historyMismatchResult.lineNo, 0, null)))
}

class ReplEvalRuntimeException(val errorResult: ReplEvalResult.Error.Runtime) : ReplException(errorResult.message, errorResult.cause)
open class ReplException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class CheckResult(val codeLine: LineId, val isComplete: Boolean = true)
data class CompileResult(val codeLine: LineId,
                         val compilerData: ReplCompileResult.CompiledClasses)

data class EvalResult(val codeLine: LineId, val resultValue: Any?)
