package uy.kohesive.keplin.util

import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.cli.jvm.repl.GenericRepl
import org.jetbrains.kotlin.utils.PathUtil
import java.io.File
import java.io.FileNotFoundException
import java.net.URI
import java.net.URL
import java.net.URLClassLoader
import java.util.jar.Manifest
import kotlin.reflect.KClass
import kotlin.script.templates.standard.ScriptTemplateWithArgs

// TODO: normalize all this, PathUtils might have some, there are dupes from different use cases of the same

object ClassPathUtils {
    fun contextClasspath(keyName: String, classLoader: ClassLoader): List<File>? =
            (classpathFromClassloader(classLoader)?.anyOrNull { it.matchMaybeVersionedFile(keyName) }
                    ?: manifestClassPath(classLoader)?.anyOrNull { it.matchMaybeVersionedFile(keyName) }
                    )?.toList()


    fun scriptCompilationClasspathFromContext(classLoader: ClassLoader = Thread.currentThread().contextClassLoader): List<File> =
            (System.getProperty("kotlin.script.classpath")?.split(File.pathSeparator)?.map(::File)
                    ?: contextClasspath(PathUtil.KOTLIN_JAVA_RUNTIME_JAR, classLoader)
                    ?: listOf(kotlinRuntimeJar, kotlinScriptRuntimeJar)
                    )
                    .map { it?.canonicalFile }
                    .distinct()
                    .mapNotNull { it?.existsOrNull() }

    val kotlinCompilerJar: File by lazy {
        // highest prio - explicit property
        System.getProperty("kotlin.compiler.jar")?.let(::File)?.existsOrNull()
                // search classpath from context classloader and `java.class.path` property
                ?: (classpathFromClass(Thread.currentThread().contextClassLoader, K2JVMCompiler::class)
                ?: contextClasspath(PathUtil.KOTLIN_COMPILER_JAR, Thread.currentThread().contextClassLoader)
                ?: classpathFromClasspathProperty()
                )?.firstOrNull { it.matchMaybeVersionedFile(PathUtil.KOTLIN_COMPILER_JAR) }
                ?: throw FileNotFoundException("Cannot find kotlin compiler jar, set kotlin.compiler.jar property to proper location")
    }

    val kotlinRuntimeJar: File? by lazy {
        System.getProperty("kotlin.java.runtime.jar")?.let(::File)?.existsOrNull()
                ?: kotlinCompilerJar.let { File(it.parentFile, PathUtil.KOTLIN_JAVA_RUNTIME_JAR) }.existsOrNull()
                ?: PathUtil.getResourcePathForClass(JvmStatic::class.java).existsOrNull()
    }

    val kotlinScriptRuntimeJar: File? by lazy {
        System.getProperty("kotlin.script.runtime.jar")?.let(::File)?.existsOrNull()
                ?: kotlinCompilerJar.let { File(it.parentFile, PathUtil.KOTLIN_JAVA_SCRIPT_RUNTIME_JAR) }.existsOrNull()
                ?: PathUtil.getResourcePathForClass(ScriptTemplateWithArgs::class.java).existsOrNull()
    }


    fun classpathFromClassloader(classLoader: ClassLoader): List<File>? =
            generateSequence(classLoader) { it.parent }.toList().flatMap { (it as? URLClassLoader)?.urLs?.mapNotNull(URL::toFile) ?: emptyList() }

    fun classpathFromClasspathProperty(): List<File>? =
            System.getProperty("java.class.path")
                    ?.split(String.format("\\%s", File.pathSeparatorChar).toRegex())
                    ?.dropLastWhile(String::isEmpty)
                    ?.map(::File)

    fun classpathFromClass(classLoader: ClassLoader, klass: KClass<out Any>): List<File>? {
        val clp = "${klass.qualifiedName?.replace('.', '/')}.class"
        val url = classLoader.getResource(clp)
        return url?.toURI()?.path?.removeSuffix(clp)?.let {
            listOf(File(it))
        }
    }

    // Maven runners sometimes place classpath into the manifest, so we can use it for a fallback search
    fun manifestClassPath(classLoader: ClassLoader): List<File>? =
            classLoader.getResources("META-INF/MANIFEST.MF")
                    .asSequence()
                    .mapNotNull { ifFailed(null) { it.openStream().use { Manifest().apply { read(it) } } } }
                    .flatMap { it.mainAttributes?.getValue("Class-Path")?.splitToSequence(" ") ?: emptySequence() }
                    .mapNotNull { ifFailed(null) { File(URI.create(it)) } }
                    .toList()
                    .let { if (it.isNotEmpty()) it else null }


    fun findKotlinCompilerJarsOrEmpty(useEmbeddedCompiler: Boolean = true): List<File> {
        val filter = if (useEmbeddedCompiler) """.*\/kotlin-compiler-embeddable.*\.jar""".toRegex()
        else """.*\/kotlin-compiler-(?!embeddable).*\.jar""".toRegex()
        return listOf(K2JVMCompiler::class.containingClasspath(filter)).filterNotNull()
    }

    fun findKotlinCompilerJars(useEmbeddedCompiler: Boolean = true): List<File> {
        return findKotlinCompilerJarsOrEmpty(useEmbeddedCompiler).assertNotEmpty("Cannot find kotlin compiler classpath, which is required")
    }

    fun findKotlinStdLibJarsOrEmpty(): List<File> {
        return listOf(Pair::class.containingClasspath(""".*\/kotlin-stdlib.*\.jar""".toRegex())).filterNotNull()
    }

    fun findKotlinStdLibOrEmbeddedCompilerJars(): List<File> {
        return (findKotlinStdLibJarsOrEmpty().takeUnless { it.isEmpty() } ?: findKotlinCompilerJarsOrEmpty(true)).assertNotEmpty("Cannot find kotlin stdlib classpath, which is required")
    }

    fun findKotlinStdLibJars(): List<File> {
        return findKotlinStdLibJarsOrEmpty().assertNotEmpty("Cannot find kotlin stdlib classpath, which is required")
    }

    fun findKotlinRuntimeJarsOrEmpty(): List<File> {
        return emptyList()
        // no longer in 1.1 since beta-38
        // listOf(JvmName::class.containingClasspath(""".*\/kotlin-runtime.*\.jar""".toRegex())).filterNotNull()
    }

    fun findKotlinRuntimeJars(): List<File> {
        return emptyList()
        // no longer in 1.1 since beta-38
        // findKotlinRuntimeJarsOrEmpty().assertNotEmpty("Cannot find kotlin runtime classpath, which is required")
    }

    fun findClassJarsOrEmpty(klass: KClass<out Any>, filterJarByRegex: Regex = ".*".toRegex()): List<File> {
        return listOf(klass.containingClasspath(filterJarByRegex)).filterNotNull()
    }

    fun findClassJars(klass: KClass<out Any>, filterJarByRegex: Regex = ".*".toRegex()): List<File> {
        return findClassJarsOrEmpty(klass, filterJarByRegex).assertNotEmpty("Cannot find required JAR for $klass")
    }

    fun findRequiredScriptingJarFiles(templateClass: KClass<out Any>? = null,
                                      includeScriptEngine: Boolean = false,
                                      includeKotlinCompiler: Boolean = false,
                                      useEmbeddableCompiler: Boolean = true,
                                      includeStdLib: Boolean = true,
                                      includeRuntime: Boolean = true,
                                      additionalClasses: List<KClass<out Any>> = emptyList()): List<File> {
        val templateClassJars = if (templateClass != null) findClassJarsOrEmpty(templateClass).assertNotEmpty("Cannot find template classpath, which is required")
        else emptyList()
        val additionalClassJars = additionalClasses.map { findClassJarsOrEmpty(it).assertNotEmpty("Missing JAR for additional class $it") }.flatten()
        val scriptEngineJars = if (includeScriptEngine) findClassJarsOrEmpty(GenericRepl::class).assertNotEmpty("Cannot find repl engine classpath, which is required")
        else emptyList()
        val kotlinJars = (if (includeKotlinCompiler) findKotlinCompilerJars(useEmbeddableCompiler) else emptyList()) +
                (if (includeStdLib) findKotlinStdLibOrEmbeddedCompilerJars() else emptyList()) +
                (if (includeRuntime) findKotlinRuntimeJars() else emptyList())
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

    private fun <T : Any> KClass<T>.containingClasspath(filterJarName: Regex = ".*".toRegex()): File? {
        val clp = "${qualifiedName?.replace('.', '/')}.class"
        val baseList = Thread.currentThread().contextClassLoader.getResources(clp)
                ?.toList()
                ?.map { it.toString() }
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

private fun File.existsOrNull(): File? = existsAndCheckOrNull { true }
private inline fun File.existsAndCheckOrNull(check: (File.() -> Boolean)): File? = if (exists() && check()) this else null

private fun <T> Iterable<T>.anyOrNull(predicate: (T) -> Boolean) = if (any(predicate)) this else null

private fun File.matchMaybeVersionedFile(baseName: String) =
        name == baseName ||
                name == baseName.removeSuffix(".jar") || // for classes dirs
                name.startsWith(baseName.removeSuffix(".jar") + "-")

private fun URL.toFile() =
        try {
            File(toURI().schemeSpecificPart)
        } catch (e: java.net.URISyntaxException) {
            if (protocol != "file") null
            else File(file)
        }


private inline fun <R> ifFailed(default: R, block: () -> R) = try {
    block()
} catch (t: Throwable) {
    default
}

fun <T : Any> List<T>.assertNotEmpty(error: String): List<T> {
    if (this.isEmpty()) throw IllegalStateException(error)
    return this
}