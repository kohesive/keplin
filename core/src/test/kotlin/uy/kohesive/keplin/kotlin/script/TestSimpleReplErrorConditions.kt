package uy.kohesive.keplin.kotlin.script

import org.jetbrains.kotlin.cli.common.repl.NO_ACTION
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.junit.JUnitAsserter


class TestErrorConditions {
    @Test
    fun testBasicCompilerErrors() {
        SimplifiedRepl().use { repl ->
            try {
                repl.compileAndEval("java.util.Xyz()")
            } catch (ex: ReplCompilerException) {
                assertTrue("unresolved reference: Xyz" in ex.message!!)
            }
        }
    }

    @Test
    fun testBasicRuntimeErrors() {
        SimplifiedRepl().use { repl ->
            try {
                repl.compileAndEval("val x: String? = null")
                repl.compileAndEval("x!!")
            } catch (ex: ReplEvalRuntimeException) {
                assertTrue("NullPointerException" in ex.message!!)
            }
        }
    }

    @Test
    fun testResumeAfterCompilerError() {
        SimplifiedRepl().use { repl ->
            repl.compileAndEval("val x = 10")
            try {
                repl.compileAndEval("java.util.fish")
                JUnitAsserter.fail("Expected compile error")
            } catch (ex: ReplCompilerException) {
                NO_ACTION()
            }

            val result = repl.compileAndEval("x")
            assertEquals(10, result.resultValue)
        }
    }

    @Test
    fun testResumeAfterRuntimeError() {
        SimplifiedRepl().use { repl ->
            try {
                repl.compileAndEval("val y = 100")
                repl.compileAndEval("val x: String? = null")
                try {
                    repl.compileAndEval("x!!")
                    JUnitAsserter.fail("Expected runtime error")
                } catch (ex: ReplEvalRuntimeException) {
                    NO_ACTION()
                }

                val result = repl.compileAndEval("\"\$x \$y\"")
                assertEquals("null 100", result.resultValue)
            } catch (ex: ReplEvalRuntimeException) {
                assertTrue("NullPointerException" in ex.message!!)
            }
        }
    }
}