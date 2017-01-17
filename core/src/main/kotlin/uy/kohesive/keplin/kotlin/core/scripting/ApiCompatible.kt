package uy.kohesive.keplin.kotlin.core.scripting

import com.intellij.openapi.Disposable
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.repl.*
import org.jetbrains.kotlin.cli.jvm.config.jvmClasspathRoots
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.script.KotlinScriptDefinition
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

@Suppress("DEPRECATION")
@Deprecated("GenericReplChecker has been replaced by DefaultResettableReplChecker", replaceWith = ReplaceWith("DefaultResettableReplChecker(disposable, scripDefinition, compilerConfiguration, messageCollector)"))
open class GenericReplChecker(
        disposable: Disposable,
        scriptDefinition: KotlinScriptDefinition,
        compilerConfiguration: CompilerConfiguration,
        messageCollector: MessageCollector,
        stateLock: ReentrantReadWriteLock
) : ReplChecker {
    constructor(disposable: Disposable,
                scriptDefinition: KotlinScriptDefinition,
                compilerConfiguration: CompilerConfiguration,
                messageCollector: MessageCollector)
            : this(disposable, scriptDefinition, compilerConfiguration, messageCollector, ReentrantReadWriteLock())

    private val checker = DefaultResettableReplChecker(disposable, scriptDefinition, compilerConfiguration, messageCollector, stateLock)

    override fun check(codeLine: ReplCodeLine, history: List<ReplCodeLine>): ReplCheckResult {
        val result = checker.check(codeLine)
        return when (result) {
            is ResettableReplChecker.Response.Ok -> ReplCheckResult.Ok(history)
            is ResettableReplChecker.Response.Incomplete -> ReplCheckResult.Incomplete(history)
            is ResettableReplChecker.Response.Error -> ReplCheckResult.Error(history, result.message, result.location)
            else -> throw IllegalStateException("Unknown check result type ${result}")
        }
    }
}

@Suppress("DEPRECATION")
@Deprecated("GenericReplCompiler has been replaced by DefaultResettableReplChecker", replaceWith = ReplaceWith("DefaultResettableReplChecker(disposable, scripDefinition, compilerConfiguration, messageCollector)"))
open class GenericReplCompiler(
        disposable: Disposable,
        scriptDefinition: KotlinScriptDefinition,
        compilerConfiguration: CompilerConfiguration,
        messageCollector: MessageCollector,
        stateLock: ReentrantReadWriteLock
) : ReplCompiler, ReplChecker {
    constructor(disposable: Disposable,
                scriptDefinition: KotlinScriptDefinition,
                compilerConfiguration: CompilerConfiguration,
                messageCollector: MessageCollector)
            : this(disposable, scriptDefinition, compilerConfiguration, messageCollector, ReentrantReadWriteLock())

    private val compiler = DefaultResettableReplCompiler(disposable, scriptDefinition, compilerConfiguration, messageCollector, stateLock)

    override fun check(codeLine: ReplCodeLine, history: List<ReplCodeLine>): ReplCheckResult {
        val result = compiler.check(codeLine)
        return when (result) {
            is ResettableReplChecker.Response.Ok -> ReplCheckResult.Ok(history)
            is ResettableReplChecker.Response.Incomplete -> ReplCheckResult.Incomplete(history)
            is ResettableReplChecker.Response.Error -> ReplCheckResult.Error(history, result.message, result.location)
            else -> throw IllegalStateException("Unknown check result type ${result}")
        }
    }

    override fun compile(codeLine: ReplCodeLine, history: List<ReplCodeLine>): ReplCompileResult {
        val result = compiler.compile(codeLine, history)
        return when (result) {
            is ResettableReplCompiler.Response.CompiledClasses -> ReplCompileResult.CompiledClasses(result.compiledHistory, result.classes, result.hasResult, result.classpathAddendum)
            is ResettableReplCompiler.Response.Incomplete -> ReplCompileResult.Incomplete(result.compiledHistory)
            is ResettableReplCompiler.Response.Error -> ReplCompileResult.Error(result.compiledHistory, result.message, result.location)
            is ResettableReplCompiler.Response.HistoryMismatch -> ReplCompileResult.HistoryMismatch(result.compiledHistory, result.lineNo)
            else -> throw IllegalStateException("Unknown compiler result type ${result}")
        }
    }
}

@Suppress("DEPRECATION")
@Deprecated("GenericReplCompiledEvaluator has been replaced by DefaultResettableReplEvaluator", replaceWith = ReplaceWith("DefaultResettableReplEvaluator(baseClasspath, baseClassloader, scriptArgs, scriptArgsTypes)"))
open class GenericReplCompiledEvaluator(baseClasspath: Iterable<File>,
                                        baseClassloader: ClassLoader?,
                                        val scriptArgs: Array<Any?>? = null,
                                        val scriptArgsTypes: Array<Class<*>>? = null,
                                        val stateLock: ReentrantReadWriteLock
) : ReplCompiledEvaluator {
    constructor(baseClasspath: Iterable<File>,
                baseClassloader: ClassLoader?,
                scriptArgs: Array<Any?>? = null,
                scriptArgsTypes: Array<Class<*>>? = null) : this(baseClasspath, baseClassloader, scriptArgs, scriptArgsTypes, ReentrantReadWriteLock())

    private val evaluator = DefaultResettableReplEvaluator(baseClasspath, baseClassloader, ReplRepeatingMode.NONE, stateLock)

    override val lastEvaluatedScript: ClassWithInstance? = evaluator.lastEvaluatedScripts.lastOrNull()?.let { (_, lastScript) ->
        lastScript.instance?.let { instance -> ClassWithInstance(lastScript.klass, instance) }
    }

    override fun eval(codeLine: ReplCodeLine, history: List<ReplCodeLine>, compiledClasses: List<CompiledClassData>, hasResult: Boolean, classpathAddendum: List<File>, invokeWrapper: InvokeWrapper?): ReplEvalResult {
        val className = makeSriptBaseName(codeLine, 1L)
        val compiled = ResettableReplCompiler.Response.CompiledClasses(history,
                CompiledReplCodeLine(className, codeLine), className,
                compiledClasses, hasResult, classpathAddendum)

        val result = evaluator.eval(compiled, invokeWrapper, history,
                scriptArgs?.let { args -> ScriptArgsWithTypes(args, scriptArgsTypes?.map { it.kotlin }?.toTypedArray() ?: arrayOf()) })
        return when (result) {

            is ResettableReplEvaluator.Response.Incomplete -> ReplEvalResult.Incomplete(result.completedEvalHistory)
            is ResettableReplEvaluator.Response.Error.CompileTime -> ReplEvalResult.Error.CompileTime(result.completedEvalHistory, result.message, result.location)
            is ResettableReplEvaluator.Response.Error.Runtime -> ReplEvalResult.Error.Runtime(result.completedEvalHistory, result.message, result.cause)
            is ResettableReplEvaluator.Response.HistoryMismatch -> ReplEvalResult.HistoryMismatch(result.completedEvalHistory, result.lineNo)
            is ResettableReplEvaluator.Response.ValueResult -> ReplEvalResult.ValueResult(result.completedEvalHistory, result.value)
            is ResettableReplEvaluator.Response.UnitResult -> ReplEvalResult.UnitResult(result.completedEvalHistory)
            else -> throw IllegalStateException("Unknown eval result type ${result}")
        }
    }
}


@Suppress("DEPRECATION")
@Deprecated("GenericRepl has been replaced by ResettableRepl", replaceWith = ReplaceWith("ResettableRepl(disposable, scripDefinition, compilerConfiguration, messageCollector, baseClassloader, scriptArgs, scriptArgsTypes)"))
open class GenericRepl(
        disposable: Disposable,
        scriptDefinition: KotlinScriptDefinition,
        compilerConfiguration: CompilerConfiguration,
        messageCollector: MessageCollector,
        baseClassloader: ClassLoader?,
        scriptArgs: Array<Any?>? = null,
        scriptArgsTypes: Array<Class<*>>? = null,
        val stateLock: ReentrantReadWriteLock
) : ReplCompiler, ReplEvaluator, ReplCompiledEvaluator {

    constructor(disposable: Disposable,
                scriptDefinition: KotlinScriptDefinition,
                compilerConfiguration: CompilerConfiguration,
                messageCollector: MessageCollector,
                baseClassloader: ClassLoader?,
                scriptArgs: Array<Any?>? = null,
                scriptArgsTypes: Array<Class<*>>? = null)
            : this(disposable, scriptDefinition, compilerConfiguration, messageCollector, baseClassloader, scriptArgs, scriptArgsTypes, ReentrantReadWriteLock())

    private val compiler = DefaultResettableReplCompiler(disposable, scriptDefinition, compilerConfiguration, messageCollector, stateLock)
    private val evaluator = DefaultResettableReplEvaluator(compilerConfiguration.jvmClasspathRoots, baseClassloader, ReplRepeatingMode.NONE, stateLock)
    private val defaultScriptArgs = scriptArgs?.let { args -> ScriptArgsWithTypes(args, scriptArgsTypes?.map { it.kotlin }?.toTypedArray() ?: arrayOf()) }

    override fun check(codeLine: ReplCodeLine, history: List<ReplCodeLine>): ReplCheckResult {
        val result = compiler.check(codeLine)
        return when (result) {
            is ResettableReplChecker.Response.Ok -> ReplCheckResult.Ok(history)
            is ResettableReplChecker.Response.Incomplete -> ReplCheckResult.Incomplete(history)
            is ResettableReplChecker.Response.Error -> ReplCheckResult.Error(history, result.message, result.location)
            else -> throw IllegalStateException("Unknown check result type ${result}")
        }
    }

    override fun compile(codeLine: ReplCodeLine, history: List<ReplCodeLine>): ReplCompileResult {
        val result = compiler.compile(codeLine, history)
        return when (result) {
            is ResettableReplCompiler.Response.CompiledClasses -> ReplCompileResult.CompiledClasses(result.compiledHistory, result.classes, result.hasResult, result.classpathAddendum)
            is ResettableReplCompiler.Response.Incomplete -> ReplCompileResult.Incomplete(result.compiledHistory)
            is ResettableReplCompiler.Response.Error -> ReplCompileResult.Error(result.compiledHistory, result.message, result.location)
            is ResettableReplCompiler.Response.HistoryMismatch -> ReplCompileResult.HistoryMismatch(result.compiledHistory, result.lineNo)
            else -> throw IllegalStateException("Unknown compiler result type ${result}")
        }
    }

    override val lastEvaluatedScript: ClassWithInstance? = evaluator.lastEvaluatedScripts.lastOrNull()?.let { (_, lastScript) ->
        lastScript.instance?.let { instance -> ClassWithInstance(lastScript.klass, instance) }
    }

    override fun eval(codeLine: ReplCodeLine, history: List<ReplCodeLine>, compiledClasses: List<CompiledClassData>, hasResult: Boolean, classpathAddendum: List<File>, invokeWrapper: InvokeWrapper?): ReplEvalResult {
        val className = makeSriptBaseName(codeLine, 1L)
        val compiled = ResettableReplCompiler.Response.CompiledClasses(history,
                CompiledReplCodeLine(className, codeLine), className,
                compiledClasses, hasResult, classpathAddendum)

        val result = evaluator.eval(compiled, invokeWrapper, history, defaultScriptArgs)
        return when (result) {
            is ResettableReplEvaluator.Response.Incomplete -> ReplEvalResult.Incomplete(result.completedEvalHistory)
            is ResettableReplEvaluator.Response.Error.CompileTime -> ReplEvalResult.Error.CompileTime(result.completedEvalHistory, result.message, result.location)
            is ResettableReplEvaluator.Response.Error.Runtime -> ReplEvalResult.Error.Runtime(result.completedEvalHistory, result.message, result.cause)
            is ResettableReplEvaluator.Response.HistoryMismatch -> ReplEvalResult.HistoryMismatch(result.completedEvalHistory, result.lineNo)
            is ResettableReplEvaluator.Response.ValueResult -> ReplEvalResult.ValueResult(result.completedEvalHistory, result.value)
            is ResettableReplEvaluator.Response.UnitResult -> ReplEvalResult.UnitResult(result.completedEvalHistory)
            else -> throw IllegalStateException("Unknown eval result type ${result}")
        }
    }

    /**
     * In GenericRepl this is a compile+eval step
     */
    override fun eval(codeLine: ReplCodeLine, history: List<ReplCodeLine>, invokeWrapper: InvokeWrapper?): ReplEvalResult {
        stateLock.write {
            val compiled = compiler.compile(codeLine, history)
            if (compiled is ResettableReplCompiler.Response.CompiledClasses) {
                val result = evaluator.eval(compiled, invokeWrapper, scriptArgs = defaultScriptArgs)
                return when (result) {
                    is ResettableReplEvaluator.Response.Incomplete -> ReplEvalResult.Incomplete(result.completedEvalHistory)
                    is ResettableReplEvaluator.Response.Error.CompileTime -> ReplEvalResult.Error.CompileTime(result.completedEvalHistory, result.message, result.location)
                    is ResettableReplEvaluator.Response.Error.Runtime -> ReplEvalResult.Error.Runtime(result.completedEvalHistory, result.message, result.cause)
                    is ResettableReplEvaluator.Response.HistoryMismatch -> ReplEvalResult.HistoryMismatch(result.completedEvalHistory, result.lineNo)
                    is ResettableReplEvaluator.Response.ValueResult -> ReplEvalResult.ValueResult(result.completedEvalHistory, result.value)
                    is ResettableReplEvaluator.Response.UnitResult -> ReplEvalResult.UnitResult(result.completedEvalHistory)
                    else -> throw IllegalStateException("Unknown eval result type ${result}")
                }
            } else {
                return when (compiled) {
                    is ResettableReplCompiler.Response.Incomplete -> ReplEvalResult.Incomplete(compiled.compiledHistory)
                    is ResettableReplCompiler.Response.Error -> ReplEvalResult.Error.CompileTime(compiled.compiledHistory, compiled.message, compiled.location)
                    is ResettableReplCompiler.Response.HistoryMismatch -> ReplEvalResult.HistoryMismatch(compiled.compiledHistory, compiled.lineNo)
                    else -> throw IllegalStateException("Unknown compiler result type ${compiled}")
                }
            }
        }
    }
}