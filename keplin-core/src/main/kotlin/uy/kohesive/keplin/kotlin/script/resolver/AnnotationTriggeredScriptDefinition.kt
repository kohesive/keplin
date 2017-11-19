package uy.kohesive.keplin.kotlin.script.resolver

import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.cli.common.repl.ScriptArgsWithTypes
import org.jetbrains.kotlin.com.intellij.openapi.diagnostic.Logger
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.openapi.vfs.StandardFileSystems
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.com.intellij.psi.PsiFile
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.NameUtils
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.resolve.BindingTraceContext
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.script.KotlinScriptDefinitionFromAnnotatedTemplate
import org.jetbrains.kotlin.script.tryCreateCallableMappingFromNamedArgs
import org.jetbrains.kotlin.types.TypeUtils
import uy.kohesive.keplin.util.ApiChangeDependencyResolverWrapper
import uy.kohesive.keplin.util.KotlinScriptDefinitionEx
import java.io.File
import java.util.concurrent.Future
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor
import kotlin.script.dependencies.*
import kotlin.script.experimental.dependencies.DependenciesResolver
import kotlin.script.templates.DEFAULT_SCRIPT_FILE_PATTERN

open class AnnotationTriggeredScriptDefinition(definitionName: String,
                                               template: KClass<out Any>,
                                               defaultEmptyArgs: ScriptArgsWithTypes? = null,
                                               val resolvers: List<AnnotationTriggeredScriptResolver> = emptyList(),
                                               defaultImports: List<String> = emptyList(),
                                               val scriptFilePattern: Regex = DEFAULT_SCRIPT_FILE_PATTERN.toRegex(),
                                               val environment: Map<String, Any?>? = null) :
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

    override val dependencyResolver: DependenciesResolver = ApiChangeDependencyResolverWrapper(OldSchoolDependencyResolver())


    private inner class OldSchoolDependencyResolver : ScriptDependenciesResolver {
        override fun resolve(script: ScriptContents, environment: Environment?, report: (ScriptDependenciesResolver.ReportSeverity, String, ScriptContents.Position?) -> Unit, previousDependencies: KotlinScriptExternalDependencies?): Future<KotlinScriptExternalDependencies?> {
            fun logClassloadingError(ex: Throwable) {
                logScriptDefMessage(ScriptDependenciesResolver.ReportSeverity.WARNING, ex.message ?: "Invalid script template: ${template.qualifiedName}", null)
            }

            try {
                val fileDeps = resolutionManager.resolve(script, environment, Companion::logScriptDefMessage, previousDependencies)

                val updatedDependencies = fileDeps.get()
                val backupDependencies = super.resolve(script, environment, report, previousDependencies).get()
                return if (updatedDependencies == null || backupDependencies == null) PseudoFuture(updatedDependencies ?: backupDependencies)
                else PseudoFuture(MergeDependencies(updatedDependencies, backupDependencies))
            } catch (ex: Throwable) {
                logClassloadingError(ex)
            }
            return PseudoFuture(null)
        }

        private val KtAnnotationEntry.typeName: String get() = (typeReference?.typeElement as? KtUserType)?.referencedName.orAnonymous()

        internal fun String?.orAnonymous(kind: String = ""): String =
                this ?: "<anonymous" + (if (kind.isNotBlank()) " $kind" else "") + ">"

        internal fun constructAnnotation(psi: KtAnnotationEntry, targetClass: KClass<out Annotation>): Annotation {

            val valueArguments = psi.valueArguments.map { arg ->
                val evaluator = ConstantExpressionEvaluator(DefaultBuiltIns.Instance, LanguageVersionSettingsImpl.DEFAULT)
                val trace = BindingTraceContext()
                val result = evaluator.evaluateToConstantValue(arg.getArgumentExpression()!!, trace, TypeUtils.NO_EXPECTED_TYPE)
                // TODO: consider inspecting `trace` to find diagnostics reported during the computation (such as division by zero, integer overflow, invalid annotation parameters etc.)
                val argName = arg.getArgumentName()?.asName?.toString()
                argName to result?.value
            }
            val mappedArguments: Map<KParameter, Any?> =
                    tryCreateCallableMappingFromNamedArgs(targetClass.constructors.first(), valueArguments)
                            ?: return InvalidScriptResolverAnnotation(psi.typeName, valueArguments)

            try {
                return targetClass.primaryConstructor!!.callBy(mappedArguments)
            } catch (ex: Exception) {
                return InvalidScriptResolverAnnotation(psi.typeName, valueArguments, ex)
            }
        }


        private fun <TF : Any> getAnnotationEntries(file: TF, project: Project): Iterable<KtAnnotationEntry> = when (file) {
            is PsiFile -> getAnnotationEntriesFromPsiFile(file)
            is VirtualFile -> getAnnotationEntriesFromVirtualFile(file, project)
            is File -> {
                val virtualFile = (StandardFileSystems.local().findFileByPath(file.canonicalPath)
                        ?: throw IllegalArgumentException("Unable to find file ${file.canonicalPath}"))
                getAnnotationEntriesFromVirtualFile(virtualFile, project)
            }
            else -> throw IllegalArgumentException("Unsupported file type $file")
        }

        private fun getAnnotationEntriesFromPsiFile(file: PsiFile) =
                (file as? KtFile)?.annotationEntries
                        ?: throw IllegalArgumentException("Unable to extract kotlin annotations from ${file.name} (${file.fileType})")

        private fun getAnnotationEntriesFromVirtualFile(file: VirtualFile, project: Project): Iterable<KtAnnotationEntry> {
            val psiFile: PsiFile = PsiManager.getInstance(project).findFile(file)
                    ?: throw IllegalArgumentException("Unable to load PSI from ${file.canonicalPath}")
            return getAnnotationEntriesFromPsiFile(psiFile)
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
