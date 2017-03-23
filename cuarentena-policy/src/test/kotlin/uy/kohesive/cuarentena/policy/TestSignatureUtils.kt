package uy.kohesive.cuarentena.policy

import org.junit.Test
import kotlin.reflect.KTypeProjection
import kotlin.reflect.KVariance
import kotlin.reflect.full.createType

class TestSignatureUtils {
    val types = listOf("int", Int::class.qualifiedName, "long", Long::class.qualifiedName,
            String::class.qualifiedName, java.lang.String::class.qualifiedName,
            Map::class.qualifiedName,
            Map::class.createType(listOf(KTypeProjection(KVariance.IN, String::class.createType(nullable = false)),
                    KTypeProjection(KVariance.OUT, String::class.createType(nullable = false))), nullable = false),
            Void::class.qualifiedName,
            Unit::class.qualifiedName,
            "method([ILjava/lang/String;)V", "method(Lorg/stuff/Test;)Ljava/util/List;")

    val typesFound = listOf("int", "int", "long", "long",
            "java/lang/String", "java/lang/String",
            "java/util/Map",
            "java/util/Map")

    @Test
    fun testSeeTypesWithinSignatures() {

    }

    @Test
    fun testSignatureNormalization() {


    }
}

/*
fun parseSig(sig: String): SignatureResult {
    SignatureReader(sig).accept(object: SignatureVisitor() {

    })
}
        */