package uy.kohesive.keplin.elasticsearch.kotlinscript

import org.apache.lucene.util.BytesRefBuilder
import org.apache.lucene.util.StringHelper
import java.io.*
import java.util.*

object ClassSerDesUtil {
    val BINARY_PREFIX = "keplinbin~~"
    val MARKER_SIG = "x9a0K1"
    val MARKER_VER = 1
    val SIG_SEED = 1331

    val otherAllowedSerializedClasses = ClassRestrictionVerifier.kotinAllowedClasses

    fun isPrefixedBase64(scriptSource: String): Boolean = scriptSource.startsWith(BINARY_PREFIX)

    fun deserFromPrefixedBase64(scriptSource: String): Triple<String, List<KotlinScriptEngineService.NamedClassBytes>, ByteArray> {
        if (!isPrefixedBase64(scriptSource)) throw ClassSerDesException("Script is not valid encoded classes")
        try {
            val rawData = scriptSource.substring(BINARY_PREFIX.length)
            val decodedBinary = Base64.getDecoder().decode(rawData)
            val content = DataInputStream(ByteArrayInputStream(decodedBinary)).use { stream ->
                val markerSig = stream.readString()
                val markerVer = stream.readInt()

                if (MARKER_SIG != markerSig || MARKER_VER != markerVer) {
                    throw ClassSerDesException("Serialized class has wrong signature or version, be sure client and server have matching versions")
                }

                val className = stream.readString()
                val classes = stream.readInt().let { count ->
                    (1..count).map {
                        val name = stream.readString()
                        val bytes = stream.readByteArray()
                        KotlinScriptEngineService.NamedClassBytes(name, bytes)
                    }
                }

                val serializedInstance = stream.readByteArray()

                val murmurSig = stream.readInt()

                val checkBytes = BytesRefBuilder().apply {
                    copyChars(className)
                    classes.forEach {
                        val nameAsBytes = it.className.toByteArray()
                        append(nameAsBytes, 0, nameAsBytes.size)
                        append(it.bytes, 0, it.bytes.size)
                    }
                    append(serializedInstance, 0, serializedInstance.size)
                }.toBytesRef()
                val calcSig = StringHelper.murmurhash3_x86_32(checkBytes, SIG_SEED)

                if (murmurSig != calcSig) throw ClassSerDesException("Serialized classes signature is not valid")
                Triple(className, classes, serializedInstance)
            }
            return content
        } catch (ex: Throwable) {
            if (ex is ClassSerDesException) throw ex
            throw ClassSerDesException(ex.message ?: "unknown error", ex)
        }
    }

    private fun DataInputStream.readString(): String = this.readUTF()
    private fun DataInputStream.readByteArray(): ByteArray {
        val bytesSize = readInt()
        val bytesBuffer = ByteArray(bytesSize)
        val bytesRead = read(bytesBuffer)
        if (bytesRead != bytesSize) throw ClassSerDesException("serialized bytes are wrong size within buffer")
        return bytesBuffer
    }

    private fun DataOutputStream.writeString(s: String) = this.writeUTF(s)
    private fun DataOutputStream.writeByteArray(b: ByteArray) {
        writeInt(b.size)
        write(b)
    }

    fun <T : Any?> serializeLambdaToBase64(lambda: EsKotlinScriptTemplate.() -> T): String {
        val serClass = lambda.javaClass
        val className = serClass.name

        val tracedClasses = mutableSetOf<Class<out Any>>()

        val serializedBytes = ByteArrayOutputStream().apply {
            TraceUsedClassesObjectOutputStream(serClass, this, tracedClasses).use { stream ->
                stream.writeObject(lambda)
            }
        }.toByteArray()

        val containingClass = className.substringBefore("$")

        fun validClassFilter(x: Class<out Any>): Boolean = x.name == className || x.name.startsWith("$className\$") || x.name == containingClass || x.name.startsWith("$containingClass\$") || x.name in ClassSerDesUtil.otherAllowedSerializedClasses

        val invalidClasses = tracedClasses.filterNot(::validClassFilter)

        if (invalidClasses.isNotEmpty()) {
            throw ClassSerDesException("Cannot serialize anything outside of your Lambda and its containing class, only primitives are allowed, illegal: ${invalidClasses.joinToString()}")
        }

        if (tracedClasses.none { it.name == className }) {
            throw ClassSerDesException("The Lambda didn't appear to serialize, it wasn't written to serialized data")
        }

        val classesAsBytes = tracedClasses.filterNot { it.name in otherAllowedSerializedClasses }.map { oneClass ->
            KotlinScriptEngineService.NamedClassBytes(oneClass.name,
                    serClass.classLoader.getResourceAsStream(oneClass.name.replace('.', '/') + ".class").use { it.readBytes() })
        }

        val verification = ClassRestrictionVerifier.verifySafeClass(className, emptySet(), classesAsBytes)
        if (verification.failed) {
            throw ClassSerDesException("The Lambda references invalid classes: ${verification.violations.joinToString()}")
        }

        val content = ByteArrayOutputStream().apply {
            DataOutputStream(this).use { stream ->
                stream.writeString(MARKER_SIG)
                stream.writeInt(MARKER_VER)

                stream.writeString(className)
                stream.writeInt(classesAsBytes.size)
                classesAsBytes.forEach {
                    stream.writeString(it.className)
                    stream.writeByteArray(it.bytes)
                }

                stream.writeByteArray(serializedBytes)

                val checkBytes = BytesRefBuilder().apply {
                    copyChars(className)
                    classesAsBytes.forEach {
                        val nameAsBytes = it.className.toByteArray()
                        append(nameAsBytes, 0, nameAsBytes.size)
                        append(it.bytes, 0, it.bytes.size)
                    }
                    append(serializedBytes, 0, serializedBytes.size)
                }.toBytesRef()
                val calcSig = StringHelper.murmurhash3_x86_32(checkBytes, SIG_SEED)

                stream.writeInt(calcSig)
            }
        }.toByteArray()

        val encodedBinary = Base64.getEncoder().encodeToString(content)
        return BINARY_PREFIX + encodedBinary
    }

    @Suppress("UNCHECKED_CAST")
    fun deserLambdaInstanceSafely(className: String, serBytes: ByteArray, allowedClassNames: Set<String>): EsKotlinScriptTemplate.() -> Any? {
        val tracer = RestrictUsedClassesObjectInputStream(className, allowedClassNames, ByteArrayInputStream(serBytes))
        return tracer.use { stream ->
            stream.readObject()
        } as EsKotlinScriptTemplate.() -> Any?
    }

    class RestrictUsedClassesObjectInputStream(val className: String, val allowedClassNames: Set<String>, input: InputStream) : ObjectInputStream(input) {
        override fun readClassDescriptor(): ObjectStreamClass {
            val temp = super.readClassDescriptor()
            val verify = ClassRestrictionVerifier.verifySafeClassForDeser(className, allowedClassNames, temp.name)
            if (verify.failed) throw IllegalStateException("Invalid class ${temp.name} not allowed for Kotlin Script Lambda deserialization")
            return temp
        } // called first:  we can check the type name here, and the fields it has with their name + signatures /// kotlin.jvm.internal.Lambda
    }

    class TraceUsedClassesObjectOutputStream(startingClass: Class<*>, output: OutputStream, val classes: MutableSet<Class<out Any>> = mutableSetOf()) : ObjectOutputStream(output) {
        // TODO: don't need this method?
        override fun annotateClass(cl: Class<*>) {
            classes.add(cl)
            super.annotateClass(cl)
        }

        // TODO: don't need this method?
        override fun annotateProxyClass(cl: Class<*>) {
            classes.add(cl)
            super.annotateProxyClass(cl)
        }

        override fun writeClassDescriptor(desc: ObjectStreamClass) {
            desc.forClass()?.let { classes.add(it) }
            super.writeClassDescriptor(desc)
        }
    }

    class ClassSerDesException(msg: String, cause: Throwable? = null) : Exception(msg, cause)
}

