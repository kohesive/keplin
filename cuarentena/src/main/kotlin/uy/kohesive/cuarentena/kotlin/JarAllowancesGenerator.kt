package uy.kohesive.cuarentena.kotlin

import uy.kohesive.cuarentena.Cuarentena
import uy.kohesive.cuarentena.NamedClassBytes
import uy.kohesive.cuarentena.policy.toPolicy
import uy.kohesive.keplin.util.ClassPathUtils
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

enum class ScanMode {
    ALL, SAFE
}

data class ClassesVerifyResult(
    val perClassViolations: List<Cuarentena.VerifyResultsPerClass>,
    val violationClasses: Set<String>,
    val verifiedClasses: List<String>
)

interface DebugCallback {
    fun debug(line: String)
}
object DefaultDebugCallback : DebugCallback {
    override fun debug(line: String) {
        println(line)
    }
}

open class JarAllowancesGenerator(
    val jarFiles: List<String>,
    val scanMode: ScanMode,
    val excludePackages: List<String>,
    val excludeClasses: List<String>,
    val useBootStrapPolicy: Boolean = false,
    val verbose: Boolean = false,
    val debugCallback: DebugCallback = DefaultDebugCallback
) {

    constructor(jarFiles: List<String>, scanMode: String, excludePackages: List<String>, excludeClasses: List<String>, verbose: Boolean, debugCallback: DebugCallback) : this(
        jarFiles        = jarFiles,
        scanMode        = ScanMode.valueOf(scanMode),
        excludePackages = excludePackages,
        excludeClasses  = excludeClasses,
        verbose         = verbose,
        debugCallback   = debugCallback
    )

    init {
        if (jarFiles.isEmpty()) {
            throw IllegalArgumentException("No jar files passed")
        }
    }

    private fun debug(str: String) = debugCallback.debug(str)

    fun writePolicy(outputPath: String) {
        File(outputPath).bufferedWriter().use { writer ->
            generatePolicy().forEach {
                writer.write(it)
                writer.newLine()
            }
        }
    }

    fun verifyClasses(): ClassesVerifyResult {
        debug("Building kotlin whitelist for ${ ClassPathUtils.findKotlinStdLibJars().map { it.path }.joinToString(", ") }")
        val cuarentena = if (useBootStrapPolicy) {
            Cuarentena.createKotlinBootstrapCuarentena()
        } else {
            Cuarentena.createKotlinCuarentena()
        }
        debug("Done processing kotlin")

        val allClasses = jarFiles.map { File(it) }.flatMap { jarFile ->
            jarFile.getClassNames().map { className ->
                classBytesForClass(className, Thread.currentThread().contextClassLoader)
            }
        }

        val perClassViolations = cuarentena.verifyClassAgainstPoliciesPerClass(allClasses)
        val violationClasses   = perClassViolations.map { it.violatingClass.className }.toSet()

        if (verbose) {
            debug("[Cuarentena] Black-listed classes:")
            perClassViolations.sortedBy { it.violatingClass.className }.forEach {
                debug(" - ${it.violatingClass.className}, violation(s):")
                it.violations.forEach { debug("   - $it") }
            }
        }

        return ClassesVerifyResult(
            perClassViolations = perClassViolations,
            violationClasses   = violationClasses,
            verifiedClasses    = allClasses.map { it.className } - violationClasses
        )
    }

    fun generatePolicy(): List<String> {
        val verifiedClassNames = when (scanMode) {
            ScanMode.ALL  -> jarFiles.map { File(it) }.flatMap { it.getClassNames() }
            ScanMode.SAFE -> verifyClasses().verifiedClasses
        }.filter { className ->
            excludePackages.none { excludedPackage ->
                className.startsWith(excludedPackage)
            }
        } - excludeClasses

        val classAllowancesGenerator = FullClassAllowancesGenerator()
        return verifiedClassNames.flatMap { verifiedClassName ->
            classAllowancesGenerator.generateAllowances(Thread.currentThread().contextClassLoader.loadClass(verifiedClassName))
        }.toPolicy()
    }

    private fun classBytesForClass(className: String, useClassLoader: ClassLoader): NamedClassBytes {
        return NamedClassBytes(className,
            useClassLoader.getResourceAsStream(className.replace('.', '/') + ".class").use { it.readBytes() })
    }

    private fun File.getClassNames(): ArrayList<String> {
        val classNames = ArrayList<String>()

        FileInputStream(this).use { fis ->
            ZipInputStream(fis).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".class")) {
                        val className = entry.name.replace('/', '.')
                        classNames.add(className.substring(0, className.length - ".class".length))
                    }

                    entry = zip.nextEntry
                }
            }
        }

        return classNames
    }

}