package uy.kohesive.keplin.jsr223

import uy.kohesive.keplin.jsr223.core.scripting.AbstractIoFriendlyScriptEngine
import uy.kohesive.keplin.jsr223.core.scripting.makeBestIoTrappingInvoker
import uy.kohesive.keplin.kotlin.core.scripting.*
import uy.kohesive.keplin.kotlin.util.scripting.findClassJars
import uy.kohesive.keplin.kotlin.util.scripting.findKotlinCompilerJars
import uy.kohesive.keplin.kotlin.util.scripting.resolver.AnnotationTriggeredScriptDefinition
import uy.kohesive.keplin.kotlin.util.scripting.resolver.local.JarFileScriptDependenciesResolver
import uy.kohesive.keplin.util.scripting.resolver.maven.MavenScriptDependenciesResolver
import java.io.Reader
import javax.script.*

class CompilableEngine(val factory: CompilableEngineFactory,
                       defaultImports: List<String> = emptyList())
    : AbstractIoFriendlyScriptEngine(), Compilable {
    private val extraClasspath = findClassJars(ResettableRepl::class) +
            findKotlinCompilerJars(false)

    private val engine: ResettableRepl = ResettableRepl(
            moduleName = "KeplinKotlinJsr223-${System.currentTimeMillis()}",
            additionalClasspath = extraClasspath,
            repeatingMode = ReplRepeatingMode.REPEAT_ANY_PREVIOUS,
            scriptDefinition = AnnotationTriggeredScriptDefinition(
                    definitionName = "KeplinKotlinJsr223",
                    template = KeplinKotlinJsr223ScriptTemplate::class,
                    defaultEmptyArgs = KeplinKotlinJsr223ScriptTemplateEmptyArgs,
                    resolvers = listOf(JarFileScriptDependenciesResolver(), MavenScriptDependenciesResolver()),
                    defaultImports = defaultImports)
    )

    fun fixupArgsForScriptTemplate() {
        engine.defaultScriptArgs = ScriptArgsWithTypes(arrayOf<Any?>(engine, getContext()), KeplinKotlinJsr223ScriptTemplateArgTypes)
    }

    // TODO: capture input/output to the correct place for the script context

    override fun eval(script: String, context: ScriptContext): Any? {
        return engine.compileAndEval(engine.nextCodeLine(script),
                ScriptArgsWithTypes(arrayOf<Any?>(engine, context),
                        KeplinKotlinJsr223ScriptTemplateArgTypes),
                makeBestIoTrappingInvoker(context)).resultValue
    }

    override fun eval(reader: Reader, context: ScriptContext): Any? {
        return eval(reader.use(Reader::readText), context)
    }

    override fun createBindings(): Bindings = SimpleBindings()
    override fun getFactory(): ScriptEngineFactory = factory

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
            throw ScriptException("Unknown error during compilation")
        }
    }

    override fun compile(script: Reader): CompiledScript {
        return compile(script.use(Reader::readText))
    }

    inner class CompiledCode(val compiled: CompileResult) : CompiledScript() {
        override fun eval(context: ScriptContext): Any? {
            @Suppress("DEPRECATION")
            return this@CompilableEngine.engine.eval(compiled,
                    ScriptArgsWithTypes(arrayOf<Any?>(this@CompilableEngine.engine, context),
                            KeplinKotlinJsr223ScriptTemplateArgTypes),
                    makeBestIoTrappingInvoker(context)).resultValue
        }

        override fun getEngine(): ScriptEngine {
            return this@CompilableEngine
        }
    }
}
