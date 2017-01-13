package uy.kohesive.keplin.jsr223

import uy.kohesive.keplin.jsr223.core.scripting.AbstractReplScriptEngine
import uy.kohesive.keplin.jsr223.core.scripting.makeBestIoTrappingInvoker
import uy.kohesive.keplin.kotlin.core.scripting.*
import java.io.Reader
import javax.script.*

class CompilableReplEngine(factory: ScriptEngineFactory,
                           defaultImports: List<String> = emptyList())
    : AbstractReplScriptEngine(factory, defaultImports), Compilable {

    override val engine: ResettableRepl = ResettableRepl(
            moduleName = moduleName,
            additionalClasspath = extraClasspath,
            repeatingMode = ReplRepeatingMode.REPEAT_ANY_PREVIOUS,
            scriptDefinition = scriptDefinition
    )

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

    inner class CompiledCode(val compiled: CompileResult) : CompiledScript() {
        override fun eval(context: ScriptContext): Any? {
            @Suppress("DEPRECATION")
            return this@CompilableReplEngine.engine.eval(compiled,
                    ScriptArgsWithTypes(arrayOf<Any?>(this@CompilableReplEngine.engine, context),
                            KeplinKotlinJsr223ScriptTemplateArgTypes),
                    makeBestIoTrappingInvoker(context)).resultValue
        }

        override fun getEngine(): ScriptEngine {
            return this@CompilableReplEngine
        }
    }
}
