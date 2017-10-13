package uy.kohesive.cuarentena.kotlin

import uy.kohesive.cuarentena.Cuarentena
import uy.kohesive.cuarentena.NamedClassBytes
import uy.kohesive.cuarentena.policy.toPolicy
import uy.kohesive.keplin.util.ClassPathUtils
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

data class StdlibVerifyResult(
    val perClassViolations: List<Cuarentena.VerifyResultsPerClass>,
    val violationClasses: Set<String>,
    val verifiedClasses: List<String>
)

class KotlinStdlibPolicyGenerator {

    companion object {
        val BlackListedPackages = listOf(
            "kotlin.io",
            "kotlin.concurrent",
            "kotlin.coroutines",
            "kotlin.internal"
        )
    }

    fun verifyStdlibClasses(): StdlibVerifyResult {
        val cuarentena = Cuarentena.createKotlinBootstrapCuarentena()

        val stdLibClasses = ClassPathUtils.findKotlinStdLibJars().flatMap { kotlinStdLib ->
            kotlinStdLib.getClassNames().map { className ->
                classBytesForClass(className, Thread.currentThread().contextClassLoader)
            }
        }

        val perClassViolations = cuarentena.verifyClassAgainstPoliciesPerClass(stdLibClasses)
        val violationClasses   = perClassViolations.map { it.violatingClass.className }.toSet()

        return StdlibVerifyResult(
            perClassViolations = perClassViolations,
            violationClasses   = violationClasses,
            verifiedClasses    = stdLibClasses.map { it.className } - violationClasses
        )
    }

    fun generatePolicy(): List<String> =
        FullClassAllowancesGenerator().let { allowancesGenerator ->
            verifyStdlibClasses().verifiedClasses.map { verifiedClassName ->
                allowancesGenerator.generateAllowances(Class.forName(verifiedClassName))
            }.flatten().filter { allowance ->
                BlackListedPackages.none { blackListedPackage ->
                    allowance.fqnTarget.startsWith(blackListedPackage)
                }
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
