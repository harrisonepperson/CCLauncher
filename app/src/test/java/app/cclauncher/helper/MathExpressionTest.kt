package app.cclauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MathExpressionTest {

    // Basic arithmetic

    @Test
    fun evaluatesAdditionAndSubtraction() {
        assertEquals("4", evaluateMathExpression("2+2"))
        assertEquals("8", evaluateMathExpression("10-2"))
        assertEquals("-3", evaluateMathExpression("4 - 7"))
    }

    @Test
    fun evaluatesMultiplicationAndDivision() {
        assertEquals("20", evaluateMathExpression("4*5"))
        assertEquals("12", evaluateMathExpression("144/12"))
        assertEquals("0.3333333333", evaluateMathExpression("1/3"))
    }

    @Test
    fun evaluatesModuloAndPower() {
        assertEquals("1", evaluateMathExpression("10%3"))
        assertEquals("8", evaluateMathExpression("2^3"))
        assertEquals("512", evaluateMathExpression("2^3^2"))
    }

    @Test
    fun evaluatesParenthesesAndUnarySigns() {
        assertEquals("16", evaluateMathExpression("(3+5)*2"))
        assertEquals("10", evaluateMathExpression("-(2+3)*-2"))
        assertEquals("-4", evaluateMathExpression("-2^2"))
    }

    @Test
    fun ignoresTrailingEqualsAndWhitespace() {
        assertEquals("4", evaluateMathExpression(" 2 + 2 = "))
        assertEquals("6", evaluateMathExpression("2×3"))
        assertEquals("2", evaluateMathExpression("8÷4"))
        assertEquals("2", evaluateMathExpression("6−4"))
    }

    // Functions and constants

    @Test
    fun evaluatesPowerAndRootFunctions() {
        assertEquals("16", evaluateMathExpression("pow(2, 4)"))
        assertEquals("16", evaluateMathExpression("pow(2,4)"))
        assertEquals("3", evaluateMathExpression("sqrt(9)"))
        assertEquals("2", evaluateMathExpression("cbrt(8)"))
        assertEquals("1.4142135624", evaluateMathExpression("sqrt(2)"))
    }

    @Test
    fun evaluatesLogarithms() {
        assertEquals("1", evaluateMathExpression("log(10)"))
        assertEquals("2", evaluateMathExpression("log(100)"))
        assertEquals("3", evaluateMathExpression("log(8, 2)"))
        assertEquals("1", evaluateMathExpression("ln(e)"))
        assertEquals("1.1447298858", evaluateMathExpression("ln(pi)"))
        assertEquals("3", evaluateMathExpression("log2(8)"))
    }

    @Test
    fun evaluatesTrigonometry() {
        assertEquals("0", evaluateMathExpression("sin(0)"))
        assertEquals("1", evaluateMathExpression("cos(0)"))
        assertEquals("0", evaluateMathExpression("tan(0)"))
        assertEquals("0", evaluateMathExpression("sin(pi)"))
        assertEquals("1.5707963268", evaluateMathExpression("arcsin(1)"))
        assertEquals("0", evaluateMathExpression("arccos(1)"))
        assertEquals("0.7853981634", evaluateMathExpression("arctan(1)"))
    }

    @Test
    fun evaluatesChainedFunctions() {
        assertEquals("1", evaluateMathExpression("sin(rad(90))"))
        assertEquals("-1", evaluateMathExpression("cos(rad(180))"))
        assertEquals("5", evaluateMathExpression("sqrt(pow(3, 2) + pow(4, 2))"))
        assertEquals("2", evaluateMathExpression("log(pow(10, 2))"))
    }

    @Test
    fun evaluatesConstantsAndIdentity() {
        assertEquals("3.1415926536", evaluateMathExpression("pi"))
        assertEquals("3.1415926536", evaluateMathExpression("PI"))
        assertEquals("3.1415926536", evaluateMathExpression("val(pi)"))
        assertEquals("2.7182818285", evaluateMathExpression("val(e)"))
        assertEquals("6.2831853072", evaluateMathExpression("tau"))
    }

    @Test
    fun evaluatesImplicitMultiplication() {
        assertEquals("6.2831853072", evaluateMathExpression("2pi"))
        assertEquals("8", evaluateMathExpression("2(3+1)"))
    }

    @Test
    fun evaluatesNumericHelpers() {
        assertEquals("3", evaluateMathExpression("min(3, 7)"))
        assertEquals("7", evaluateMathExpression("max(3, 7)"))
        assertEquals("5", evaluateMathExpression("abs(-5)"))
        assertEquals("2", evaluateMathExpression("floor(2.9)"))
        assertEquals("3", evaluateMathExpression("ceil(2.1)"))
        assertEquals("3", evaluateMathExpression("round(2.5)"))
    }

    // Invalid input

    @Test
    fun returnsNullForNonMathText() {
        assertNull(evaluateMathExpression(""))
        assertNull(evaluateMathExpression("hello"))
        assertNull(evaluateMathExpression("2"))
        assertNull(evaluateMathExpression("2048"))
    }

    @Test
    fun returnsNullForInvalidExpressions() {
        assertNull(evaluateMathExpression("2+"))
        assertNull(evaluateMathExpression("pow(2"))
        assertNull(evaluateMathExpression("sin()"))
        assertNull(evaluateMathExpression("log(-1)"))
        assertNull(evaluateMathExpression("unknown(1)"))
    }

    // Query classification

    @Test
    fun classifiesCompleteExpressionsAsEvaluated() {
        assertEquals(MathQuery.Evaluated("4"), classifyMathQuery("2+2"))
        assertEquals(MathQuery.Evaluated("16"), classifyMathQuery("pow(2, 4)"))
        assertEquals(MathQuery.Evaluated("3.1415926536"), classifyMathQuery("pi"))
    }

    @Test
    fun classifiesBareNumbersAsIncomplete() {
        assertIncomplete(classifyMathQuery("2"))
        assertIncomplete(classifyMathQuery("2048"))
        assertIncomplete(classifyMathQuery("3.14"))
        assertIncomplete(classifyMathQuery("2.5."))
    }

    @Test
    fun classifiesIncompleteExpressionsAsMathRegardlessOfSetting() {
        assertIncomplete(classifyMathQuery("2+"))
        assertIncomplete(classifyMathQuery("sin("))
        assertIncomplete(classifyMathQuery("pow(2"))
        assertIncomplete(classifyMathQuery("sqrt(-1)"))
        assertIncomplete(classifyMathQuery("pow(2, 4"))
        assertIncomplete(classifyMathQuery("2+", treatAmbiguousAsMath = true))
    }

    @Test
    fun exactFunctionNamesAreIncomplete() {
        assertIncomplete(classifyMathQuery("sin"))
        assertIncomplete(classifyMathQuery("log"))
        assertIncomplete(classifyMathQuery("pow"))
        assertIncomplete(classifyMathQuery("arcsin"))
        assertIncomplete(classifyMathQuery("sin", treatAmbiguousAsMath = true))
    }

    @Test
    fun ambiguousLettersAreOnlyMathWhenEnabled() {
        assertEquals(MathQuery.NotMath, classifyMathQuery("s"))
        assertEquals(MathQuery.NotMath, classifyMathQuery("e"))
        assertEquals(MathQuery.NotMath, classifyMathQuery("p"))

        assertIncomplete(classifyMathQuery("s", treatAmbiguousAsMath = true))
        assertIncomplete(classifyMathQuery("e", treatAmbiguousAsMath = true))
        assertIncomplete(classifyMathQuery("p", treatAmbiguousAsMath = true))
    }

    @Test
    fun partialFunctionNamesAreOnlyMathWhenEnabled() {
        assertEquals(MathQuery.NotMath, classifyMathQuery("si"))
        assertEquals(MathQuery.NotMath, classifyMathQuery("arc"))
        assertEquals(MathQuery.NotMath, classifyMathQuery("lo"))

        assertIncomplete(classifyMathQuery("si", treatAmbiguousAsMath = true))
        assertIncomplete(classifyMathQuery("arc", treatAmbiguousAsMath = true))
        assertIncomplete(classifyMathQuery("lo", treatAmbiguousAsMath = true))
    }

    @Test
    fun ordinaryWordsAreNotMath() {
        assertEquals(MathQuery.NotMath, classifyMathQuery("hello"))
        assertEquals(MathQuery.NotMath, classifyMathQuery("hello", treatAmbiguousAsMath = true))
        assertEquals(MathQuery.NotMath, classifyMathQuery("spotify", treatAmbiguousAsMath = true))
        assertEquals(MathQuery.NotMath, classifyMathQuery("q", treatAmbiguousAsMath = true))
    }

    private fun assertIncomplete(query: MathQuery) {
        assertTrue("Expected Incomplete but was $query", query is MathQuery.Incomplete)
    }
}
