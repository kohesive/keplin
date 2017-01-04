package uy.kohesive.keplin.kotlin.util.scripting.resolver.local

@Target(AnnotationTarget.FILE, AnnotationTarget.EXPRESSION, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class DependsOnJar(val filename: String)

@Target(AnnotationTarget.FILE, AnnotationTarget.EXPRESSION, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class DirRepository(val path: String)


