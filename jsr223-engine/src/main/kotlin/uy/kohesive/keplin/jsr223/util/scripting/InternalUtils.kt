package uy.kohesive.keplin.jsr223.util.scripting

import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.utils.PathUtil
import java.io.File
import java.io.FileNotFoundException
import java.net.URI
import java.net.URL
import java.net.URLClassLoader
import java.util.jar.Manifest
import kotlin.reflect.KClass
import kotlin.script.templates.standard.ScriptTemplateWithArgs


internal fun <T> Iterable<T>.anyOrNull(predicate: (T) -> Boolean) = if (any(predicate)) this else null

internal fun File.matchMaybeVersionedFile(baseName: String) =
        name == baseName ||
                name == baseName.removeSuffix(".jar") || // for classes dirs
                name.startsWith(baseName.removeSuffix(".jar") + "-")

internal fun contextClasspath(keyName: String, classLoader: ClassLoader): List<File>? =
        (classpathFromClassloader(classLoader)?.anyOrNull { it.matchMaybeVersionedFile(keyName) }
                ?: manifestClassPath(classLoader)?.anyOrNull { it.matchMaybeVersionedFile(keyName) }
                )?.toList()


internal fun scriptCompilationClasspathFromContext(classLoader: ClassLoader = Thread.currentThread().contextClassLoader): List<File> =
        (System.getProperty("kotlin.script.classpath")?.split(File.pathSeparator)?.map(::File)
                ?: contextClasspath(PathUtil.KOTLIN_JAVA_RUNTIME_JAR, classLoader)
                ?: listOf(kotlinRuntimeJar, kotlinScriptRuntimeJar)
                )
                .map { it?.canonicalFile }
                .distinct()
                .mapNotNull { it?.existsOrNull() }


internal fun File.existsOrNull(): File? = existsAndCheckOrNull { true }
internal inline fun File.existsAndCheckOrNull(check: (File.() -> Boolean)): File? = if (exists() && check()) this else null

internal val kotlinCompilerJar: File by lazy {
    // highest prio - explicit property
    System.getProperty("kotlin.compiler.jar")?.let(::File)?.existsOrNull()
            // search classpath from context classloader and `java.class.path` property
            ?: (classpathFromClass(Thread.currentThread().contextClassLoader, K2JVMCompiler::class)
            ?: contextClasspath(PathUtil.KOTLIN_COMPILER_JAR, Thread.currentThread().contextClassLoader)
            ?: classpathFromClasspathProperty()
            )?.firstOrNull { it.matchMaybeVersionedFile(PathUtil.KOTLIN_COMPILER_JAR) }
            ?: throw FileNotFoundException("Cannot find kotlin compiler jar, set kotlin.compiler.jar property to proper location")
}

internal val kotlinRuntimeJar: File? by lazy {
    System.getProperty("kotlin.java.runtime.jar")?.let(::File)?.existsOrNull()
            ?: kotlinCompilerJar.let { File(it.parentFile, PathUtil.KOTLIN_JAVA_RUNTIME_JAR) }.existsOrNull()
            ?: PathUtil.getResourcePathForClass(JvmStatic::class.java).existsOrNull()
}

internal val kotlinScriptRuntimeJar: File? by lazy {
    System.getProperty("kotlin.script.runtime.jar")?.let(::File)?.existsOrNull()
            ?: kotlinCompilerJar.let { File(it.parentFile, PathUtil.KOTLIN_JAVA_SCRIPT_RUNTIME_JAR) }.existsOrNull()
            ?: PathUtil.getResourcePathForClass(ScriptTemplateWithArgs::class.java).existsOrNull()
}


internal fun URL.toFile() =
        try {
            File(toURI().schemeSpecificPart)
        } catch (e: java.net.URISyntaxException) {
            if (protocol != "file") null
            else File(file)
        }

internal fun classpathFromClassloader(classLoader: ClassLoader): List<File>? =
        generateSequence(classLoader) { it.parent }.toList().flatMap { (it as? URLClassLoader)?.urLs?.mapNotNull(URL::toFile) ?: emptyList() }

internal fun classpathFromClasspathProperty(): List<File>? =
        System.getProperty("java.class.path")
                ?.split(String.format("\\%s", File.pathSeparatorChar).toRegex())
                ?.dropLastWhile(String::isEmpty)
                ?.map(::File)

internal fun classpathFromClass(classLoader: ClassLoader, klass: KClass<out Any>): List<File>? {
    val clp = "${klass.qualifiedName?.replace('.', '/')}.class"
    val url = classLoader.getResource(clp)
    return url?.toURI()?.path?.removeSuffix(clp)?.let {
        listOf(File(it))
    }
}

// Maven runners sometimes place classpath into the manifest, so we can use it for a fallback search
internal fun manifestClassPath(classLoader: ClassLoader): List<File>? =
        classLoader.getResources("META-INF/MANIFEST.MF")
                .asSequence()
                .mapNotNull { ifFailed(null) { it.openStream().use { Manifest().apply { read(it) } } } }
                .flatMap { it.mainAttributes?.getValue("Class-Path")?.splitToSequence(" ") ?: emptySequence() }
                .mapNotNull { ifFailed(null) { File(URI.create(it)) } }
                .toList()
                .let { if (it.isNotEmpty()) it else null }

internal inline fun <R> ifFailed(default: R, block: () -> R) = try {
    block()
} catch (t: Throwable) {
    default
}
