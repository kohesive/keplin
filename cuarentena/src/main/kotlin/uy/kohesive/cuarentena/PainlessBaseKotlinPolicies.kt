package uy.kohesive.cuarentena

import uy.kohesive.cuarentena.policy.AccessTypes
import uy.kohesive.cuarentena.policy.PolicyAllowance
import uy.kohesive.cuarentena.policy.toPolicy

object KotlinPolicies {
    // TODO: move to a policy file/generator outside of here for Kotlin safe calls
    val painlessBaseKotlinPolicy = (listOf(
            // java.lang.StringBuilder
            PolicyAllowance.ClassLevel.ClassMethodAccess("java.lang.StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", setOf(AccessTypes.call_Class_Instance_Method)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("java.lang.StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", setOf(AccessTypes.call_Class_Instance_Method)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("java.lang.StringBuilder", "append", "(J)Ljava/lang/StringBuilder;", setOf(AccessTypes.call_Class_Instance_Method)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("java.lang.StringBuilder", "append", "(C)Ljava/lang/StringBuilder;", setOf(AccessTypes.call_Class_Instance_Method)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("java.lang.StringBuilder", "append", "(D)Ljava/lang/StringBuilder;", setOf(AccessTypes.call_Class_Instance_Method)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("java.lang.StringBuilder", "append", "(F)Ljava/lang/StringBuilder;", setOf(AccessTypes.call_Class_Instance_Method)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("java.lang.StringBuilder", "append", "(B)Ljava/lang/StringBuilder;", setOf(AccessTypes.call_Class_Instance_Method)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("java.lang.StringBuilder", "append", "(S)Ljava/lang/StringBuilder;", setOf(AccessTypes.call_Class_Instance_Method)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("java.lang.StringBuilder", "append", "(Z)Ljava/lang/StringBuilder;", setOf(AccessTypes.call_Class_Instance_Method)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("java.lang.StringBuilder", "toString", "()Ljava/lang/String;", setOf(AccessTypes.call_Class_Instance_Method)),

            // java.lang.Integer
            PolicyAllowance.ClassLevel.ClassMethodAccess("java.lang.Integer", "valueOf", "(I)Ljava/lang/Integer;", setOf(AccessTypes.call_Class_Static_Method)),

            // java.util.ArrayList
            PolicyAllowance.ClassLevel.ClassConstructorAccess("java.util.ArrayList", "(I)Ljava/util/ArrayList;", setOf(AccessTypes.call_Class_Constructor)),

            // java.util.regex
            PolicyAllowance.ClassLevel.ClassMethodAccess("java.util.regex.Pattern", "compile", "(Ljava/lang/String;I)Ljava/util/regex/Pattern;", setOf(AccessTypes.call_Class_Static_Method)),

            // kotlin.text
            PolicyAllowance.ClassLevel.ClassConstructorAccess("kotlin.text.Regex", "(Ljava/lang/String;)Lkotlin/text/Regex;", setOf(AccessTypes.call_Class_Constructor)),
            PolicyAllowance.ClassLevel.ClassConstructorAccess("kotlin.text.Regex", "(Ljava.util.regex.Pattern;)Lkotlin.text.Regex;", setOf(AccessTypes.call_Class_Constructor)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("kotlin.text.Regex", "matchEntire", "(Ljava.lang.CharSequence;)Lkotlin.text.MatchResult;", setOf(AccessTypes.call_Class_Instance_Method)),

            PolicyAllowance.ClassLevel.ClassAccess("kotlin.text.MatchGroup", setOf(AccessTypes.ref_Class_Instance)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("kotlin.text.MatchGroup", "getValue", "()Ljava.lang.String;", setOf(AccessTypes.call_Class_Instance_Method)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("kotlin.text.MatchGroup", "get", "(I)Lkotlin.text.MatchGroup;", setOf(AccessTypes.call_Class_Instance_Method)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("kotlin.text.MatchGroup", "size", "()I", setOf(AccessTypes.call_Class_Instance_Method)),

            PolicyAllowance.ClassLevel.ClassAccess("kotlin.text.MatchGroupCollection", setOf(AccessTypes.ref_Class_Instance)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("kotlin.text.MatchGroupCollection", "size", "()I", setOf(AccessTypes.call_Class_Instance_Method)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("kotlin.text.MatchGroupCollection", "get", "(I)Lkotlin.text.MatchGroup;", setOf(AccessTypes.call_Class_Instance_Method)),

            PolicyAllowance.ClassLevel.ClassAccess("kotlin.text.MatchResult", setOf(AccessTypes.ref_Class_Instance)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("kotlin.text.MatchResult", "getGroups", "()Lkotlin.text.MatchGroupCollection;", setOf(AccessTypes.call_Class_Instance_Method)),

            PolicyAllowance.ClassLevel.ClassMethodAccess("kotlin.text.StringsKt", "split\$default", "(Ljava.lang.CharSequence;[CZIILjava.lang.Object;)Ljava.util.List;", setOf(AccessTypes.call_Class_Static_Method)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("kotlin.text.StringsKt", "trim", "(Ljava.lang.CharSequence;)Ljava.lang.CharSequence;", setOf(AccessTypes.call_Class_Static_Method)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("kotlin.text.StringsKt", "isBlank", "(Ljava.lang.CharSequence;)Z", setOf(AccessTypes.call_Class_Static_Method)),


            // kotlin misc
            PolicyAllowance.ClassLevel.ClassAccess("kotlin.Metadata", setOf(AccessTypes.ref_Class)),
            PolicyAllowance.ClassLevel.ClassAccess("kotlin.TypeCastException", setOf(AccessTypes.ref_Class)),
            PolicyAllowance.ClassLevel.ClassConstructorAccess("kotlin.TypeCastException", "(Ljava/lang/String;)Lkotlin/TypeCastException;", setOf(AccessTypes.call_Class_Constructor)),
            PolicyAllowance.ClassLevel.ClassAccess("org.jetbrains.annotations.NotNull", setOf(AccessTypes.ref_Class)),
            PolicyAllowance.ClassLevel.ClassAccess("org.jetbrains.annotations.Nullable", setOf(AccessTypes.ref_Class)),

            // kotlin.collections
            PolicyAllowance.ClassLevel.ClassMethodAccess("kotlin.collections.CollectionsKt", "emptyList", "()Ljava/util/List;", setOf(AccessTypes.call_Class_Static_Method)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("kotlin.collections.CollectionsKt", "listOf", "(Ljava/lang/Object;)Ljava/util/List;", setOf(AccessTypes.call_Class_Static_Method)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("kotlin.collections.CollectionsKt", "listOf", "([Ljava/lang/Object;)Ljava/util/List;", setOf(AccessTypes.call_Class_Static_Method)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("kotlin.collections.CollectionsKt", "plus", "(Ljava/util/Collection;Ljava/lang/Iterable;)Ljava/util/List;", setOf(AccessTypes.call_Class_Static_Method)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("kotlin.collections.CollectionsKt", "collectionSizeOrDefault", "(Ljava.lang.Iterable;I)I", setOf(AccessTypes.call_Class_Static_Method)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("kotlin.collections.CollectionsKt", "filterNotNull", "(Ljava.lang.Iterable;)Ljava.util.List;", setOf(AccessTypes.call_Class_Static_Method)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("kotlin.collections.CollectionsKt", "flatten", "(Ljava.lang.Iterable;)Ljava.util.List;", setOf(AccessTypes.call_Class_Static_Method)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("kotlin.collections.CollectionsKt", "single", "(Ljava.util.List;)Ljava.lang.Object;", setOf(AccessTypes.call_Class_Static_Method)),

            PolicyAllowance.ClassLevel.ClassMethodAccess("kotlin.collections.SetsKt", "emptySet", "()Ljava/util/Set;", setOf(AccessTypes.call_Class_Static_Method)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("kotlin.collections.SetsKt", "setOf", "(Ljava/lang/Object;)Ljava/util/Set;", setOf(AccessTypes.call_Class_Static_Method)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("kotlin.collections.SetsKt", "setOf", "([Ljava/lang/Object;)Ljava/util/Set;", setOf(AccessTypes.call_Class_Static_Method)),

            // kotlin.jvm
            PolicyAllowance.ClassLevel.ClassAccess("kotlin.jvm.functions.Function", setOf(AccessTypes.ref_Class)),
            PolicyAllowance.ClassLevel.ClassAccess("kotlin.jvm.internal.Lambda", setOf(AccessTypes.ref_Class_Instance)),
            PolicyAllowance.ClassLevel.ClassConstructorAccess("kotlin.jvm.internal.Lambda", "(I)Lkotlin.jvm.internal.Lambda;", setOf(AccessTypes.call_Class_Constructor)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("kotlin.jvm.internal.Intrinsics", "checkExpressionValueIsNotNull", "(Ljava/lang/Object;Ljava/lang/String;)V", setOf(AccessTypes.call_Class_Static_Method)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("kotlin.jvm.internal.Intrinsics", "checkParameterIsNotNull", "(Ljava/lang/Object;Ljava/lang/String;)V", setOf(AccessTypes.call_Class_Static_Method)),
            PolicyAllowance.ClassLevel.ClassMethodAccess("kotlin.jvm.internal.Intrinsics", "throwNpe", "()V", setOf(AccessTypes.call_Class_Static_Method)),

            // kotlin.Unit
            PolicyAllowance.ClassLevel.ClassFieldAccess("kotlin.Unit", "INSTANCE", "Lkotlin/Unit;", setOf(AccessTypes.read_Class_Static_Field))
    ) + (0..22).map { idx -> PolicyAllowance.ClassLevel.ClassAccess("kotlin.jvm.functions.Function$idx", setOf(AccessTypes.ref_Class)) }
            ).toPolicy()

    private fun safeCodeByExample(): List<PolicyAllowance.ClassLevel> {
        val outsidePrimitive = 22

        val classBytes = lambdaToBytes {
            val x = 10 + outsidePrimitive

            emptyList<String>()
            listOf("a")
            listOf("a", "b")
            listOf(1, 2, 3)

            emptySet<String>()
            setOf("a")
            setOf("a", "b")
            setOf(1, 2, 3)

            emptySequence<String>()
            sequenceOf("a")
            sequenceOf("a", "b")
            sequenceOf(1, 2, 3)

            emptyArray<String>()
            arrayOf("a")
            arrayOf(1)
            arrayOf(1L)
            arrayOf(1.0)
            arrayOf(true)
            arrayOf(1, 2, 3)
            arrayOf(1L, 2L, 3L)
            arrayOf(1.0, 2.0, 3.0)
            arrayOf(true, false)


            val lists = listOf("a") + listOf("b") + listOf("c", "d", "e") + listOf("z")

            val s = "stringy $x is next to these $lists"
            val s2 = """$s what $s"""

            val r = """[\w\d]+""".toRegex()
            val p = """[\w\d]+""".toPattern()

            p.toRegex().matches("a99")
            r.matchEntire("asd")?.apply {
                this.groupValues
                this.groups
                this.destructured
                this.value
            }

            "string".takeIf { true }.also { }.takeUnless { false }

            val snullable: String? = ""
            if (snullable != null) {
            } else {
            }
            val snonullable = snullable ?: ""

            true.and(true).or(true).xor(true).not()
        }

        val goodThings = ClassAllowanceDetector.scanClassByteCodeForDesiredAllowances(listOf(classBytes))
        println("===[ sniffed from safe code ]===")
        goodThings.allowances.toPolicy().forEach(::println)
        return goodThings.allowances
    }

    private fun lambdaToBytes(lambda: () -> Unit): NamedClassBytes {
        val serClass = lambda.javaClass
        val className = serClass.name

        return loadClassAsBytes(className, serClass.classLoader)
    }

    private fun loadClassAsBytes(className: String, loader: ClassLoader = Thread.currentThread().contextClassLoader): NamedClassBytes {
        return NamedClassBytes(className,
                loader.getResourceAsStream(className.replace('.', '/') + ".class").use { it.readBytes() })
    }
}