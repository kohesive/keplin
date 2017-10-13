package uy.kohesive.cuarentena.kotlin

import uy.kohesive.cuarentena.policy.*

fun main(args: Array<String>) {
    FullClassAllowancesGenerator().generateAllowances(Class.forName("kotlin.Unit")).toPolicy().forEach {
        println(it)
    }
}

class FullClassAllowancesGenerator {

    fun generateAllowances(clazz: Class<*>): AccessPolicies {
        val currentClassName = clazz.name

        // Constructors
        val constructorAllowances = clazz.declaredConstructors
            .filter { java.lang.reflect.Modifier.isPublic(it.modifiers) }
            .map { constructor: java.lang.reflect.Constructor<*> ->
                PolicyAllowance.ClassLevel.ClassConstructorAccess(currentClassName, constructor.signature(), setOf(AccessTypes.call_Class_Constructor))
            }

        // Methods
        val methodAllowances = clazz.methods
            .filter { java.lang.reflect.Modifier.isPublic(it.modifiers) }
            .map { method ->
                val access = if (java.lang.reflect.Modifier.isStatic(method.modifiers)) uy.kohesive.cuarentena.policy.AccessTypes.call_Class_Static_Method
                else uy.kohesive.cuarentena.policy.AccessTypes.call_Class_Instance_Method
                val signature = method.signature()

                signature to PolicyAllowance.ClassLevel.ClassMethodAccess(currentClassName, method.name, signature, setOf(access))
            }.distinctBy { it.first }.map { it.second }

        // Fields
        val fieldsAllowances = clazz.fields
            .filter { java.lang.reflect.Modifier.isPublic(it.modifiers) }
            .map { field ->
                val access = when {
                    java.lang.reflect.Modifier.isStatic(field.modifiers) && java.lang.reflect.Modifier.isFinal(field.modifiers) -> setOf(uy.kohesive.cuarentena.policy.AccessTypes.read_Class_Static_Field)
                    java.lang.reflect.Modifier.isStatic(field.modifiers) -> setOf(uy.kohesive.cuarentena.policy.AccessTypes.read_Class_Static_Field, uy.kohesive.cuarentena.policy.AccessTypes.write_Class_Static_Field)
                    java.lang.reflect.Modifier.isFinal(field.modifiers) -> setOf(uy.kohesive.cuarentena.policy.AccessTypes.read_Class_Instance_Field)
                    else -> setOf(uy.kohesive.cuarentena.policy.AccessTypes.read_Class_Instance_Field, uy.kohesive.cuarentena.policy.AccessTypes.write_Class_Instance_Field)
                }
                val signature = field.signature()

                PolicyAllowance.ClassLevel.ClassFieldAccess(currentClassName, field.name, signature, access)
            }

        return constructorAllowances + methodAllowances + fieldsAllowances
    }

}