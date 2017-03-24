package uy.kohesive.cuarentena

import uy.kohesive.cuarentena.ClassAllowanceDetector.scanClassByteCodeForDesiredAllowances
import uy.kohesive.cuarentena.KotlinPolicies.painlessBaseKotlinPolicy
import uy.kohesive.cuarentena.policy.ALL_CLASS_ACCESS_TYPES
import uy.kohesive.cuarentena.policy.AccessTypes
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


class Cuarentena(val policies: Set<String> = painlessCombinedPolicy) {
    companion object {
        private val painlessBaseJavaPolicy = CuarentenaPolicyLoader.loadPolicy("painless-base-java")

        val painlessCombinedPolicy = painlessBaseJavaPolicy + painlessBaseKotlinPolicy
    }

    fun verifyClassAgainstPolicies(newClasses: List<NamedClassBytes>, additionalPolicies: Set<String> = emptySet()): VerifyResults {
        val filteredClasses = filterKnownClasses(newClasses, additionalPolicies)
        val classScanResults = scanClassByteCodeForDesiredAllowances(filteredClasses)

        val filteredClassNames = filteredClasses.map { it.className }.toSet()

        val violations = classScanResults.allowances
                .filterNot {
                    // new classes can call themselves, so these can't be violations
                    it.fqnTarget in filteredClassNames
                }.filterNot { it.assertAllowance(additionalPolicies) }

        val violationStrings = violations.map { it.resultingViolations(additionalPolicies) }.flatten().toSet()

        return VerifyResults(classScanResults, violationStrings, filteredClasses)
    }

    fun verifyClassNamesAgainstPolicies(classesToCheck: List<String>, additionalPolicies: Set<String> = emptySet()): VerifyNameResults {
        val violations = classesToCheck.map { PolicyAllowance.ClassLevel.ClassAccess(it, setOf(AccessTypes.ref_Class_Instance)) }
                .filterNot { it.assertAllowance(additionalPolicies) }
        val violationStrings = violations.map { it.resultingViolations(additionalPolicies) }.flatten().toSet()
        return VerifyNameResults(violationStrings)
    }

    data class VerifyResults(val scanResults: ClassAllowanceDetector.ScanState, val violations: Set<String>, val filteredClasses: List<NamedClassBytes>) {
        val failed: Boolean = violations.isNotEmpty()

        fun violationsAsString() = violations.joinToString()
    }

    data class VerifyNameResults(val violations: Set<String>) {
        val failed: Boolean = violations.isNotEmpty()

        fun violationsAsString() = violations.joinToString()
    }

    fun PolicyAllowance.assertAllowance(additionalPolicies: Set<String> = emptySet()): Boolean
            = this.asCheckStrings(true).any { it in policies || it in additionalPolicies }

    fun PolicyAllowance.resultingViolations(additionalPolicies: Set<String> = emptySet()): List<String>
            = this.asCheckStrings(false).filterNot { it in policies || it in additionalPolicies }

    /*
        Remove known classes from the policy from the captured class bytes, because they are not expected to be shipped
     */
    fun filterKnownClasses(newClasses: List<NamedClassBytes>, additionalPolicies: Set<String>): List<NamedClassBytes> {
        return newClasses.filterNot { PolicyAllowance.ClassLevel.ClassAccess(it.className, ALL_CLASS_ACCESS_TYPES).assertAllowance(additionalPolicies) }
    }
}
