package uy.kohesive.cuarentena

import uy.kohesive.cuarentena.kotlin.KotlinStdlibPolicyGenerator

// Not really a test
// TODO: do a real test
fun main(args: Array<String>) {
    val verifyStdlibClasses = KotlinStdlibPolicyGenerator().verifyClasses()

    println("Verified classes:")
    verifyStdlibClasses.verifiedClasses.sorted().forEach { println(" + $it") }
    println()

    println("Black-listed classes:")
    verifyStdlibClasses.perClassViolations.sortedBy { it.violatingClass.className }.forEach {
        println(" - ${it.violatingClass.className}:")
        it.violations.forEach { println("   - $it") }
    }

    println()
    println("FINAL CLASSES ALLOWED")
    KotlinStdlibPolicyGenerator().determineValidClasses().sorted().forEach {
        println("  $it")
    }
}

fun main1(args: Array<String>) {
    Cuarentena.createKotlinCuarentena().policies.forEach { println(it) }
}