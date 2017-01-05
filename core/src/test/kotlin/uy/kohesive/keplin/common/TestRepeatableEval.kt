@file:Suppress("DEPRECATION")

package uy.kohesive.keplin.common

import org.junit.Test
import uy.kohesive.keplin.kotlin.core.scripting.ReplCompilerException
import uy.kohesive.keplin.kotlin.core.scripting.ReplRepeatingMode
import uy.kohesive.keplin.kotlin.core.scripting.ResettableRepl
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.junit.JUnitAsserter

class TestRepeatableEval {
    @Test
    fun testRepeatableLastNotAllowed() {
        ResettableRepl(repeatingMode = ReplRepeatingMode.NONE).use { repl ->
            val line1 = repl.compile(repl.nextCodeLine("""val x = 1"""))
            val line2 = repl.compile(repl.nextCodeLine("""val y = 2"""))
            val line3 = repl.compile(repl.nextCodeLine("""x+y"""))

            repl.eval(line1)
            repl.eval(line2)
            repl.eval(line3)
            try {
                repl.eval(line3)
                JUnitAsserter.fail("Expecting history mismatch error")
            } catch (ex: ReplCompilerException) {
                assertTrue("History Mismatch" in ex.message!!)
            }
        }
    }

    @Test
    fun testRepeatableAnyNotAllowedInModeNONE() {
        ResettableRepl(repeatingMode = ReplRepeatingMode.NONE).use { repl ->
            val line1 = repl.compile(repl.nextCodeLine("""val x = 1"""))
            val line2 = repl.compile(repl.nextCodeLine("""val y = 2"""))
            val line3 = repl.compile(repl.nextCodeLine("""x+y"""))

            repl.eval(line1)
            repl.eval(line2)
            repl.eval(line3)

            try {
                repl.eval(line2)
                JUnitAsserter.fail("Expecting history mismatch error")
            } catch (ex: ReplCompilerException) {
                assertTrue("History Mismatch" in ex.message!!)
            }
        }
    }

    @Test
    fun testRepeatableAnyNotAllowedInModeMOSTRECENT() {
        ResettableRepl(repeatingMode = ReplRepeatingMode.REPEAT_ONLY_MOST_RECENT).use { repl ->
            val line1 = repl.compile(repl.nextCodeLine("""val x = 1"""))
            val line2 = repl.compile(repl.nextCodeLine("""val y = 2"""))
            val line3 = repl.compile(repl.nextCodeLine("""x+y"""))

            repl.eval(line1)
            repl.eval(line2)
            repl.eval(line3)

            try {
                repl.eval(line2)
                JUnitAsserter.fail("Expecting history mismatch error")
            } catch (ex: ReplCompilerException) {
                assertTrue("History Mismatch" in ex.message!!)
            }
        }
    }

    @Test
    fun testRepeatableExecutionsMOSTRECENT() {
        ResettableRepl(repeatingMode = ReplRepeatingMode.REPEAT_ONLY_MOST_RECENT).use { repl ->
            val line1 = repl.compile(repl.nextCodeLine("""val x = 1"""))
            val line2 = repl.compile(repl.nextCodeLine("""val y = 2"""))
            val line3 = repl.compile(repl.nextCodeLine("""x+y"""))

            repl.eval(line1)
            repl.eval(line1)
            repl.eval(line1)

            repl.eval(line2)
            repl.eval(line2)
            repl.eval(line2)
            repl.eval(line2)

            val result = repl.eval(line3)
            assertEquals(3, result.resultValue)
        }
    }

    @Test
    fun testRepeatableExecutionsREPEATANYPREVIOUS() {
        ResettableRepl(repeatingMode = ReplRepeatingMode.REPEAT_ANY_PREVIOUS).use { repl ->
            val line1 = repl.compile(repl.nextCodeLine("""val x = 1"""))
            val line2 = repl.compile(repl.nextCodeLine("""val y = 2"""))
            val line3 = repl.compile(repl.nextCodeLine("""x+y"""))

            repl.eval(line1)

            repl.eval(line2)

            repl.eval(line1)

            repl.eval(line2)

            val resultFirstTime = repl.eval(line3)
            assertEquals(3, resultFirstTime.resultValue)

            repl.eval(line2)

            val resultSecondTime = repl.eval(line3)
            assertEquals(3, resultSecondTime.resultValue)

            repl.eval(line1)
            repl.eval(line1)
            repl.eval(line1)
            repl.eval(line1)
        }
    }
}