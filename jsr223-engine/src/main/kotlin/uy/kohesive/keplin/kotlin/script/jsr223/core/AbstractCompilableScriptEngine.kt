package uy.kohesive.keplin.kotlin.script.jsr223.core

import uy.kohesive.keplin.kotlin.script.Evaluable
import uy.kohesive.keplin.kotlin.script.ReplCompilerException
import java.io.Reader
import javax.script.*


abstract class AbstractCompilableScriptEngine(factory: ScriptEngineFactory,
                                              defaultImports: List<String> = emptyList())
    : AbstractInvocableReplScriptEngine(factory, defaultImports), Compilable, Invocable {

    override fun compile(script: String): CompiledScript {
        try {
            @Suppress("DEPRECATION")
            val delayed = engine.compileToEvaluable(engine.nextCodeLine(script))
            return CompiledCode(delayed)
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

    inner class CompiledCode(val compiled: Evaluable) : CompiledScript() {
        override fun eval(context: ScriptContext): Any? {
            @Suppress("DEPRECATION")
            return compiled.eval(baseArgsForScriptTemplate(context), makeBestIoTrappingInvoker(context)).resultValue
        }

        override fun getEngine(): ScriptEngine {
            return this@AbstractCompilableScriptEngine
        }
    }

}