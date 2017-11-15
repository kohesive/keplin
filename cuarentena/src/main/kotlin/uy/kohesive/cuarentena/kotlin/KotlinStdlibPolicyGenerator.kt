package uy.kohesive.cuarentena.kotlin

import uy.kohesive.keplin.util.ClassPathUtils

class KotlinStdlibPolicyGenerator : JarAllowancesGenerator(
    jarFiles           = ClassPathUtils.findKotlinStdLibJars().map { it.path },
    excludeClasses     = emptyList(),
    excludePackages    = BlackListedPackages,
    scanMode           = ScanMode.SAFE,
    useBootStrapPolicy = true
) {

    companion object {
        val BlackListedPackages = listOf(
            "kotlin.io",
            "kotlin.concurrent",
            "kotlin.coroutines",
            "kotlin.internal"
        )
    }

}
