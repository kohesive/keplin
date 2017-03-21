package uy.kohesive.chillamda

import com.zackehh.siphash.SipHashCase
import com.zackehh.siphash.SipHashDigest
import uy.kohesive.cuarentena.ClassRestrictionVerifier
import uy.kohesive.cuarentena.NamedClassBytes
import java.io.*
import java.util.*
import kotlin.reflect.KClass


object ClassSerDesUtil {
    val BINARY_PREFIX = "chilambda~~"
    val MARKER_SIG = "x9a0K1"
    val MARKER_VER = 1
    val SIG_SEED = "ChillWitMeLambda"

    val otherAllowedSerializedClasses = ClassRestrictionVerifier.kotinAllowedClasses

    val verifier = ClassRestrictionVerifier(emptySet(), emptySet())

    fun isPrefixedBase64(scriptSource: String): Boolean = scriptSource.startsWith(BINARY_PREFIX)

    fun SipHashDigest.update(s: String): SipHashDigest = this.update(s.toByteArray())

    data class DeserResponse(val className: String, val classes: List<NamedClassBytes>, val serializedLambda: ByteArray)

    inline fun <reified R : Any, reified T : Any> deserFromPrefixedBase64(scriptSource: String): DeserResponse {
        return deserFromPrefixedBase64(R::class, T::class, scriptSource)
    }

    fun <R : Any, T : Any> deserFromPrefixedBase64(lambdaReceiver: KClass<R>, lambdaReturnType: KClass<T>, scriptSource: String): DeserResponse {
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
                        NamedClassBytes(name, bytes)
                    }
                }

                val serializedInstance = stream.readByteArray()

                val sentSig = stream.readString()

                val digest = SipHashDigest(SIG_SEED.toByteArray())
                digest.update(className)
                classes.forEach {
                    digest.update(it.className)
                    digest.update(it.bytes)
                }
                digest.update(serializedInstance)
                val calcSig = digest.finish().getHex(true, SipHashCase.UPPER)

                if (sentSig != calcSig) throw ClassSerDesException("Serialized classes signature is not valid")
                DeserResponse(className, classes, serializedInstance)
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

    inline fun <reified R : Any, reified T : Any> serializeLambdaToBase64(noinline lambda: R.() -> T?): String {
        return serializeLambdaToBase64(R::class, T::class, lambda)
    }

    // TODO: handle types with generics
    fun <R : Any, T : Any> serializeLambdaToBase64(lambdaReceiver: KClass<R>, lambdaReturnType: KClass<T>, lambda: R.() -> T?): String {
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
            NamedClassBytes(oneClass.name,
                    serClass.classLoader.getResourceAsStream(oneClass.name.replace('.', '/') + ".class").use { it.readBytes() })
        }

        val verification = verifier.verifySafeClass(className, setOf(lambdaReceiver.java.name, lambdaReturnType.java.name), classesAsBytes)
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

                val digest = SipHashDigest(SIG_SEED.toByteArray())
                digest.update(className)
                    classesAsBytes.forEach {
                        digest.update(it.className)
                        digest.update(it.bytes)
                    }
                digest.update(serializedBytes)

                val calcSig = digest.finish().getHex(true, SipHashCase.UPPER)
                stream.writeString(calcSig)
            }
        }.toByteArray()

        val encodedBinary = Base64.getEncoder().encodeToString(content)
        return BINARY_PREFIX + encodedBinary
    }

    inline fun <reified R : Any, reified T : Any> deserLambdaInstanceSafely(className: String, serBytes: ByteArray, allowedClassNames: Set<String>): R.() -> T? {
        return deserLambdaInstanceSafely(R::class, T::class, className, serBytes, allowedClassNames)
    }

    @Suppress("UNCHECKED_CAST")
    fun <R : Any, T : Any> deserLambdaInstanceSafely(lambdaReceiver: KClass<R>, lambdaReturnType: KClass<T>, className: String, serBytes: ByteArray, allowedClassNames: Set<String>): R.() -> T? {
        val tracer = RestrictUsedClassesObjectInputStream(className,
                setOf(lambdaReceiver.java.name, lambdaReturnType.java.name) + allowedClassNames,
                ByteArrayInputStream(serBytes))
        return tracer.use { stream ->
            stream.readObject()
        } as R.() -> T
    }

    class RestrictUsedClassesObjectInputStream(val className: String, val allowedClassNames: Set<String>, input: InputStream) : ObjectInputStream(input) {
        override fun resolveClass(desc: ObjectStreamClass?): Class<*> {
            return super.resolveClass(desc)
        }

        override fun readClassDescriptor(): ObjectStreamClass {
            val temp = super.readClassDescriptor()
            val verify = verifier.verifySafeClassForDeser(className, allowedClassNames, temp.name)
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

