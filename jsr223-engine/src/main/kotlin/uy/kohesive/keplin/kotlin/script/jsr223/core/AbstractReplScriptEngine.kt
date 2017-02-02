package uy.kohesive.keplin.kotlin.script.jsr223.core

import org.jetbrains.kotlin.cli.common.repl.ScriptArgsWithTypes
import uy.kohesive.keplin.kotlin.script.ReplCompilerException
import uy.kohesive.keplin.kotlin.script.ReplEvalRuntimeException
import uy.kohesive.keplin.kotlin.script.SimplifiedRepl
import uy.kohesive.keplin.kotlin.script.resolver.AnnotationTriggeredScriptDefinition
import uy.kohesive.keplin.kotlin.script.resolver.JarFileScriptDependenciesResolver
import uy.kohesive.keplin.kotlin.script.resolver.maven.MavenScriptDependenciesResolver
import uy.kohesive.keplin.kotlin.script.util.findClassJars
import uy.kohesive.keplin.kotlin.script.util.findKotlinCompilerJars
import java.io.File
import java.io.Reader
import javax.script.*
import kotlin.reflect.KClass

abstract class AbstractReplScriptEngine(val _factory: ScriptEngineFactory,
                                        defaultImports: List<String>)
    : AbstractIoFriendlyScriptEngine() {
    protected val extraClasspath: List<File> = findClassJars(SimplifiedRepl::class) +
            findKotlinCompilerJars(false)
    open protected val resolvers = listOf(JarFileScriptDependenciesResolver(), MavenScriptDependenciesResolver())
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

    abstract protected val engine: SimplifiedRepl

    /**
     * Must be called after engine is created
     */
    open fun fixupArgsForScriptTemplate() {
        engine.fallbackArgs = baseArgsForScriptTemplate(getContext())
    }

    open fun baseArgsForScriptTemplate(context: ScriptContext): ScriptArgsWithTypes? {
        return ScriptArgsWithTypes(arrayOf<Any?>(engine, context), KeplinKotlinJsr223ScriptTemplateArgTypes)
    }

    override fun eval(script: String, context: ScriptContext): Any? {
        try {
            return engine.compileAndEval(engine.nextCodeLine(script),
                    overrideScriptArgs = baseArgsForScriptTemplate(context),
                    invokeWrapper = makeBestIoTrappingInvoker(context)).resultValue
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
