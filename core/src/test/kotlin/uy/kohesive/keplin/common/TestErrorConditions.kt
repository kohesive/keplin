package uy.kohesive.keplin.common

import org.junit.Test
import uy.kohesive.keplin.kotlin.core.scripting.DO_NOTHING
import uy.kohesive.keplin.kotlin.core.scripting.ReplCompilerException
import uy.kohesive.keplin.kotlin.core.scripting.ReplEvalRuntimeException
import uy.kohesive.keplin.kotlin.core.scripting.ResettableRepl
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.junit.JUnitAsserter

class TestErrorConditions {
    @Test
    fun testBasicCompilerErrors() {
        ResettableRepl().use { repl ->
            try {
                repl.compileAndEval("java.util.Xyz()")
            } catch (ex: ReplCompilerException) {
                assertTrue("unresolved reference: Xyz" in ex.message!!)
            }
        }
    }

    @Test
    fun testBasicRuntimeErrors() {
        ResettableRepl().use { repl ->
            try {
                repl.compileAndEval("val x: String? = null; x!!")
            } catch (ex: ReplEvalRuntimeException) {
                assertTrue("NullPointerException" in ex.message!!)
            }
        }
    }

    @Test
    fun testResumeAfterCompilerError() {
        ResettableRepl().use { repl ->
            repl.compileAndEval("val x = 10")
            try {
                repl.compileAndEval("java.util.fish")
                JUnitAsserter.fail("Expected compile error")
            } catch (ex: ReplCompilerException) {
                DO_NOTHING()
            }

            val result = repl.compileAndEval("x")
            assertEquals(10, result.resultValue)
        }
    }

    @Test
    fun testResumeAfterRuntimeError() {
        ResettableRepl().use { repl ->
            try {
                repl.compileAndEval("val y = 100")
                repl.compileAndEval("val x: String? = null")
                try {
                    repl.compileAndEval("x!!")
                    JUnitAsserter.fail("Expected runtime error")
                } catch (ex: ReplEvalRuntimeException) {
                    DO_NOTHING()
                }

                val result = repl.compileAndEval("\"\$x \$y\"")
                assertEquals("null 100", result.resultValue)
            } catch (ex: ReplEvalRuntimeException) {
                assertTrue("NullPointerException" in ex.message!!)
            }
        }
    }
}