package uy.kohesive.keplin.util

import org.jetbrains.kotlin.cli.common.repl.ScriptArgsWithTypes
import org.jetbrains.kotlin.script.DependencyResolverWrapper
import org.jetbrains.kotlin.script.KotlinScriptDefinition
import org.jetbrains.kotlin.script.LegacyResolverWrapper
import java.io.File
import java.util.concurrent.Future
import kotlin.reflect.KClass
import kotlin.script.dependencies.*
import kotlin.script.experimental.dependencies.DependenciesResolver
import kotlin.script.experimental.dependencies.ScriptDependencies
import kotlin.script.experimental.dependencies.ScriptReport


interface ScriptTemplateEmptyArgsProvider {
    val defaultEmptyArgs: ScriptArgsWithTypes?
}

open class KotlinScriptDefinitionEx(template: KClass<out Any>,
                                    override val defaultEmptyArgs: ScriptArgsWithTypes?,
                                    val defaultImports: List<String> = emptyList())
    : KotlinScriptDefinition(template), ScriptTemplateEmptyArgsProvider {

    override val dependencyResolver: DependenciesResolver
            = ApiChangeDependencyResolverWrapper(DefaultImportsDependencyResolver(defaultImports))
}

class DefaultImportsDependencyResolver(val defaultImports: List<String>) : ScriptDependenciesResolver {
    override fun resolve(script: ScriptContents, environment: Environment?, report: (ScriptDependenciesResolver.ReportSeverity, String, ScriptContents.Position?) -> Unit, previousDependencies: KotlinScriptExternalDependencies?): Future<KotlinScriptExternalDependencies?> {
        val base = super.resolve(script, environment, report, previousDependencies).get()
        val result = if (previousDependencies == null && defaultImports.isNotEmpty()) {
            EmptyDependenciesWithDefaultImports(defaultImports, base ?: EmptyDependencies())
        } else {
            base
        }
        return PseudoFuture(result)
    }
}

class EmptyDependencies() : KotlinScriptExternalDependencies
class EmptyDependenciesWithDefaultImports(val defaultImports: List<String>, val base: KotlinScriptExternalDependencies) : KotlinScriptExternalDependencies by base {
    override val imports: List<String> get() = (defaultImports + base.imports).distinct()
}


class ApiChangeDependencyResolverWrapper(
        override val delegate: kotlin.script.dependencies.ScriptDependenciesResolver
) : kotlin.script.experimental.dependencies.DependenciesResolver,
        DependencyResolverWrapper<ScriptDependenciesResolver>,
        LegacyResolverWrapper {
    override fun resolve(
            scriptContents: kotlin.script.dependencies.ScriptContents,
            environment: Environment
    ): DependenciesResolver.ResolveResult {
        val reports = ArrayList<ScriptReport>()
        val legacyDeps = delegate.resolve(
                scriptContents,
                environment,
                { sev, msg, pos ->
                    reports.add(ScriptReport(msg, sev.convertSeverity(), pos?.convertPosition()))
                }, null
        ).get() ?: return DependenciesResolver.ResolveResult.Failure(reports)

        val dependencies = ScriptDependencies(
                javaHome = legacyDeps.javaHome?.let(::File),
                classpath = legacyDeps.classpath.toList(),
                imports = legacyDeps.imports.toList(),
                sources = legacyDeps.sources.toList(),
                scripts = legacyDeps.scripts.toList()
        )
        return DependenciesResolver.ResolveResult.Success(dependencies, reports)
    }

    private fun kotlin.script.dependencies.ScriptDependenciesResolver.ReportSeverity.convertSeverity(): ScriptReport.Severity = when (this) {
        kotlin.script.dependencies.ScriptDependenciesResolver.ReportSeverity.ERROR -> ScriptReport.Severity.ERROR
        kotlin.script.dependencies.ScriptDependenciesResolver.ReportSeverity.WARNING -> ScriptReport.Severity.WARNING
        kotlin.script.dependencies.ScriptDependenciesResolver.ReportSeverity.INFO -> ScriptReport.Severity.INFO
        kotlin.script.dependencies.ScriptDependenciesResolver.ReportSeverity.DEBUG -> ScriptReport.Severity.DEBUG
    }

    private fun kotlin.script.dependencies.ScriptContents.Position.convertPosition(): ScriptReport.Position = ScriptReport.Position(line, col)
}