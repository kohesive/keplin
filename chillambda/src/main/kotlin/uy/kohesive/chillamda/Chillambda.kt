package uy.kohesive.chillamda

import com.zackehh.siphash.SipHashCase
import com.zackehh.siphash.SipHashDigest
import uy.kohesive.cuarentena.ClassAllowanceDetector
import uy.kohesive.cuarentena.Cuarentena
import uy.kohesive.cuarentena.NamedClassBytes
import uy.kohesive.cuarentena.policy.ALL_CLASS_ACCESS_TYPES
import java.io.*
import java.util.*
import kotlin.reflect.KClass


class Chillambda(val verifier: Cuarentena = Cuarentena()) {
    companion object {
        val BINARY_PREFIX = "chilambda~~"
        val MARKER_SIG = "x9a0K1"
        val MARKER_VER = 1
        val SIG_SEED = "ChillWitMeLambda"

        fun isPrefixedBase64(scriptSource: String): Boolean = scriptSource.startsWith(BINARY_PREFIX)
    }

    fun SipHashDigest.update(s: String): SipHashDigest = this.update(s.toByteArray())

    data class SerializedLambdaClassData(val className: String, val classes: List<NamedClassBytes>, val serializedLambda: ByteArray, val verification: Cuarentena.VerifyResults)

    inline fun <reified R : Any, reified T : Any> deserFromPrefixedBase64(scriptSource: String, additionalPolicies: Set<String> = emptySet<String>()): SerializedLambdaClassData {
        return deserFromPrefixedBase64(R::class, T::class, scriptSource, additionalPolicies)
    }

    fun <R : Any, T : Any> deserFromPrefixedBase64(lambdaReceiver: KClass<R>, lambdaReturnType: KClass<T>, scriptSource: String, additionalPolicies: Set<String> = emptySet<String>()): SerializedLambdaClassData {
        if (!isPrefixedBase64(scriptSource)) throw ClassSerDesException("Script is not valid encoded classes")
        try {
            val receiverClassName = lambdaReceiver.java.canonicalName ?: lambdaReceiver.java.name
            val returnTypeClassName = lambdaReturnType.java.canonicalName ?: lambdaReturnType.java.name

            val rawData = scriptSource.substring(BINARY_PREFIX.length)
            val decodedBinary = Base64.getDecoder().decode(rawData)
            val content = DataInputStream(ByteArrayInputStream(decodedBinary)).use { stream ->
                val markerSig = stream.readString()
                val markerVer = stream.readInt()

                if (MARKER_SIG != markerSig || MARKER_VER != markerVer) {
                    throw ClassSerDesException("Serialized class has wrong signature or version, be sure client and server have matching versions")
                }

                val className = stream.readString()
                val checkReceiverClassName = stream.readString()
                val checkReturnTypeClassName = stream.readString()
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
                digest.update(checkReceiverClassName)
                digest.update(checkReturnTypeClassName)
                classes.forEach {
                    digest.update(it.className)
                    digest.update(it.bytes)
                }
                digest.update(serializedInstance)
                val calcSig = digest.finish().getHex(true, SipHashCase.UPPER)

                if (sentSig != calcSig) throw ClassSerDesException("Serialized classes signature is not valid")
                if (receiverClassName != checkReceiverClassName) throw ClassSerDesException("Serialized lambda does not have expected receiver ${receiverClassName}, instead is ${checkReceiverClassName}")
                if (returnTypeClassName != checkReturnTypeClassName) throw ClassSerDesException("Serialized lambda does not have expected return type ${returnTypeClassName}, instead is ${checkReturnTypeClassName}")

                val verification = verifier.verifyClassAgainstPolicies(classes, additionalPolicies)
                if (verification.failed) {
                    throw ClassSerDerViolationsException("The Lambda classes have invalid references:  \n${verification.violationsAsString()}", verification.violations)
                }

                SerializedLambdaClassData(className, verification.filteredClasses, serializedInstance, verification)
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

    inline fun <reified R : Any, reified T : Any> serializeLambdaToBase64(additionalPolicies: Set<String> = emptySet<String>(), noinline lambda: R.() -> T?): String {
        return serializeLambdaToBase64(R::class, T::class, additionalPolicies, lambda)
    }

    inline fun <reified R : Any, reified T : Any> serializeLambdaToBase64(noinline lambda: R.() -> T?): String {
        return serializeLambdaToBase64(R::class, T::class, emptySet(), lambda)
    }

    private fun classBytesForClass(className: String, useClassLoader: ClassLoader): NamedClassBytes {
        return NamedClassBytes(className,
                useClassLoader.getResourceAsStream(className.replace('.', '/') + ".class").use { it.readBytes() })
    }

    // TODO: handle types with generics
    fun <R : Any, T : Any> serializeLambdaToBase64(lambdaReceiver: KClass<R>, lambdaReturnType: KClass<T>, additionalPolicies: Set<String> = emptySet<String>(), lambda: R.() -> T?): String {
        val serClass = lambda.javaClass
        val className = serClass.canonicalName ?: serClass.name
        val receiverClassName = lambdaReceiver.java.canonicalName ?: lambdaReceiver.java.name
        val returnTypeClassName = lambdaReturnType.java.canonicalName ?: lambdaReturnType.java.name

        val serClassBytes = classBytesForClass(className, serClass.classLoader)

        // TODO:  for performance, since we scanned this class once, we should use it in future calls to avoid scanning twice
        val classScanResults = ClassAllowanceDetector.scanClassByteCodeForDesiredAllowances(listOf(serClassBytes))
        val accessedClassNames = classScanResults.allowances.filter { it.actions.any { it in ALL_CLASS_ACCESS_TYPES } }
                .map { it.fqnTarget }.plus(className).toSet()

        // TODO:  for Kotlin, we only need to serialize the Lambda if it has constructor parameters (and therefore state), otherwise not.  currently
        //        we are doing it every time regardless
        val classesIncludedInSerialization = mutableSetOf<Class<out Any>>()
        val serializedBytes = ByteArrayOutputStream().apply {
            TraceUsedClassesObjectOutputStream(this, classesIncludedInSerialization).use { stream ->
                stream.writeObject(lambda)
            }
        }.toByteArray()
        val serializedClassNames = classesIncludedInSerialization.map { it.canonicalName ?: it.name }.toSet()

        // take the starting lambda, and maybe its outer and inner classes but only if they are referenced
        val outerClasses = generateSequence<Class<*>>(serClass) { seed -> seed.declaringClass.takeIf { it != seed } } +
                generateSequence<Class<*>>(serClass) { seed -> seed.enclosingClass.takeIf { it != seed } }
        val innerClasses = generateSequence<List<Class<*>>>(listOf<Class<*>>(serClass)) { seed ->
            val l = seed.map { it.classes.filterNot { it == seed }.toList() }.flatten()
            if (l.isEmpty()) null else l
        }.flatten()
        val serClassRelatives = (innerClasses + outerClasses + serClass).map { it.canonicalName ?: it.name }.toSet()

        val classesToVerifyAndShip = (accessedClassNames + serializedClassNames + className).filter { it in serClassRelatives }

        // TODO: since these were serialized they probably already were accessed checked
        val serializedClassesToVerifyAccess = serializedClassNames.filterNot { it in serClassRelatives }
        val serializedClassVerifactionResult = verifier.verifyClassNamesAgainstPolicies(serializedClassesToVerifyAccess, additionalPolicies)
        if (serializedClassVerifactionResult.failed) {
            throw ClassSerDerViolationsException("The Lambda causes serialization of classes not in policy:  \n${serializedClassVerifactionResult.violationsAsString()}", serializedClassVerifactionResult.violations)
        }

        val classesToVerifyAndShipAsBytes = classesToVerifyAndShip.map { name ->
            if (name == className) serClassBytes
            else NamedClassBytes(name, serClass.classLoader.getResourceAsStream(name.replace('.', '/') + ".class").use { it.readBytes() })
        }

        val verification = verifier.verifyClassAgainstPolicies(classesToVerifyAndShipAsBytes)
        if (verification.failed) {
            throw ClassSerDerViolationsException("The Lambda classes have invalid references:  \n${verification.violationsAsString()}", verification.violations)
        }

        val actualClassesToShipAsBytes = verification.filteredClasses

        val content = ByteArrayOutputStream().apply {
            DataOutputStream(this).use { stream ->
                stream.writeString(MARKER_SIG)
                stream.writeInt(MARKER_VER)

                stream.writeString(className)
                stream.writeString(receiverClassName)
                stream.writeString(returnTypeClassName)
                stream.writeInt(actualClassesToShipAsBytes.size)
                actualClassesToShipAsBytes.forEach {
                    stream.writeString(it.className)
                    stream.writeByteArray(it.bytes)
                }

                stream.writeByteArray(serializedBytes)

                val digest = SipHashDigest(SIG_SEED.toByteArray())
                digest.update(className)
                digest.update(receiverClassName)
                digest.update(returnTypeClassName)
                actualClassesToShipAsBytes.forEach {
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

    inline fun <reified R : Any, reified T : Any> instantiateSerializedLambdaSafely(className: String, serBytes: ByteArray, additionalPolicies: Set<String> = emptySet()): R.() -> T? {
        return instantiateSerializedLambdaSafely(R::class, T::class, className, serBytes, additionalPolicies)
    }

    @Suppress("UNCHECKED_CAST")
    fun <R : Any, T : Any> instantiateSerializedLambdaSafely(lambdaReceiver: KClass<R>, lambdaReturnType: KClass<T>, className: String, serBytes: ByteArray, additionalPolicies: Set<String> = emptySet()): R.() -> T? {
        val tracer = RestrictUsedClassesObjectInputStream(verifier, additionalPolicies, ByteArrayInputStream(serBytes))
        return tracer.use { stream ->
            stream.readObject()
        } as R.() -> T
    }

    private class RestrictUsedClassesObjectInputStream(val verifier: Cuarentena, val additionalPolicies: Set<String>, input: InputStream) : ObjectInputStream(input) {
        override fun resolveClass(desc: ObjectStreamClass?): Class<*> {
            return super.resolveClass(desc)
        }

        override fun readClassDescriptor(): ObjectStreamClass {
            val temp = super.readClassDescriptor()
            val verify = verifier.verifyClassNamesAgainstPolicies(listOf(temp.name), additionalPolicies)
            if (verify.failed) {
                throw ClassSerDerViolationsException("Invalid class ${temp.name} not allowed for Kotlin Script Lambda deserialization, violations: ${verify.violationsAsString()}", verify.violations)
            }
            return temp
        } // called first:  we can check the type name here, and the fields it has with their name + signatures /// kotlin.jvm.internal.Lambda
    }

    private class TraceUsedClassesObjectOutputStream(output: OutputStream, val classes: MutableSet<Class<out Any>> = mutableSetOf()) : ObjectOutputStream(output) {
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

    open class ClassSerDesException(msg: String, cause: Throwable? = null) : Exception(msg, cause)
    class ClassSerDerViolationsException(msg: String, val violations: Set<String>, cause: Throwable? = null) : ClassSerDesException(msg, cause)
}

