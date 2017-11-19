package uy.kohesive.keplin.kotlin.script.resolver

/*
internal class ApiChangeDependencyResolverWrapper(
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
}*/