package com.example

import com.example.util.ScientificExpressionEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScientificExpressionEvaluatorTest {

    private fun evalOk(expr: String, ansValue: Double = 0.0, useDegrees: Boolean = true): Double {
        val res = ScientificExpressionEvaluator.evaluate(expr, ansValue = ansValue, useDegrees = useDegrees)
        assertTrue("Expected Success for '$expr', got $res", res is ScientificExpressionEvaluator.EvalResult.Success)
        return (res as ScientificExpressionEvaluator.EvalResult.Success).value
    }

    @Test
    fun `evaluates basic arithmetic and precedence`() {
        val r = evalOk("2 + 3 × 4 - 6 ÷ 2")
        assertEquals(11.0, r, 1e-9)
    }

    @Test
    fun `evaluates scientific trig log power and factorial`() {
        val sin90 = evalOk("sin(90)", useDegrees = true)
        assertEquals(1.0, sin90, 1e-9)

        val log1000 = evalOk("log(1000)")
        assertEquals(3.0, log1000, 1e-9)

        val fact5 = evalOk("5!")
        assertEquals(120.0, fact5, 1e-9)

        val pow = evalOk("2^3^2")
        assertEquals(512.0, pow, 1e-9)
    }

    @Test
    fun `evaluates Cameroon VAT calculation and Ans reuse`() {
        val ttc = evalOk("10000 * 1.1925")
        assertEquals(11925.0, ttc, 1e-6)

        val withDiscount = evalOk("Ans - 925", ansValue = ttc)
        assertEquals(11000.0, withDiscount, 1e-6)
    }

    @Test
    fun `fails cleanly on division by zero`() {
        val res = ScientificExpressionEvaluator.evaluate("500 / (3 - 3)")
        assertTrue(res is ScientificExpressionEvaluator.EvalResult.Error)
    }
}
