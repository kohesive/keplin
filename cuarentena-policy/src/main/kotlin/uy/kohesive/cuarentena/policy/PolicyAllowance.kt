package uy.kohesive.cuarentena.policy

typealias AccessPolicies = List<PolicyAllowance>

sealed class PolicyAllowance(val fqnTarget: String, val actions: Set<AccessTypes>, val validActions: Set<AccessTypes>) {
    init {
        if (actions.any { it !in validActions }) {
            throw IllegalArgumentException("Requested actions $actions are not valid for ${this::class.qualifiedName!!} ($validActions)")
        }
    }

    abstract fun asPolicyStrings(): List<String>

    protected fun Set<AccessTypes>.asStrings() = this.map { it.name }.sorted()
    protected fun List<AccessTypes>.asStrings() = this.map { it.name }.sorted()

    protected fun Set<AccessTypes>.addDefaultClassActions(): Set<AccessTypes> {
        return this + if (this.any { it in STATIC_CLASS_ACCESS_TYPES }) listOf(AccessTypes.ref_Class_Static) else emptyList<AccessTypes>() +
                if (this.any { it in INSTANCE_CLASS_ACCESS_TYPES }) listOf(AccessTypes.ref_Class_Instance) else emptyList<AccessTypes>()
    }

    class PackageAccess(fqPackageName: String, actions: Set<AccessTypes>, val requireSealed: Boolean = true) : PolicyAllowance(fqPackageName, actions, ALL_PACKAGE_ACCESS_TYPES) {
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
            return (list1 + list2).distinct()
        }

        protected abstract fun sigPart(): String

        class ClassAccess(fqClassName: String, actions: Set<AccessTypes>) : ClassLevel(fqClassName, actions, ALL_CLASS_ACCESS_TYPES) {
            override fun sigPart(): String = ""
        }

        class ClassMethodAccess(fqClassName: String, val methodName: String, val methodSig: String, actions: Set<AccessTypes>) : ClassLevel(fqClassName, actions, ALL_CLASS_METHOD_ACCESS_TYPES) {
            override fun sigPart(): String = "$methodName$methodSig"
        }

        class ClassConstructorAccess(fqClassName: String, val constructorSig: String, actions: Set<AccessTypes>) : ClassLevel(fqClassName, actions, ALL_CLASS_CONSTUCTOR_ACCESS_TYPES) {
            override fun sigPart(): String = "<init>:$constructorSig"
        }

        class ClassFieldAccess(fqClassName: String, val fieldName: String, val fieldTypeSig: String, actions: Set<AccessTypes>) : ClassLevel(fqClassName, actions, ALL_CLASS_FIELD_ACCESS_TYPES) {
            override fun sigPart(): String = "$fieldName:$fieldTypeSig"
        }

        class ClassPropertyAccess(fqClassName: String, val propertyName: String, val propertyTypeSig: String, actions: Set<AccessTypes>) : ClassLevel(fqClassName, actions, ALL_CLASS_PROP_ACCESS_TYPES) {
            override fun sigPart(): String = "@$propertyName:$propertyTypeSig"
        }
    }
}

