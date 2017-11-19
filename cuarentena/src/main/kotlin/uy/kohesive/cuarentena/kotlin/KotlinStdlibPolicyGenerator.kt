package uy.kohesive.cuarentena.kotlin

import uy.kohesive.keplin.util.ClassPathUtils

class KotlinStdlibPolicyGenerator : JarAllowancesGenerator(
        // is this a problem, we find the compiler here instead of just stdlib, but 1.5x embedded compiler differs from
        // say 1.6x which doesn't
        jarFiles = KotlinJarFiles,
        preFilterPackageWhiteList = WhiteListedPrefixes,
        postFilterPackageWhiteList = WhiteListedPrefixes,
        postFilterClassBlackList = BlackListedClasses,
        postFilterPackageBlackList = BlackListedPackages,
    scanMode           = ScanMode.SAFE,
    useBootStrapPolicy = true
) {

    companion object {
        val BlackListedPackages = listOf(
            "kotlin.io",
            "kotlin.concurrent",
                "kotlin.coroutines"
        )

        val BlackListedClasses = emptySet<String>()

        val WhiteListedPrefixes = listOf(
                "kotlin"
        )

        val KotlinJarFiles = ClassPathUtils.findKotlinStdLibOrEmbeddedCompilerJars().map { it.path }
    }

}
