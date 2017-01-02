package uy.kohesive.keplin.kotlin.util.scripting.resolver

import java.io.File
import kotlin.reflect.KClass

interface AnnotationBasedScriptResolver {
    /**
     * Accepted Annotations are processed in priority order in the list
     */
    val acceptedAnnotations: List<KClass<out Annotation>>
    val autoImports: List<String>

    fun resolveForAnnotation(annotation: Annotation): List<File>
}

