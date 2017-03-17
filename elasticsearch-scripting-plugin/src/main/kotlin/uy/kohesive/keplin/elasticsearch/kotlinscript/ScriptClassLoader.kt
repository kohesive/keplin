package uy.kohesive.keplin.elasticsearch.kotlinscript

class ScriptClassLoader(parent: ClassLoader) : ClassLoader(parent) {

    private val classes = hashMapOf<String, ByteArray>()

    private fun String.fixClassName() = this.replace('/', '.')

    @Throws(ClassNotFoundException::class)
    override fun findClass(name: String): Class<*> {
        val classBytes = classes.get(name.fixClassName())
        if (classBytes != null) {
            return defineClass(name, classBytes, 0, classBytes!!.size)
        } else {
            return super.findClass(name)
        }
    }

    fun addClass(className: String, bytes: ByteArray) {
        val oldBytes = classes.put(className.fixClassName(), bytes)
        if (oldBytes != null) {
            throw IllegalStateException("Rewrite at key " + className)
        }
    }
}
