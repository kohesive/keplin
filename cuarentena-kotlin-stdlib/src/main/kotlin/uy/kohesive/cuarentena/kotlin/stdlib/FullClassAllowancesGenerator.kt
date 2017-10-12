package uy.kohesive.cuarentena.kotlin.stdlib

import uy.kohesive.cuarentena.policy.AccessPolicies
import uy.kohesive.cuarentena.policy.AccessTypes
import uy.kohesive.cuarentena.policy.PolicyAllowance
import uy.kohesive.cuarentena.policy.*
import java.lang.reflect.*

class FullClassAllowancesGenerator {

    fun generateAllowances(clazz: Class<*>): AccessPolicies {
        val currentClassName = clazz.name

        // Constructors
        val constructorAllowances = clazz.declaredConstructors
            .filter { Modifier.isPublic(it.modifiers) }
            .map { constructor: Constructor<*> ->
                PolicyAllowance.ClassLevel.ClassConstructorAccess(currentClassName, constructor.signature(), setOf(AccessTypes.call_Class_Constructor))
            }

        // Methods
        val methodAllowances = clazz.methods
            .filter { Modifier.isPublic(it.modifiers) }
            .map { method ->
                val access = if (Modifier.isStatic(method.modifiers)) AccessTypes.call_Class_Static_Method
                else AccessTypes.call_Class_Instance_Method
                val signature = method.signature()

                signature to PolicyAllowance.ClassLevel.ClassMethodAccess(currentClassName, method.name, signature, setOf(access))
            }.distinctBy { it.first }.map { it.second }

        // Fields
        val fieldsAllowances = clazz.fields
            .filter { Modifier.isPublic(it.modifiers) }
            .map { field ->
                val access = when {
                    Modifier.isStatic(field.modifiers) && Modifier.isFinal(field.modifiers) -> setOf(AccessTypes.read_Class_Static_Field)
                    Modifier.isStatic(field.modifiers) -> setOf(AccessTypes.read_Class_Static_Field, AccessTypes.write_Class_Static_Field)
                    Modifier.isFinal(field.modifiers) -> setOf(AccessTypes.read_Class_Instance_Field)
                    else -> setOf(AccessTypes.read_Class_Instance_Field, AccessTypes.write_Class_Instance_Field)
                }
                val signature = field.signature()

                PolicyAllowance.ClassLevel.ClassFieldAccess(currentClassName, field.name, signature, access)
            }

        return constructorAllowances + methodAllowances + fieldsAllowances
    }

}