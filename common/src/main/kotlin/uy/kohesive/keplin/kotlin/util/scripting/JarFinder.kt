package uy.kohesive.keplin.kotlin.util.scripting

import org.jetbrains.kotlin.cli.jvm.repl.GenericReplCompiler
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import java.io.File
import kotlin.reflect.KClass

fun findRequiredScriptingJarFiles(templateClass: KClass<out Any>, additionalClasses: List<KClass<out Any>> = emptyList()): List<File> {
    val templateClassJar = templateClass.containingClasspath() ?:
            throw IllegalStateException("Cannot find template classpath, which is required")
    val additionalClassJars = additionalClasses.map { it.containingClasspath() ?: throw IllegalStateException("Missing JAR for additional class $it }") }
    val kotlinJars = listOf(
            GenericReplCompiler::class.containingClasspath() ?:
                    throw IllegalStateException("Cannot find repl engine classpath, which is required"),
            KotlinCompilerVersion::class.containingClasspath() ?:
                    throw IllegalStateException("Cannot find kotlin compiler classpath, which is required"),
            Pair::class.containingClasspath() ?:
                    throw IllegalStateException("Cannot find kotlin stdlib classpath, which is required"),
            JvmName::class.containingClasspath() ?:
                    throw IllegalStateException("Cannot find kotlin runtime classpath, which is required")
    )
    return (listOf(templateClassJar) + additionalClassJars + kotlinJars).toSet().toList()
}

fun <T : Any> KClass<T>.containingClasspath(): File? {
    val clp = "${qualifiedName?.replace('.', '/')}.class"
    val url = Thread.currentThread().contextClassLoader.getResource(clp)?.toString() ?: return null
    val zipOrJarRegex = """(?:zip:|jar:file:)(.*)!\/(?:.*)""".toRegex()
    val filePathRegex = """(?:file:)(.*)""".toRegex()
    val foundPath = zipOrJarRegex.find(url)?.let { it.groupValues[1] }
            ?: filePathRegex.find(url)?.let { it.groupValues[1].removeSuffix(clp) }
            ?: throw IllegalStateException("Expecting a local classpath when searching for class: ${qualifiedName}")
    return File(foundPath)
}