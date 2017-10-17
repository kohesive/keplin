package uy.kohesive.cuarentena.kotlin

import uy.kohesive.cuarentena.Cuarentena
import uy.kohesive.cuarentena.NamedClassBytes
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
    val verifiedClasses: List<String>
)

open class JarAllowancesGenerator(
    val jarFiles: List<String>,
    val scanMode: ScanMode,
    val excludePackages: List<String>,
    val excludeClasses: List<String>,
    val useBootStrapPolicy: Boolean = false
) {

    constructor(jarFiles: List<String>, scanMode: String, excludePackages: List<String>, excludeClasses: List<String>)
        : this(jarFiles, ScanMode.valueOf(scanMode), excludePackages, excludeClasses)

    fun writePolicy(outputPath: String) {
        File(outputPath).bufferedWriter().use { writer ->
            generatePolicy().forEach {
                writer.write(it)
                writer.newLine()
            }
        }
    }

    fun verifyClasses(): ClassesVerifyResult {
        val cuarentena = if (useBootStrapPolicy) {
            Cuarentena.createKotlinBootstrapCuarentena()
        } else {
            Cuarentena.createKotlinCuarentena()
        }

        val allClasses = jarFiles.map { File(it) }.flatMap { jarFile ->
            jarFile.getClassNames().map { className ->
                classBytesForClass(className, Thread.currentThread().contextClassLoader)
            }
        }

        val perClassViolations = cuarentena.verifyClassAgainstPoliciesPerClass(allClasses)
        val violationClasses   = perClassViolations.map { it.violatingClass.className }.toSet()

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
            classAllowancesGenerator.generateAllowances(Class.forName(verifiedClassName))
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