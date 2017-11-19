package uy.kohesive.keplin.kotlin.script.resolver

import org.jetbrains.kotlin.cli.common.repl.ScriptArgsWithTypes
import org.jetbrains.kotlin.com.intellij.openapi.diagnostic.Logger
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.NameUtils
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.script.KotlinScriptDefinitionFromAnnotatedTemplate
import uy.kohesive.keplin.util.ApiChangeDependencyResolverWrapper
import uy.kohesive.keplin.util.EmptyDependencies
import uy.kohesive.keplin.util.EmptyDependenciesWithDefaultImports
import uy.kohesive.keplin.util.KotlinScriptDefinitionEx
import java.io.File
import java.util.concurrent.Future
import kotlin.reflect.KClass
import kotlin.script.dependencies.*
import kotlin.script.experimental.dependencies.DependenciesResolver
import kotlin.script.templates.DEFAULT_SCRIPT_FILE_PATTERN

open class AnnotationTriggeredScriptDefinition(definitionName: String,
                                               template: KClass<out Any>,
                                               defaultEmptyArgs: ScriptArgsWithTypes? = null,
                                               val resolvers: List<AnnotationTriggeredScriptResolver> = emptyList(),
                                               defaultImports: List<String> = emptyList(),
                                               val scriptFilePattern: Regex = DEFAULT_SCRIPT_FILE_PATTERN.toRegex()) :
        KotlinScriptDefinitionEx(template, defaultEmptyArgs, defaultImports) {
    override val name = definitionName
    override val acceptedAnnotations = resolvers.map { it.acceptedAnnotations }.flatten()

    protected val resolutionManager: AnnotationTriggeredResolutionManager by lazy {
        AnnotationTriggeredResolutionManager(resolvers)
    }

    override fun getScriptName(script: KtScript): Name = NameUtils.getScriptNameForFile(script.containingKtFile.name)
    override fun isScript(fileName: String): Boolean = scriptFilePattern.matches(fileName)

    class MergeDependencies(val current: KotlinScriptExternalDependencies, val backup: KotlinScriptExternalDependencies) : KotlinScriptExternalDependencies {
        override val imports: List<String> get() = (current.imports + backup.imports).distinct()
        override val javaHome: String? get() = current.javaHome ?: backup.javaHome
        override val classpath: Iterable<File> get() = (current.classpath + backup.classpath).distinct()
        override val sources: Iterable<File> get() = (current.sources + backup.sources).distinct()
        override val scripts: Iterable<File> get() = (current.scripts + backup.scripts).distinct()
    }

    override val dependencyResolver: DependenciesResolver = ApiChangeDependencyResolverWrapper(
            OldSchoolDependencyResolver(defaultImports)
    )

    private inner class OldSchoolDependencyResolver(val defaultImports: List<String>) : ScriptDependenciesResolver {
        override fun resolve(script: ScriptContents, environment: Environment?, report: (ScriptDependenciesResolver.ReportSeverity, String, ScriptContents.Position?) -> Unit, previousDependencies: KotlinScriptExternalDependencies?): Future<KotlinScriptExternalDependencies?> {
            fun logClassloadingError(ex: Throwable) {
                logScriptDefMessage(ScriptDependenciesResolver.ReportSeverity.WARNING, ex.message ?: "Invalid script template: ${template.qualifiedName}", null)
            }

            try {
                val updatedDependencies = resolutionManager.resolve(script, environment, Companion::logScriptDefMessage, previousDependencies).get()
                val backupDependencies = EmptyDependenciesWithDefaultImports(defaultImports, EmptyDependencies())
                return if (updatedDependencies == null) PseudoFuture(backupDependencies)
                else PseudoFuture(MergeDependencies(updatedDependencies, backupDependencies))
            } catch (ex: Throwable) {
                logClassloadingError(ex)
            }
            return PseudoFuture(null)
        }
    }

    class InvalidScriptResolverAnnotation(val name: String, val annParams: List<Pair<String?, Any?>>?, val error: Exception? = null) : Annotation

    companion object {
        internal val log = Logger.getInstance(KotlinScriptDefinitionFromAnnotatedTemplate::class.java)

        fun logScriptDefMessage(reportSeverity: ScriptDependenciesResolver.ReportSeverity, s: String, position: ScriptContents.Position?): Unit {
            val msg = (position?.run { "[at $line:$col]" } ?: "") + s
            when (reportSeverity) {
                ScriptDependenciesResolver.ReportSeverity.ERROR -> log.error(msg)
                ScriptDependenciesResolver.ReportSeverity.WARNING -> log.warn(msg)
                ScriptDependenciesResolver.ReportSeverity.INFO -> log.info(msg)
                ScriptDependenciesResolver.ReportSeverity.DEBUG -> log.debug(msg)
            }
        }
    }
}
