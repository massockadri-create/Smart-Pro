package com.example.util

import java.util.Locale
import kotlin.math.*

object ScientificExpressionEvaluator {

    sealed class EvalResult {
        data class Success(val value: Double, val formatted: String) : EvalResult()
        data class Error(val message: String) : EvalResult()
    }

    fun evaluate(expression: String, ansValue: Double = 0.0, useDegrees: Boolean = true): EvalResult {
        val cleaned = expression
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace("π", "pi")
            .replace("√(", "sqrt(")
            .replace("√", "sqrt")
            .replace(",", ".")
            .replace(Regex("\\s+"), "")

        if (cleaned.isBlank()) return EvalResult.Error("Expression vide")

        return try {
            val parser = Parser(cleaned, ansValue, useDegrees)
            val value = parser.parseExpression()
            if (!parser.isAtEnd()) {
                return EvalResult.Error("Syntaxe invalide près de '${parser.peekChar()}'")
            }
            if (value.isNaN() || value.isInfinite()) {
                return EvalResult.Error("Résultat indéfini (division par zéro ou domaine)")
            }
            val normalized = if (abs(value) < 1e-12) 0.0 else value
            EvalResult.Success(normalized, formatNumber(normalized))
        } catch (e: IllegalArgumentException) {
            EvalResult.Error(e.message ?: "Erreur de syntaxe")
        } catch (_: Exception) {
            EvalResult.Error("Expression mathématique invalide")
        }
    }

    fun formatNumber(value: Double): String {
        val safe = if (abs(value) < 1e-12) 0.0 else value
        return if (abs(safe) >= 1e12 || (abs(safe) > 0 && abs(safe) < 1e-7)) {
            String.format(Locale.US, "%.6E", safe)
        } else {
            val rounded = safe.toLong()
            if (abs(safe - rounded.toDouble()) < 1e-10 && abs(safe) < Long.MAX_VALUE / 2) {
                rounded.toString()
            } else {
                String.format(Locale.US, "%.10f", safe)
                    .trimEnd('0')
                    .trimEnd('.')
            }
        }
    }

    private class Parser(
        private val input: String,
        private val ansValue: Double,
        private val useDegrees: Boolean
    ) {
        private var pos = 0

        fun isAtEnd(): Boolean = pos >= input.length
        fun peekChar(): Char = if (pos < input.length) input[pos] else '\u0000'

        private fun consumeIf(ch: Char): Boolean {
            if (pos < input.length && input[pos] == ch) {
                pos++
                return true
            }
            return false
        }

        fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                x = when {
                    consumeIf('+') -> x + parseTerm()
                    consumeIf('-') -> x - parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parsePower()
            while (true) {
                x = when {
                    consumeIf('*') -> x * parsePower()
                    consumeIf('/') -> {
                        val divisor = parsePower()
                        if (abs(divisor) < 1e-15) throw IllegalArgumentException("Division par zéro")
                        x / divisor
                    }
                    else -> return x
                }
            }
        }

        private fun parsePower(): Double {
            val base = parseUnary()
            return if (consumeIf('^')) {
                base.pow(parsePower())
            } else {
                base
            }
        }

        private fun parseUnary(): Double {
            if (consumeIf('+')) return parseUnary()
            if (consumeIf('-')) return -parseUnary()
            return parsePostfix()
        }

        private fun parsePostfix(): Double {
            var x = parsePrimary()
            while (true) {
                x = when {
                    consumeIf('!') -> factorial(x)
                    consumeIf('%') -> x / 100.0
                    else -> return x
                }
            }
        }

        private fun parsePrimary(): Double {
            if (consumeIf('(')) {
                val inner = parseExpression()
                if (!consumeIf(')')) throw IllegalArgumentException("Parenthèse ')' manquante")
                return inner
            }

            if (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) {
                val start = pos
                while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
                return input.substring(start, pos).toDoubleOrNull()
                    ?: throw IllegalArgumentException("Nombre invalide")
            }

            if (pos < input.length && input[pos].isLetter()) {
                val start = pos
                while (pos < input.length && (input[pos].isLetterOrDigit() || input[pos] == '_')) pos++
                val ident = input.substring(start, pos).lowercase(Locale.ROOT)
                return when (ident) {
                    "pi" -> PI
                    "e" -> E
                    "ans" -> ansValue
                    "sin", "cos", "tan", "asin", "acos", "atan", "sqrt", "ln", "log", "abs", "exp" -> {
                        val arg = if (consumeIf('(')) {
                            val v = parseExpression()
                            if (!consumeIf(')')) throw IllegalArgumentException("Parenthèse ')' manquante")
                            v
                        } else {
                            parseUnary()
                        }
                        applyFunction(ident, arg)
                    }
                    else -> throw IllegalArgumentException("Symbole inconnu : $ident")
                }
            }

            throw IllegalArgumentException("Terme attendu près de '${peekChar()}'")
        }

        private fun applyFunction(fn: String, arg: Double): Double {
            val angle = if (useDegrees) Math.toRadians(arg) else arg
            return when (fn) {
                "sin" -> sin(angle)
                "cos" -> cos(angle)
                "tan" -> {
                    val c = cos(angle)
                    if (abs(c) < 1e-12) throw IllegalArgumentException("Tangente indéfinie")
                    tan(angle)
                }
                "asin" -> {
                    if (arg < -1.0 || arg > 1.0) throw IllegalArgumentException("Domaine asin [-1, 1]")
                    val r = asin(arg)
                    if (useDegrees) Math.toDegrees(r) else r
                }
                "acos" -> {
                    if (arg < -1.0 || arg > 1.0) throw IllegalArgumentException("Domaine acos [-1, 1]")
                    val r = acos(arg)
                    if (useDegrees) Math.toDegrees(r) else r
                }
                "atan" -> {
                    val r = atan(arg)
                    if (useDegrees) Math.toDegrees(r) else r
                }
                "sqrt" -> {
                    if (arg < 0.0) throw IllegalArgumentException("Racine négative impossible")
                    sqrt(arg)
                }
                "ln" -> {
                    if (arg <= 0.0) throw IllegalArgumentException("ln requiert x > 0")
                    ln(arg)
                }
                "log" -> {
                    if (arg <= 0.0) throw IllegalArgumentException("log requiert x > 0")
                    log10(arg)
                }
                "abs" -> abs(arg)
                "exp" -> exp(arg)
                else -> throw IllegalArgumentException("Fonction inconnue : $fn")
            }
        }

        private fun factorial(x: Double): Double {
            val n = x.toInt()
            if (x < 0.0 || abs(x - n.toDouble()) > 1e-9 || n > 170) {
                throw IllegalArgumentException("Factorielle définie pour entiers 0..170")
            }
            var res = 1.0
            for (i in 2..n) res *= i.toDouble()
            return res
        }
    }
}
