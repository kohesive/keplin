package uy.kohesive.cuarentena.policy

typealias AccessPolicies = List<PolicyAllowance>

sealed class PolicyAllowance(_fqnTarget: String, val actions: Set<AccessTypes>, val validActions: Set<AccessTypes>) {
    val fqnTarget: String = _fqnTarget.replace('/', '.')
    init {
        if (actions.any { it !in validActions }) {
            throw IllegalArgumentException("Requested actions $actions are not valid for ${this::class.qualifiedName!!} ($validActions)")
        }
    }

    abstract fun asPolicyStrings(): List<String>

    class PackageAccess(fqPackageName: String, actions: Set<AccessTypes>, val requireSealed: Boolean = true) : PolicyAllowance(fqPackageName, actions, ALL_PACKAGE_ACCESS_TYPES) {
        init {
            TODO("DISABLED")
        }

        override fun asPolicyStrings(): List<String> {
            val packageId = fqnTarget + if (requireSealed) ":sealed" else ""
            return actions.addDefaultClassActions().asStrings().map { "$packageId * $it" }
        }
    }

    sealed class ClassLevel(fqClassName: String, actions: Set<AccessTypes>, validActions: Set<AccessTypes>) : PolicyAllowance(fqClassName, actions, validActions) {
        override fun asPolicyStrings(): List<String> {
            val packageId = fqnTarget.substringBeforeLast('.')
            val classId = fqnTarget.substringAfterLast('.')
            val sig = sigPart()

            val list1 = actions.addDefaultClassActions().filter { it in ALL_CLASS_ACCESS_TYPES }
                    .asStrings()
                    .map { access -> "$packageId $classId $access" }
            val list2 = actions.filterNot { it in ALL_CLASS_ACCESS_TYPES }
                    .asStrings()
                    .map { access -> "$packageId $classId.$sig $access" }
            return (list1 + list2).toSet().toList()
        }

        protected abstract fun sigPart(): String

        class ClassAccess(fqClassName: String, actions: Set<AccessTypes>) : ClassLevel(fqClassName, actions, ALL_CLASS_ACCESS_TYPES) {
            override fun sigPart(): String = ""
        }

        class ClassMethodAccess(fqClassName: String, val methodName: String, _methodSig: String, actions: Set<AccessTypes>) : ClassLevel(fqClassName, actions, ALL_CLASS_METHOD_ACCESS_TYPES) {
            val methodSig: String = _methodSig.replace('.', '/')
            override fun sigPart(): String = "$methodName$methodSig"
        }

        class ClassConstructorAccess(fqClassName: String, _constructorSig: String, actions: Set<AccessTypes>) : ClassLevel(fqClassName, actions, ALL_CLASS_CONSTUCTOR_ACCESS_TYPES) {
            val constructorSig: String = _constructorSig.replace('.', '/')
            override fun sigPart(): String = "<init>:${constructorSig.substringBefore(')') + ")L$fqnTarget;"}"
        }

        class ClassFieldAccess(fqClassName: String, val fieldName: String, _fieldTypeSig: String, actions: Set<AccessTypes>) : ClassLevel(fqClassName, actions, ALL_CLASS_FIELD_ACCESS_TYPES) {
            val fieldTypeSig: String = _fieldTypeSig.replace('.', '/')
            override fun sigPart(): String = "$fieldName:$fieldTypeSig"
        }

        class ClassPropertyAccess(fqClassName: String, val propertyName: String, _propertyTypeSig: String, actions: Set<AccessTypes>) : ClassLevel(fqClassName, actions, ALL_CLASS_PROP_ACCESS_TYPES) {
            val propertyTypeSig: String = _propertyTypeSig.replace('.', '/')
            override fun sigPart(): String = "@$propertyName:$propertyTypeSig"
        }
    }
}

