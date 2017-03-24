package uy.kohesive.cuarentena.policy

import org.junit.Test
import kotlin.test.assertEquals

class TestPolicyFormats {
    @Test
    fun testAllFormsOfPolicyItems() {
        val statements = listOf<PolicyAllowance>(
                PolicyAllowance.PackageAccess("com.test.safe.stuff", ALL_PACKAGE_ACCESS_TYPES),
                PolicyAllowance.PackageAccess("com.test.stuff", ALL_PACKAGE_ACCESS_TYPES, false),
                PolicyAllowance.ClassLevel.ClassAccess("com.test.stuff.SafeClass", ALL_CLASS_ACCESS_TYPES),
                PolicyAllowance.ClassLevel.ClassConstructorAccess("com.test.stuff.SafeClass", "()Lcom/test/stuff/SafeClass;", ALL_CLASS_CONSTUCTOR_ACCESS_TYPES),
                PolicyAllowance.ClassLevel.ClassFieldAccess("com.test.stuff.SafeClass", "fieldBool", "Z", ALL_CLASS_FIELD_ACCESS_TYPES),
                PolicyAllowance.ClassLevel.ClassMethodAccess("com.test.stuff.SafeClass", "foo", "(Ljava.lang.String;)Z", ALL_CLASS_METHOD_ACCESS_TYPES),
                PolicyAllowance.ClassLevel.ClassPropertyAccess("com.test.stuff.SafeClass", "proppy", "Ljava.lang.String;", ALL_CLASS_PROP_ACCESS_TYPES)
        )

        val expectedStrings = listOf<String>(
                "com.test.safe.stuff:sealed * call_Class_Constructor",
                "com.test.safe.stuff:sealed * call_Class_Instance_Method",
                "com.test.safe.stuff:sealed * call_Class_Static_Method",
                "com.test.safe.stuff:sealed * read_Class_Instance_Field",
                "com.test.safe.stuff:sealed * read_Class_Instance_Property",
                "com.test.safe.stuff:sealed * read_Class_Static_Field",
                "com.test.safe.stuff:sealed * read_Class_Static_Property",
                "com.test.safe.stuff:sealed * ref_Class",
                "com.test.safe.stuff:sealed * ref_Class_Instance",
                "com.test.safe.stuff:sealed * ref_Class_Static",
                "com.test.safe.stuff:sealed * write_Class_Instance_Field",
                "com.test.safe.stuff:sealed * write_Class_Instance_Property",
                "com.test.safe.stuff:sealed * write_Class_Static_Field",
                "com.test.safe.stuff:sealed * write_Class_Static_Property",
                "com.test.stuff * call_Class_Constructor",
                "com.test.stuff * call_Class_Instance_Method",
                "com.test.stuff * call_Class_Static_Method",
                "com.test.stuff * read_Class_Instance_Field",
                "com.test.stuff * read_Class_Instance_Property",
                "com.test.stuff * read_Class_Static_Field",
                "com.test.stuff * read_Class_Static_Property",
                "com.test.stuff * ref_Class",
                "com.test.stuff * ref_Class_Instance",
                "com.test.stuff * ref_Class_Static",
                "com.test.stuff * write_Class_Instance_Field",
                "com.test.stuff * write_Class_Instance_Property",
                "com.test.stuff * write_Class_Static_Field",
                "com.test.stuff * write_Class_Static_Property",
                "com.test.stuff SafeClass ref_Class",
                "com.test.stuff SafeClass ref_Class_Instance",
                "com.test.stuff SafeClass ref_Class_Static",
                "com.test.stuff SafeClass.<init>:()Lcom.test.stuff.SafeClass; call_Class_Constructor",
                "com.test.stuff SafeClass.@proppy:Ljava.lang.String; read_Class_Instance_Property",
                "com.test.stuff SafeClass.@proppy:Ljava.lang.String; read_Class_Static_Property",
                "com.test.stuff SafeClass.@proppy:Ljava.lang.String; write_Class_Instance_Property",
                "com.test.stuff SafeClass.@proppy:Ljava.lang.String; write_Class_Static_Property",
                "com.test.stuff SafeClass.fieldBool:Z read_Class_Instance_Field",
                "com.test.stuff SafeClass.fieldBool:Z read_Class_Static_Field",
                "com.test.stuff SafeClass.fieldBool:Z write_Class_Instance_Field",
                "com.test.stuff SafeClass.fieldBool:Z write_Class_Static_Field",
                "com.test.stuff SafeClass.foo(Ljava.lang.String;)Z call_Class_Instance_Method",
                "com.test.stuff SafeClass.foo(Ljava.lang.String;)Z call_Class_Static_Method"
        )

        val actualStrings = statements.toPolicy()

        println(actualStrings.map { "\"$it\"," }.joinToString("\n"))

        assertEquals(expectedStrings.joinToString("\n"), actualStrings.joinToString("\n"))
    }
}