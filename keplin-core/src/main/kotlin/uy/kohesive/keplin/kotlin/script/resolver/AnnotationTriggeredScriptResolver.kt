package uy.kohesive.keplin.kotlin.script.resolver

import java.io.File
import kotlin.reflect.KClass

interface AnnotationTriggeredScriptResolver {
    /**
     * Accepted Annotations are processed in priority order in the list
     */
    val acceptedAnnotations: List<KClass<out Annotation>>
    val autoImports: List<String>

    fun resolveForAnnotation(annotation: Annotation): List<File>
}

