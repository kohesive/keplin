package uy.kohesive.cuarentena

import uy.kohesive.cuarentena.ClassAllowanceDetector.scanClassByteCodeForDesiredAllowances
import uy.kohesive.cuarentena.KotlinPolicies.painlessBaseKotlinPolicy
import uy.kohesive.cuarentena.policy.CuarentenaPolicyLoader
import uy.kohesive.cuarentena.policy.PolicyAllowance


// TODO: this is from first round of exploring and work, needs overhauled to take those learnings into account, simplify
//       and develop the formal Kotlin whitelist, and then the lambda serializeable tiny whitelist.  More checking, more
//       eyes, continue to be more secure.

// TODO: replace all class, package, ... lists with Cuarentena policies

/*
 val kotinAllowedClasses = setOf("org.jetbrains.annotations.NotNull",
                "kotlin.jvm.internal.Intrinsics",
                "kotlin.jvm.internal.Lambda",
                "kotlin.Metadata",
                "kotlin.Unit",
                "kotlin.text.Regex",
                "kotlin.text.MatchResult",
                "kotlin.text.MatchGroupCollection",
                "kotlin.text.MatchGroup",
                "kotlin.TypeCastException",
                "kotlin.text.StringsKt",
                "org.jetbrains.annotations.Nullable",
                "[Ljava.lang.String;", // TODO: this needs other handling
                "java.util.regex.Pattern")  // TODO: we need a serializable white-list too

                val kotlinAllowedPackages = listOf("kotlin.collections.", "kotlin.jvm.functions.") // TODO: need specific list, these are not sealed
*/


class ClassRestrictionVerifier(val policies: Set<String> = painlessCombinedPolicy) {

    companion object {
        private val painlessBaseJavaPolicy = CuarentenaPolicyLoader.loadPolicy("painless-base-java")

        val painlessCombinedPolicy = painlessBaseJavaPolicy + painlessBaseKotlinPolicy
    }

    fun assertAllowance(allowance: PolicyAllowance) {
        allowance.asPolicyStrings().any { it in policies }
    }



    fun verifySafeClass(className: String, knownExtraAllowed: Set<String>, newClasses: List<NamedClassBytes>): VerifyResults {
        val x = scanClassByteCodeForDesiredAllowances(newClasses)
        return VerifyResults(false, emptySet())
        /*
        val readers = newClasses.map { ClassReader(it.bytes) }
        val verifier = VerifySafeClassVisitor(className, knownExtraAllowed)
        readers.forEach { reader ->
            reader.accept(verifier, 0)
        }
        return VerifyResults(verifier.scoreAccessed, verifier.violations)
        */
    }

    data class VerifyResults(val isScoreAccessed: Boolean, val violations: Set<String>) {
        val failed: Boolean = violations.isNotEmpty()
    }


}
