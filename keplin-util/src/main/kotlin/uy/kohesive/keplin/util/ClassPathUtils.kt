package uy.kohesive.keplin.util

import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.cli.jvm.repl.ReplInterpreter
import java.io.File
import java.net.URL
import java.util.*
import kotlin.reflect.KClass

// TODO: normalize all this, PathUtils might have some, there are dupes from different use cases of the same

object ClassPathUtils {
    fun findKotlinCompilerJarsOrEmpty(classLoader: ClassLoader, useEmbeddedCompiler: Boolean = true): List<File> {
        val filter = if (useEmbeddedCompiler) """.*\/kotlin-compiler-embeddable.*\.jar""".toRegex()
        else """.*\/kotlin-compiler-(?!embeddable).*\.jar""".toRegex()
        return listOf(K2JVMCompiler::class.containingClasspath(classLoader, filter)).filterNotNull()
    }

    fun findKotlinReflectJarsOrEmpty(classLoader: ClassLoader): List<File> {
        val filter = """.*\/kotlin-reflect.*\.jar""".toRegex()
        // TODO: this is wrong, we need a way to see reflect jar but cannot see
        // any of its classes due to compiler preventing it.
        return listOf(kotlin.reflect.KType::class.containingClasspath(classLoader, filter)).filterNotNull()
    }

    fun findKotlinCompilerJars(classLoader: ClassLoader, useEmbeddedCompiler: Boolean = true): List<File> {
        return findKotlinCompilerJarsOrEmpty(classLoader, useEmbeddedCompiler).assertNotEmpty("Cannot find kotlin compiler classpath, which is required")
    }

    fun findKotlinReflectJars(classLoader: ClassLoader): List<File> {
        return findKotlinReflectJarsOrEmpty(classLoader).assertNotEmpty("Cannot find kotlin reflect classpath, which is required")
    }

    fun findKotlinStdLibJarsOrEmpty(classLoader: ClassLoader): List<File> {
        return listOf(Pair::class.containingClasspath(classLoader, """.*\/kotlin-stdlib.*\.jar""".toRegex())).filterNotNull()
    }

    fun findKotlinStdLibOrEmbeddedCompilerJars(classLoader: ClassLoader): List<File> {
        return (findKotlinStdLibJarsOrEmpty(classLoader).takeUnless { it.isEmpty() }
                ?: findKotlinCompilerJarsOrEmpty(classLoader, true)).assertNotEmpty("Cannot find kotlin stdlib classpath, which is required")
    }

    fun findKotlinStdLibJars(classLoader: ClassLoader): List<File> {
        return findKotlinStdLibJarsOrEmpty(classLoader).assertNotEmpty("Cannot find kotlin stdlib classpath, which is required")
    }

    fun findKotlinRuntimeJarsOrEmpty(classLoader: ClassLoader): List<File> {
        return emptyList()
        // no longer in 1.1 since beta-38
        // listOf(JvmName::class.containingClasspath(""".*\/kotlin-runtime.*\.jar""".toRegex())).filterNotNull()
    }

    fun findKotlinRuntimeJars(classLoader: ClassLoader): List<File> {
        return emptyList()
        // no longer in 1.1 since beta-38
        // findKotlinRuntimeJarsOrEmpty().assertNotEmpty("Cannot find kotlin runtime classpath, which is required")
    }

    fun findClassJarsOrEmpty(klass: KClass<out Any>, classLoader: ClassLoader = klass.java.classLoader, filterJarByRegex: Regex = ".*".toRegex()): List<File> {
        return listOf(klass.containingClasspath(classLoader, filterJarByRegex)).filterNotNull()
    }

    fun findClassJars(klass: KClass<out Any>, classLoader: ClassLoader = klass.java.classLoader, filterJarByRegex: Regex = ".*".toRegex()): List<File> {
        return findClassJarsOrEmpty(klass, classLoader, filterJarByRegex).assertNotEmpty("Cannot find required JAR for $klass")
    }

    fun findRequiredScriptingJarFiles(templateClass: KClass<out Any>? = null,
                                      includeScriptEngine: Boolean = false,
                                      includeKotlinCompiler: Boolean = false,
                                      useEmbeddableCompiler: Boolean = true,
                                      includeStdLib: Boolean = true,
                                      includeRuntime: Boolean = true,
                                      additionalClasses: List<KClass<out Any>> = emptyList(),
                                      kotlinClassLoader: ClassLoader = this.javaClass.classLoader): List<File> {
        val templateClassJars = if (templateClass != null) findClassJarsOrEmpty(templateClass).assertNotEmpty("Cannot find template classpath, which is required")
        else emptyList()
        val additionalClassJars = additionalClasses.map { findClassJarsOrEmpty(it).assertNotEmpty("Missing JAR for additional class $it") }.flatten()
        val scriptEngineJars = if (includeScriptEngine) findClassJarsOrEmpty(ReplInterpreter::class).assertNotEmpty("Cannot find repl engine classpath, which is required")
        else emptyList()
        val kotlinJars = (if (includeKotlinCompiler) findKotlinCompilerJars(kotlinClassLoader, useEmbeddableCompiler) else emptyList()) +
                (if (includeStdLib) findKotlinStdLibOrEmbeddedCompilerJars(kotlinClassLoader) else emptyList()) +
                (if (includeRuntime) findKotlinRuntimeJars(kotlinClassLoader) else emptyList())
        return (templateClassJars + additionalClassJars + scriptEngineJars + kotlinJars).toSet().toList()
    }

    private val zipOrJarRegex = """(?:zip:|jar:file:)(.*)!\/(?:.*)""".toRegex()
    private val filePathRegex = """(?:file:)(.*)""".toRegex()

    private fun zipOrJarUrlToBaseFile(url: String): String? {
        return zipOrJarRegex.find(url)?.let { it.groupValues[1] }
    }

    private fun classFilenameToBaseDir(url: String, resource: String): String? {
        return filePathRegex.find(url)?.let { it.groupValues[1].removeSuffix(resource) }
    }

    fun <T : Any> classPathOf(clazz: KClass<T>, classLoader: ClassLoader = clazz.java.classLoader, filterJarName: Regex = ".*".toRegex()): File? {
        return clazz.containingClasspath(classLoader, filterJarName)
    }

    fun <T : Any> getResources(classLoader: ClassLoader, relatedClass: KClass<T>, name: String): Enumeration<URL>? {
        return classLoader.getResources(name)
                ?: relatedClass.java.classLoader.getResources(name)
                ?: Thread.currentThread().contextClassLoader.getResources(name)
    }

    private fun <T : Any> KClass<T>.containingClasspath(classLoader: ClassLoader, filterJarName: Regex = ".*".toRegex()): File? {
        val clp = "${qualifiedName?.replace('.', '/')}.class"
        val baseList = getResources(classLoader, this, clp)?.toList()?.map { it.toString() }
        return baseList
                ?.map { url ->
                    zipOrJarUrlToBaseFile(url) ?: qualifiedName?.let { classFilenameToBaseDir(url, clp) }
                            ?: throw IllegalStateException("Expecting a local classpath when searching for class: ${qualifiedName}")
                }
                ?.find {
                    filterJarName.matches(it)
                }
                ?.let { File(it) }
    }

}
fun <T : Any> List<T>.assertNotEmpty(error: String): List<T> {
    if (this.isEmpty()) throw IllegalStateException(error)
    return this
}