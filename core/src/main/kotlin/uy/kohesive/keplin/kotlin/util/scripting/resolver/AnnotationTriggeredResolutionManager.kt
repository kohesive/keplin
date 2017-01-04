package uy.kohesive.keplin.kotlin.util.scripting.resolver

import org.jetbrains.kotlin.script.*
import uy.kohesive.keplin.kotlin.util.scripting.containingClasspath
import java.io.File
import java.util.concurrent.Future
import kotlin.reflect.KClass

class AnnotationTriggeredResolutionManager(val resolvers: List<AnnotationTriggeredScriptResolver>,
                                           val defaultImports: List<String> = emptyList()) : ScriptDependenciesResolver {
    class ResolvedDependencies(override val classpath: List<File>, override val imports: List<String>) : KotlinScriptExternalDependencies

    val annotationSortOrder: Map<KClass<out Annotation>, Int> = HashMap<KClass<out Annotation>, Int>().apply {
        resolvers.forEachIndexed { idx: Int, resolver: AnnotationTriggeredScriptResolver ->
            resolver.acceptedAnnotations.forEachIndexed { subIdx: Int, ann: KClass<out Annotation> ->
                put(ann, idx * 1000 + subIdx)
            }
        }
    }

    override fun resolve(script: ScriptContents,
                         environment: Map<String, Any?>?,
                         report: (ScriptDependenciesResolver.ReportSeverity, String, ScriptContents.Position?) -> Unit,
                         previousDependencies: KotlinScriptExternalDependencies?): Future<KotlinScriptExternalDependencies?> {
        // sort script annotations first by resolver order then by accepted order within resolver, this allows the resolver
        // to be sure some actions occur before others, i.e. adding a maven repository before depending on the maven artifact.
        val sortedScriptAnnotations = script.annotations.sortedBy { annotationSortOrder.getOrDefault(it.javaClass.kotlin, 0) }
        val depsFromAnnotations = sortedScriptAnnotations.map { annotation ->
            val resolver = when (annotation) {
                is InvalidScriptResolverAnnotation -> throw Exception("Invalid annotation ${annotation.annotationClass}", annotation.error)
                else -> {
                    resolvers.firstOrNull { it.acceptedAnnotations.any { it == annotation.annotationClass } }
                            ?: throw Exception("Unknown annotation ${annotation.annotationClass}")
                }
            }
            resolver.resolveForAnnotation(annotation)
        }.flatten()

        val defaultClassPath: List<File> = if (previousDependencies == null) {
            resolvers.map {
                it.acceptedAnnotations.map {
                    it.containingClasspath()
                            ?: throw IllegalStateException("Missing JAR containing ${it} annotation")
                }
            }.flatten().distinct()
        } else emptyList()

        val defaultImports = if (previousDependencies == null) (defaultImports + (resolvers.map { it.autoImports }.flatten())).distinct() else emptyList()

        return ResolvedDependencies(defaultClassPath + depsFromAnnotations, defaultImports).asFuture()
    }
}