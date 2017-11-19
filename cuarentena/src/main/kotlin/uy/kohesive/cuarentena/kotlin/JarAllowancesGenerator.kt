package uy.kohesive.cuarentena.kotlin

import uy.kohesive.cuarentena.Cuarentena
import uy.kohesive.cuarentena.NamedClassBytes
import uy.kohesive.cuarentena.kotlin.KotlinStdlibPolicyGenerator.Companion.KotlinJarFiles
import uy.kohesive.cuarentena.policy.toPolicy
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

enum class ScanMode {
    ALL, SAFE
}

data class ClassesVerifyResult(
        val perClassViolations: List<Cuarentena.VerifyResultsPerClass>,
        val violationClasses: Set<String>,
        val verifiedClasses: Set<String>
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
        val preFilterPackageWhiteList: List<String>,
        val postFilterPackageWhiteList: List<String>,
        val postFilterPackageBlackList: List<String>,
        val postFilterClassBlackList: Set<String>,
        val useBootStrapPolicy: Boolean = false,
        val verbose: Boolean = false,
        val debugCallback: DebugCallback = DefaultDebugCallback
) {
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

    internal fun verifyClasses(): ClassesVerifyResult {
        return verifyClasses(listClassnamesFromJar())
    }

    fun verifyClasses(classNames: Set<String>): ClassesVerifyResult {
        val cuarentena = if (useBootStrapPolicy) {
            debug("Building kotlin whitelist for ${KotlinJarFiles.joinToString(", ")}")
            Cuarentena.createKotlinBootstrapCuarentena().apply {
                debug("Done processing kotlin")
            }
        } else {
            Cuarentena.createKotlinCuarentena()
        }

        val allClasses = classNames.map { className ->
                classBytesForClass(className, Thread.currentThread().contextClassLoader)
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
                verifiedClasses = classNames - violationClasses
        )
    }

    private fun String.ensureDot(): String {
        if (this.endsWith('.')) return this
        return this + '.'
    }

    private fun isAcceptableClass(className: String): Boolean {
        val whitelistPass = postFilterPackageWhiteList.isEmpty()
                || postFilterPackageWhiteList.any { className.startsWith(it.ensureDot()) }
        if (!whitelistPass || className in postFilterClassBlackList) return false
        return postFilterPackageBlackList.none { className.startsWith(it.ensureDot()) }
    }

    private fun isJarWhitelistedPackage(className: String): Boolean {
        return preFilterPackageWhiteList.isEmpty() || preFilterPackageWhiteList.any { className.startsWith(it.ensureDot()) }
    }

    private fun listClassnamesFromJar(): Set<String> {
        return jarFiles.map { File(it) }.flatMap { it.getClassNames() }.filter { isJarWhitelistedPackage(it) }.toSet()
    }

    internal fun determineValidClasses(): Set<String> {
        val jarClasses = listClassnamesFromJar()
        return when (scanMode) {
            ScanMode.ALL -> jarClasses
            ScanMode.SAFE -> verifyClasses(jarClasses).verifiedClasses
        }.filterTo(hashSetOf()) { isAcceptableClass(it) }
    }

    fun generatePolicy(): List<String> {
        val verifiedClassNames = determineValidClasses()

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