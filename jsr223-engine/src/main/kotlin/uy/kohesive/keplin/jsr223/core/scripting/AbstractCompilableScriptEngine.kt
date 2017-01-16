package uy.kohesive.keplin.jsr223.core.scripting

import uy.kohesive.keplin.kotlin.core.scripting.CompileResult
import uy.kohesive.keplin.kotlin.core.scripting.ReplCompilerException
import java.io.Reader
import javax.script.*

abstract class AbstractCompilableScriptEngine(factory: ScriptEngineFactory,
                                              defaultImports: List<String> = emptyList())
    : AbstractInvocableReplScriptEngine(factory, defaultImports), Compilable, Invocable {

    override fun compile(script: String): CompiledScript {
        try {
            @Suppress("DEPRECATION")
            val compiled = engine.compile(engine.nextCodeLine(script))
            return CompiledCode(compiled)
        } catch (ex: ReplCompilerException) {
            throw ScriptException(ex.errorResult.message,
                    ex.errorResult.location.path,
                    ex.errorResult.location.line,
                    ex.errorResult.location.column)
        } catch (ex: Exception) {
            throw ScriptException(ex)
        }
    }

    override fun compile(script: Reader): CompiledScript {
        return compile(script.use(Reader::readText))
    }

    inner class CompiledCode(compiled: CompileResult) : CompiledScript() {
        @Suppress("DEPRECATION")
        val delayedEval = this@AbstractCompilableScriptEngine.engine.delayEval(compiled)

        override fun eval(context: ScriptContext): Any? {
            @Suppress("DEPRECATION")
            return delayedEval.eval(baseArgsForScriptTemplate(context),
                    makeBestIoTrappingInvoker(context)).resultValue
        }

        override fun getEngine(): ScriptEngine {
            return this@AbstractCompilableScriptEngine
        }
    }

}