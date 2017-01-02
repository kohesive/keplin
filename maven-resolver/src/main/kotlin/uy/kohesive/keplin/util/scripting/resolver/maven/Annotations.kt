package uy.kohesive.keplin.util.scripting.resolver.maven

@Target(AnnotationTarget.FILE, AnnotationTarget.EXPRESSION, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class DependsOnMaven(val fullGAV: String)

@Target(AnnotationTarget.FILE, AnnotationTarget.EXPRESSION, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class MavenRepository(val id: String = "", val url: String)

