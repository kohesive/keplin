package uy.kohesive.keplin.kotlin.util.scripting.resolver.local

import org.jetbrains.kotlin.utils.addToStdlib.check
import uy.kohesive.keplin.kotlin.util.scripting.resolver.AnnotationTriggeredScriptResolver
import java.io.File
import java.util.concurrent.ConcurrentLinkedDeque

open class JarFileScriptDependenciesResolver() : uy.kohesive.keplin.kotlin.util.scripting.resolver.AnnotationTriggeredScriptResolver {
    override val acceptedAnnotations = listOf(DirRepository::class, DependsOnJar::class)
    override val autoImports = listOf(DependsOnJar::class.java.`package`.name + ".*")

    private val resolvers = java.util.concurrent.ConcurrentLinkedDeque<LocalJarResolver>(listOf(DirectResolver()))

    override fun resolveForAnnotation(annotation: Annotation): List<java.io.File> {
        return when (annotation) {
            is uy.kohesive.keplin.kotlin.util.scripting.resolver.local.DirRepository -> {
                uy.kohesive.keplin.kotlin.util.scripting.resolver.local.JarFileScriptDependenciesResolver.FlatLibDirectoryResolver.Companion.tryCreate(annotation)
                        ?.apply { resolvers.add(this) }
                        ?: throw IllegalArgumentException("Illegal argument for DirRepository annotation: $annotation")
                emptyList()
            }
            is uy.kohesive.keplin.kotlin.util.scripting.resolver.local.DependsOnJar -> {
                resolvers.asSequence().mapNotNull { it.tryResolve(annotation) }.firstOrNull()?.toList() ?:
                        throw Exception("Unable to resolve dependency $annotation")

            }
            else -> throw Exception("Unknown annotation ${annotation.javaClass}")
        }

    }


    interface LocalJarResolver {
        fun tryResolve(dependsOn: uy.kohesive.keplin.kotlin.util.scripting.resolver.local.DependsOnJar): Iterable<java.io.File>?
    }

    class DirectResolver : uy.kohesive.keplin.kotlin.util.scripting.resolver.local.JarFileScriptDependenciesResolver.LocalJarResolver {
        override fun tryResolve(dependsOn: uy.kohesive.keplin.kotlin.util.scripting.resolver.local.DependsOnJar): Iterable<java.io.File>? {
            return dependsOn.filename.check(String::isNotBlank)
                    ?.let(::File)
                    ?.check { it.exists() && (it.isFile || it.isDirectory) }
                    ?.let { listOf(it) }
        }
    }

    class FlatLibDirectoryResolver(val path: File) : LocalJarResolver {
        init {
            if (!path.exists() || !path.isDirectory) throw IllegalArgumentException("Invalid flat lib directory repository path '$path'")
        }

        override fun tryResolve(dependsOn: DependsOnJar): Iterable<File>? =
                // TODO: add coordinates and wildcard matching
                dependsOn.filename.check(String::isNotBlank)
                        ?.let { File(path, it) }
                        ?.check { it.exists() && (it.isFile || it.isDirectory) }
                        ?.let { listOf(it) }

        companion object {
            fun tryCreate(annotation: DirRepository): FlatLibDirectoryResolver? =
                    annotation.path.check(String::isNotBlank)
                            ?.let(::File)
                            ?.check { it.exists() && it.isDirectory }
                            ?.let(::FlatLibDirectoryResolver)
        }
    }
}
