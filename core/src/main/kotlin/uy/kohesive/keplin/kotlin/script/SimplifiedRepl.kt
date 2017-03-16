package uy.kohesive.keplin.kotlin.script

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.common.repl.*
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.config.jvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.repl.GenericRepl
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
                addJvmClasspathRoots(PathUtil.getJdkClassesRoots())
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

    private val engine: GenericRepl by lazy {
        object : GenericRepl(disposable = disposable,
                scriptDefinition = scriptDefinition,
                compilerConfiguration = compilerConfiguration,
                messageCollector = PrintingMessageCollector(System.out, MessageRenderer.WITHOUT_PATHS, false),
                baseClassloader = baseClassloader,
                fallbackScriptArgs = fallbackArgs,
                repeatingMode = repeatingMode,
                stateLock = stateLock) {}
    }


    fun nextCodeLine(code: String) = engine.nextCodeLine(code)

    fun resetToLine(lineNumber: Int): List<ReplCodeLine> = engine.resetToLine(lineNumber)

    fun resetToLine(line: ReplCodeLine): List<ReplCodeLine> = resetToLine(line.no)

    val compiledHistory: List<ReplCodeLine> get() = engine.compiledHistory
    val evaluatedHistory: List<ReplCodeLine> get() = engine.evaluatedHistory

    val currentEvalClassPath: List<File> get() = stateLock.read { engine.currentClasspath }

    fun check(codeLine: ReplCodeLine): CheckResult {
        val result = engine.check(codeLine)
        return when (result) {
            is ReplCheckResult.Error -> throw ReplCompilerException(result)
            is ReplCheckResult.Ok -> CheckResult(codeLine, true)
            is ReplCheckResult.Incomplete -> CheckResult(codeLine, false)
            else -> throw IllegalStateException("Unknown check result type ${result}")
        }
    }


    fun compileAndEval(codeLine: ReplCodeLine,
                       overrideScriptArgs: ScriptArgsWithTypes? = null,
                       verifyHistory: List<ReplCodeLine>? = null,
                       invokeWrapper: InvokeWrapper? = null): EvalResult {
        return engine.compileAndEval(codeLine, overrideScriptArgs ?: fallbackArgs, verifyHistory, invokeWrapper).toResult(codeLine)
    }

    fun compileAndEval(code: String,
                       overrideScriptArgs: ScriptArgsWithTypes? = null,
                       verifyHistory: List<ReplCodeLine>? = null,
                       invokeWrapper: InvokeWrapper? = null): EvalResult {
        return compileAndEval(nextCodeLine(code), overrideScriptArgs ?: fallbackArgs, verifyHistory, invokeWrapper)
    }

    @Deprecated("Unsafe to use individual compile/eval methods which may leave history state inconsistent across threads.", ReplaceWith("compileAndEval(codeLine)"))
    fun compile(codeLine: ReplCodeLine, verifyHistory: List<ReplCodeLine>? = null): CompileResult {
        return engine.compile(codeLine, verifyHistory).toResult()
    }

    @Deprecated("Unsafe to use individual compile/eval methods which may leave history state inconsistent across threads.", ReplaceWith("compileAndEval(codeLine)"))
    fun eval(compileResult: CompileResult,
             overrideScriptArgs: ScriptArgsWithTypes? = null,
             invokeWrapper: InvokeWrapper? = null): EvalResult {
        return engine.eval(compileResult.compilerData, overrideScriptArgs ?: fallbackArgs, invokeWrapper).toResult(compileResult.codeLine)
    }

    fun compileToEvaluable(codeLine: ReplCodeLine, verifyHistory: List<ReplCodeLine>? = null): Evaluable {
        val compiled = engine.compile(codeLine, verifyHistory)
        return if (compiled is ReplCompileResult.CompiledClasses) {
            Evaluable(compiled, engine, fallbackArgs)
        } else {
            compiled.toResult()
            throw IllegalStateException("Unknown compiler result type ${this}")
        }
    }

    val lastEvaluatedScripts: List<EvalHistoryType> get() = engine.lastEvaluatedScripts

    override fun close() {
        disposable.dispose()
    }
}

class Evaluable(val compiledCode: ReplCompileResult.CompiledClasses,
                private val evaluator: ReplEvaluator,
                private val fallbackArgs: ScriptArgsWithTypes? = null) {
    fun eval(scriptArgs: ScriptArgsWithTypes? = null, invokeWrapper: InvokeWrapper? = null): EvalResult {
        return evaluator.eval(compiledCode, scriptArgs ?: fallbackArgs, invokeWrapper).toResult(compiledCode.compiledCodeLine.source)
    }
}

private fun ReplCompileResult.toResult(): CompileResult {
    return when (this) {
        is ReplCompileResult.Error -> throw ReplCompilerException(this)
        is ReplCompileResult.HistoryMismatch -> throw ReplCompilerException(this)
        is ReplCompileResult.Incomplete -> throw ReplCompilerException(this)
        is ReplCompileResult.CompiledClasses -> {
            CompileResult(this.compiledCodeLine.source, this)
        }
        else -> throw IllegalStateException("Unknown compiler result type ${this}")
    }
}

private fun ReplEvalResult.toResult(codeLine: ReplCodeLine): EvalResult {
    return when (this) {
        is ReplEvalResult.Error.CompileTime -> throw ReplCompilerException(this)
        is ReplEvalResult.Error.Runtime -> throw ReplEvalRuntimeException(this)
        is ReplEvalResult.HistoryMismatch -> throw ReplCompilerException(this)
        is ReplEvalResult.Incomplete -> throw ReplCompilerException(this)
        is ReplEvalResult.UnitResult -> {
            EvalResult(codeLine, Unit, this.completedEvalHistory)
        }
        is ReplEvalResult.ValueResult -> {
            EvalResult(codeLine, this.value, this.completedEvalHistory)
        }
        else -> throw IllegalStateException("Unknown eval result type ${this}")
    }
}

class ReplCompilerException(val errorResult: ReplCompileResult.Error) : Exception(errorResult.message) {
    constructor (checkResult: ReplCheckResult.Error) : this(ReplCompileResult.Error(emptyList(), checkResult.message, checkResult.location))
    constructor (incompleteResult: ReplCompileResult.Incomplete) : this(ReplCompileResult.Error(incompleteResult.compiledHistory, "Incomplete Code", CompilerMessageLocation.NO_LOCATION))
    constructor (historyMismatchResult: ReplCompileResult.HistoryMismatch) : this(ReplCompileResult.Error(historyMismatchResult.compiledHistory, "History Mismatch", CompilerMessageLocation.create(null, historyMismatchResult.lineNo, 0, null)))
    constructor (checkResult: ReplEvalResult.Error.CompileTime) : this(ReplCompileResult.Error(checkResult.completedEvalHistory, checkResult.message, checkResult.location))
    constructor (incompleteResult: ReplEvalResult.Incomplete) : this(ReplCompileResult.Error(incompleteResult.completedEvalHistory, "Incomplete Code", CompilerMessageLocation.NO_LOCATION))
    constructor (historyMismatchResult: ReplEvalResult.HistoryMismatch) : this(ReplCompileResult.Error(historyMismatchResult.completedEvalHistory, "History Mismatch", CompilerMessageLocation.create(null, historyMismatchResult.lineNo, 0, null)))
}

class ReplEvalRuntimeException(val errorResult: ReplEvalResult.Error.Runtime) : Exception(errorResult.message, errorResult.cause)

data class CheckResult(val codeLine: ReplCodeLine, val isComplete: Boolean = true)
data class CompileResult(val codeLine: ReplCodeLine,
                         val compilerData: ReplCompileResult.CompiledClasses)

data class EvalResult(val codeLine: ReplCodeLine, val resultValue: Any?, val evalHistory: List<ReplCodeLine>)
