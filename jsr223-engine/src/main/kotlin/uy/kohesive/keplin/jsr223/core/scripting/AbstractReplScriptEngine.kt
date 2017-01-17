package uy.kohesive.keplin.jsr223.core.scripting

import uy.kohesive.keplin.jsr223.KeplinKotlinJsr223ScriptTemplate
import uy.kohesive.keplin.jsr223.KeplinKotlinJsr223ScriptTemplateArgTypes
import uy.kohesive.keplin.jsr223.KeplinKotlinJsr223ScriptTemplateEmptyArgs
import uy.kohesive.keplin.kotlin.core.scripting.ReplCompilerException
import uy.kohesive.keplin.kotlin.core.scripting.ReplEvalRuntimeException
import uy.kohesive.keplin.kotlin.core.scripting.ResettableRepl
import uy.kohesive.keplin.kotlin.core.scripting.ScriptArgsWithTypes
import uy.kohesive.keplin.kotlin.util.scripting.findClassJars
import uy.kohesive.keplin.kotlin.util.scripting.findKotlinCompilerJars
import uy.kohesive.keplin.kotlin.util.scripting.resolver.AnnotationTriggeredScriptDefinition
import uy.kohesive.keplin.kotlin.util.scripting.resolver.AnnotationTriggeredScriptResolver
import uy.kohesive.keplin.kotlin.util.scripting.resolver.local.JarFileScriptDependenciesResolver
import uy.kohesive.keplin.util.scripting.resolver.maven.MavenScriptDependenciesResolver
import java.io.File
import java.io.Reader
import javax.script.*
import kotlin.reflect.KClass


abstract class AbstractReplScriptEngine(val _factory: ScriptEngineFactory,
                                        defaultImports: List<String>)
    : AbstractIoFriendlyScriptEngine() {
    protected val extraClasspath: List<File> = findClassJars(ResettableRepl::class) +
            findKotlinCompilerJars(false)
    open protected val resolvers: List<AnnotationTriggeredScriptResolver> = listOf(JarFileScriptDependenciesResolver(), MavenScriptDependenciesResolver())
    open protected val moduleName: String = "KeplinKotlinJsr223-${System.currentTimeMillis()}"
    open protected val definitionName: String = "KeplinKotlinJsr223"
    open protected val scriptTemplate: KClass<out Any> = KeplinKotlinJsr223ScriptTemplate::class
    open protected val defaultEmptyArgs: ScriptArgsWithTypes = KeplinKotlinJsr223ScriptTemplateEmptyArgs

    protected val scriptDefinition: AnnotationTriggeredScriptDefinition by lazy {
        AnnotationTriggeredScriptDefinition(
                definitionName = definitionName,
                template = scriptTemplate,
                defaultEmptyArgs = defaultEmptyArgs,
                resolvers = resolvers,
                defaultImports = defaultImports)
    }

    abstract protected val engine: ResettableRepl

    /**
     * Must be called after engine is created
     */
    open fun fixupArgsForScriptTemplate() {
        engine.defaultScriptArgs = baseArgsForScriptTemplate(getContext())
    }

    open fun baseArgsForScriptTemplate(context: ScriptContext): ScriptArgsWithTypes? {
        return ScriptArgsWithTypes(arrayOf<Any?>(engine, context), KeplinKotlinJsr223ScriptTemplateArgTypes)
    }

    override fun eval(script: String, context: ScriptContext): Any? {
        try {
            return engine.compileAndEval(engine.nextCodeLine(script),
                    overrideScriptArgs = baseArgsForScriptTemplate(context),
                    wrapper = makeBestIoTrappingInvoker(context)).resultValue
        } catch (ex: ReplCompilerException) {
            throw ScriptException(ex.errorResult.message,
                    ex.errorResult.location.path,
                    ex.errorResult.location.line,
                    ex.errorResult.location.column)
        } catch (ex: ReplEvalRuntimeException) {
            throw ScriptException(ex.errorResult.message)
        } catch (ex: Exception) {
            throw ScriptException(ex)
        }
    }

    override fun eval(reader: Reader, context: ScriptContext): Any? {
        return eval(reader.use(Reader::readText), context)
    }

    override fun createBindings(): Bindings = SimpleBindings()
    override fun getFactory(): ScriptEngineFactory = _factory
}
