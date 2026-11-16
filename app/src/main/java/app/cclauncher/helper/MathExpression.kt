package app.cclauncher.helper

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Classification of a search query for the optional math result feature.
 */
sealed interface MathQuery {
    /** The query has no math meaning and should be treated as a normal search. */
    data object NotMath : MathQuery

    /**
     * The query looks like it is trying to be math (a partial function name,
     * a function call missing arguments, an operator without an operand, ...)
     * but cannot be evaluated. Single-result auto-open should stay disabled,
     * but an explicit search may still open the app.
     */
    data object Incomplete : MathQuery

    /** The query evaluated successfully. */
    data class Evaluated(val result: String) : MathQuery
}

/**
 * Evaluates [expression] and returns a display-ready result, or null when it
 * is not a valid calculation.
 */
fun evaluateMathExpression(expression: String): String? =
    (classifyMathQuery(expression) as? MathQuery.Evaluated)?.result

/**
 * Classifies [expression] so callers can distinguish "not math" from
 * "partially typed math".
 */
fun classifyMathQuery(
    expression: String,
    treatAmbiguousAsMath: Boolean = false,
): MathQuery {
    val normalized = normalizeMathInput(expression)
    if (normalized.isEmpty()) return MathQuery.NotMath

    // A bare number is likely the start of an equation, so suppress auto-open
    // while the user keeps typing, but don't show a result for it yet.
    if (normalized.all { it.isDigit() || it == '.' }) return MathQuery.Incomplete

    // A single bare letter is only ambiguous when it could be the start of a
    // known function or constant (e.g. "s" for "sin", "e" for "exp").
    if (normalized.length == 1 && normalized[0].isLetter()) {
        val couldStartMath = MathNames.known.any { it.startsWith(normalized) }
        return if (treatAmbiguousAsMath && couldStartMath) {
            MathQuery.Incomplete
        } else {
            MathQuery.NotMath
        }
    }

    val evaluated = try {
        MathExpressionParser(normalized).parse()
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: ArithmeticException) {
        null
    }

    if (evaluated != null && evaluated.isFinite()) {
        return MathQuery.Evaluated(formatMathResult(evaluated))
    }

    return if (looksLikeMath(normalized, treatAmbiguousAsMath)) {
        MathQuery.Incomplete
    } else {
        MathQuery.NotMath
    }
}

private fun normalizeMathInput(expression: String): String = expression
    .replace('×', '*')
    .replace('÷', '/')
    .replace('−', '-')
    .trim()
    .removeSuffix("=")
    .trim()
    .lowercase()

/**
 * Returns true when the text contains something that suggests the user is
 * typing math even though it does not evaluate yet.
 */
private fun looksLikeMath(input: String, treatAmbiguousAsMath: Boolean): Boolean {
    if (input.any { it.isDigit() }) {
        return input.any { !it.isDigit() && it != '.' }
    }
    if (input.any { it in "+-*/%^()" }) return true

    // Exact function names such as "log" or "arcsin" always look like math.
    // Partial names such as "si" or "arc" only count when the user opted in.
    return mathIdentifiers(input).any { token ->
        when {
            token.length < 2 -> false
            token in MathNames.known -> true
            treatAmbiguousAsMath -> MathNames.known.any { it.startsWith(token) }
            else -> false
        }
    }
}

private fun mathIdentifiers(input: String): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    for (char in input) {
        if (char.isLetter() || char.isDigit() || char == '_') {
            current.append(char)
        } else if (current.isNotEmpty()) {
            tokens += current.toString()
            current.clear()
        }
    }
    if (current.isNotEmpty()) tokens += current.toString()
    return tokens
}

private fun formatMathResult(value: Double): String {
    val snapped = if (kotlin.math.abs(value) < 1e-12) 0.0 else value
    if (snapped == 0.0) return "0"
    return BigDecimal.valueOf(snapped)
        .setScale(10, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}

private object MathNames {
    val constants: Map<String, Double> = mapOf(
        "pi" to Math.PI,
        "π" to Math.PI,
        "e" to Math.E,
        "tau" to Math.PI * 2.0,
    )

    val functions: Set<String> = setOf(
        "pow", "sqrt", "cbrt", "abs", "exp",
        "ln", "log", "log2", "log10",
        "sin", "cos", "tan",
        "asin", "acos", "atan", "arcsin", "arccos", "arctan",
        "sinh", "cosh", "tanh",
        "asinh", "acosh", "atanh", "arsinh", "arcosh", "artanh",
        "floor", "ceil", "round", "trunc", "sign", "signum",
        "min", "max", "mod", "hypot", "atan2",
        "val", "deg", "rad",
    )

    val known: Set<String> = constants.keys + functions
}

private class MathExpressionParser(private val input: String) {
    private var position = 0

    fun parse(): Double {
        val result = parseExpression()
        skipWhitespace()
        if (position != input.length) throw IllegalArgumentException("Unexpected input")
        return result
    }

    private fun parseExpression(): Double {
        var value = parseTerm()
        while (true) {
            skipWhitespace()
            when (peek()) {
                '+' -> {
                    position++
                    value += parseTerm()
                }
                '-' -> {
                    position++
                    value -= parseTerm()
                }
                else -> return value
            }
        }
    }

    private fun parseTerm(): Double {
        var value = parseUnary()
        while (true) {
            skipWhitespace()
            when {
                peek() == '*' -> {
                    position++
                    value *= parseUnary()
                }
                peek() == '/' -> {
                    position++
                    value /= parseUnary()
                }
                peek() == '%' -> {
                    position++
                    value %= parseUnary()
                }
                startsImplicitFactor() -> value *= parseUnary()
                else -> return value
            }
        }
    }

    private fun startsImplicitFactor(): Boolean {
        val char = peek() ?: return false
        return char.isLetter() || char == '(' || char.isDigit()
    }

    private fun parseUnary(): Double {
        skipWhitespace()
        return when (peek()) {
            '+' -> {
                position++
                parseUnary()
            }
            '-' -> {
                position++
                -parseUnary()
            }
            else -> parsePower()
        }
    }

    private fun parsePower(): Double {
        val base = parsePrimary()
        skipWhitespace()
        if (peek() != '^') return base
        position++
        return Math.pow(base, parseUnary())
    }

    private fun parsePrimary(): Double {
        skipWhitespace()
        if (peek() == '(') {
            position++
            val value = parseExpression()
            skipWhitespace()
            if (peek() != ')') throw IllegalArgumentException("Missing closing parenthesis")
            position++
            return value
        }
        val char = peek() ?: throw IllegalArgumentException("Unexpected end of input")
        if (char.isLetter() || char == 'π') return parseIdentifier()
        return parseNumber()
    }

    private fun parseIdentifier(): Double {
        val start = position
        while (position < input.length) {
            val char = input[position]
            if (char.isLetterOrDigit() || char == '_' || char == 'π') {
                position++
            } else {
                break
            }
        }
        val name = input.substring(start, position)
        skipWhitespace()

        if (peek() != '(') {
            MathNames.constants[name]?.let { return it }
            throw IllegalArgumentException("Unknown identifier: $name")
        }

        position++ // consume '('
        val args = mutableListOf<Double>()
        skipWhitespace()
        if (peek() != ')') {
            while (true) {
                args += parseExpression()
                skipWhitespace()
                if (peek() == ',') {
                    position++
                    continue
                }
                break
            }
        }
        skipWhitespace()
        if (peek() != ')') throw IllegalArgumentException("Missing closing parenthesis")
        position++

        return applyFunction(name, args)
    }

    private fun applyFunction(name: String, args: List<Double>): Double {
        fun unary(): Double {
            if (args.size != 1) throw IllegalArgumentException("$name expects one argument")
            return args[0]
        }

        return when (name) {
            "pow" -> {
                if (args.size != 2) throw IllegalArgumentException("pow expects two arguments")
                Math.pow(args[0], args[1])
            }
            "sqrt" -> Math.sqrt(unary())
            "cbrt" -> Math.cbrt(unary())
            "abs" -> kotlin.math.abs(unary())
            "exp" -> Math.exp(unary())
            "ln" -> Math.log(unary())
            "log" -> when (args.size) {
                1 -> Math.log10(args[0])
                2 -> Math.log(args[0]) / Math.log(args[1])
                else -> throw IllegalArgumentException("log expects one or two arguments")
            }
            "log2" -> Math.log(unary()) / Math.log(2.0)
            "log10" -> Math.log10(unary())
            "sin" -> Math.sin(unary())
            "cos" -> Math.cos(unary())
            "tan" -> Math.tan(unary())
            "asin", "arcsin" -> Math.asin(unary())
            "acos", "arccos" -> Math.acos(unary())
            "atan", "arctan" -> Math.atan(unary())
            "sinh" -> Math.sinh(unary())
            "cosh" -> Math.cosh(unary())
            "tanh" -> Math.tanh(unary())
            "asinh", "arsinh" -> {
                val x = unary()
                Math.log(x + Math.sqrt(x * x + 1.0))
            }
            "acosh", "arcosh" -> {
                val x = unary()
                Math.log(x + Math.sqrt(x * x - 1.0))
            }
            "atanh", "artanh" -> {
                val x = unary()
                0.5 * Math.log((1.0 + x) / (1.0 - x))
            }
            "floor" -> Math.floor(unary())
            "ceil" -> Math.ceil(unary())
            "round" -> Math.round(unary()).toDouble()
            "trunc" -> if (unary() < 0) Math.ceil(unary()) else Math.floor(unary())
            "sign", "signum" -> Math.signum(unary())
            "min" -> args.minOrNull() ?: throw IllegalArgumentException("min expects arguments")
            "max" -> args.maxOrNull() ?: throw IllegalArgumentException("max expects arguments")
            "mod" -> {
                if (args.size != 2) throw IllegalArgumentException("mod expects two arguments")
                args[0] % args[1]
            }
            "hypot" -> {
                if (args.size != 2) throw IllegalArgumentException("hypot expects two arguments")
                Math.hypot(args[0], args[1])
            }
            "atan2" -> {
                if (args.size != 2) throw IllegalArgumentException("atan2 expects two arguments")
                Math.atan2(args[0], args[1])
            }
            "val" -> unary()
            "deg" -> Math.toDegrees(unary())
            "rad" -> Math.toRadians(unary())
            else -> throw IllegalArgumentException("Unknown function: $name")
        }
    }

    private fun parseNumber(): Double {
        val start = position
        var hasDigit = false
        var hasDecimalPoint = false

        while (position < input.length) {
            val char = input[position]
            when {
                char.isDigit() -> {
                    hasDigit = true
                    position++
                }
                char == '.' && !hasDecimalPoint -> {
                    hasDecimalPoint = true
                    position++
                }
                else -> break
            }
        }

        if (!hasDigit) throw IllegalArgumentException("Expected a number")
        return input.substring(start, position).toDouble()
    }

    private fun peek(): Char? = input.getOrNull(position)

    private fun skipWhitespace() {
        while (position < input.length && input[position].isWhitespace()) {
            position++
        }
    }
}
