package uy.kohesive.keplin.jsr223.core.scripting

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.repl.ReplCodeLine
import uy.kohesive.keplin.kotlin.core.scripting.ResettableReplCompiler
import uy.kohesive.keplin.kotlin.core.scripting.ResettableReplEvaluator
import java.io.Reader
import javax.script.*

val KOTLIN_SCRIPT_HISTORY_BINDINGS_KEY = "kotlin.script.history"

// TODO consider additional error handling
val Bindings.kotlinScriptHistory: MutableList<ReplCodeLine>
    get() = getOrPut(KOTLIN_SCRIPT_HISTORY_BINDINGS_KEY, { arrayListOf<ReplCodeLine>() }) as MutableList<ReplCodeLine>

abstract class KotlinJsr223JvmScriptEngineBase(protected val myFactory: ScriptEngineFactory) : AbstractScriptEngine(), ScriptEngine, Compilable {

    private var lineCount = 0

    protected abstract val replCompiler: ResettableReplCompiler

    protected abstract val replEvaluator: ResettableReplEvaluator

    override fun eval(script: String, context: ScriptContext): Any? = compile(script, context).eval(context)

    override fun eval(script: Reader, context: ScriptContext): Any? = compile(script.readText(), context).eval()

    override fun compile(script: String): CompiledScript = compile(script, getContext())

    override fun compile(script: Reader): CompiledScript = compile(script.readText(), getContext())

    override fun createBindings(): Bindings = SimpleBindings()

    override fun getFactory(): ScriptEngineFactory = myFactory

    open fun compile(script: String, context: ScriptContext): CompiledScript {
        lineCount += 1

        val codeLine = ReplCodeLine(lineCount, script)
        val history = context.getBindings(ScriptContext.ENGINE_SCOPE).kotlinScriptHistory

        val compileResult = replCompiler.compile(codeLine, history)

        val compiled = when (compileResult) {
            is ResettableReplCompiler.Response.Error -> throw ScriptException("Error${compileResult.locationString()}: ${compileResult.message}")
            is ResettableReplCompiler.Response.Incomplete -> throw ScriptException("error: incomplete code")
            is ResettableReplCompiler.Response.HistoryMismatch -> throw ScriptException("Repl history mismatch at line: ${compileResult.lineNo}")
            is ResettableReplCompiler.Response.CompiledClasses -> compileResult
        }

        return CompiledKotlinScript(this, codeLine, compiled)
    }

    open fun eval(compiledScript: CompiledKotlinScript, context: ScriptContext): Any? {
        val history = context.getBindings(ScriptContext.ENGINE_SCOPE).kotlinScriptHistory

        val evalResult: ResettableReplEvaluator.Response = try {
            TODO()
            ResettableReplEvaluator.Response.Error.CompileTime(history, "TODO", CompilerMessageLocation.NO_LOCATION)
            //replEvaluator.eval(compiledScript.codeLine, history, compiledScript.compiledData.classes, compiledScript.compiledData.hasResult, compiledScript.compiledData.classpathAddendum)
        } catch (e: Exception) {
            throw ScriptException(e)
        }

        val ret = when (evalResult) {
            is ResettableReplEvaluator.Response.ValueResult -> evalResult.value
            is ResettableReplEvaluator.Response.UnitResult -> null
            is ResettableReplEvaluator.Response.Error -> throw ScriptException(evalResult.message)
            is ResettableReplEvaluator.Response.Incomplete -> throw ScriptException("error: incomplete code")
            is ResettableReplEvaluator.Response.HistoryMismatch -> throw ScriptException("Repl history mismatch at line: ${evalResult.lineNo}")
        }
        history.add(compiledScript.codeLine)
        return ret
    }

    class CompiledKotlinScript(val engine: KotlinJsr223JvmScriptEngineBase, val codeLine: ReplCodeLine, val compiledData: ResettableReplCompiler.Response.CompiledClasses) : CompiledScript() {
        override fun eval(context: ScriptContext): Any? = engine.eval(this, context)
        override fun getEngine(): ScriptEngine = engine
    }
}

private fun ResettableReplCompiler.Response.Error.locationString() =
        if (location == CompilerMessageLocation.NO_LOCATION) ""
        else " at ${location.line}:${location.column}"

