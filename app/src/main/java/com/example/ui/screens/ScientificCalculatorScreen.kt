package com.example.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.PremiumSurfaceCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalSmartStoreColors
import com.example.util.ScientificExpressionEvaluator
import java.util.Locale

private data class CalcHistoryEntry(
    val expression: String,
    val result: String
)

@Composable
fun ScientificCalculatorScreen(
    vatRate: Double = 19.25,
    onPostMessage: (String) -> Unit
) {
    val ext = LocalSmartStoreColors.current
    val clipboard = LocalClipboardManager.current

    var expression by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("0") }
    var lastAns by remember { mutableDoubleStateOf(0.0) }
    var useDegrees by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(listOf<CalcHistoryEntry>()) }

    fun recomputePreview(expr: String, deg: Boolean) {
        if (expr.isBlank()) {
            resultText = "0"
            errorMessage = null
            return
        }
        when (val eval = ScientificExpressionEvaluator.evaluate(expr, lastAns, deg)) {
            is ScientificExpressionEvaluator.EvalResult.Success -> {
                resultText = eval.formatted
                errorMessage = null
            }
            is ScientificExpressionEvaluator.EvalResult.Error -> {
                errorMessage = null
            }
        }
    }

    fun appendToken(token: String) {
        expression += token
        recomputePreview(expression, useDegrees)
    }

    fun evaluateFinal() {
        if (expression.isBlank()) return
        when (val eval = ScientificExpressionEvaluator.evaluate(expression, lastAns, useDegrees)) {
            is ScientificExpressionEvaluator.EvalResult.Success -> {
                history = (listOf(CalcHistoryEntry(expression, eval.formatted)) + history).take(20)
                lastAns = eval.value
                resultText = eval.formatted
                errorMessage = null
            }
            is ScientificExpressionEvaluator.EvalResult.Error -> {
                errorMessage = eval.message.ifBlank { "Expression invalide" }
            }
        }
    }

    fun applyCommercialMultiplier(factorExpr: String) {
        val base = if (expression.isNotBlank()) "($expression)" else ScientificExpressionEvaluator.formatNumber(lastAns)
        expression = "$base$factorExpr"
        evaluateFinal()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(
            title = "Calculatrice Scientifique & POS",
            subtitle = "Calculs scientifiques, TVA Cameroun ($vatRate%) & Marges",
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = useDegrees,
                        onClick = {
                            useDegrees = !useDegrees
                            recomputePreview(expression, useDegrees)
                        },
                        shape = RoundedCornerShape(14.dp),
                        label = { Text(if (useDegrees) "DEG" else "RAD", fontWeight = FontWeight.SemiBold) }
                    )
                    IconButton(onClick = { showHistory = !showHistory }) {
                        Icon(Icons.Rounded.History, contentDescription = "Historique calculs")
                    }
                }
            }
        )

        PremiumSurfaceCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadge(
                        label = "Ans = ${ScientificExpressionEvaluator.formatNumber(lastAns)}",
                        color = ext.violetPrimary
                    )
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(resultText))
                            onPostMessage("Résultat copié : $resultText")
                        }
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copier résultat", tint = ext.violetPrimary)
                    }
                }

                Text(
                    text = expression.ifBlank { "0" },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = errorMessage ?: resultText,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    ),
                    color = if (errorMessage != null) MaterialTheme.colorScheme.error else ext.violetPrimary,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().testTag("calc_result_display")
                )
            }
        }

        if (showHistory && history.isNotEmpty()) {
            PremiumSurfaceCard(modifier = Modifier.heightIn(max = 160.dp)) {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(history, key = { idx, _ -> "calc_hist_$idx" }) { _, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expression = entry.result
                                    recomputePreview(expression, useDegrees)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(entry.expression, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text("= ${entry.result}", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold), color = ext.violetPrimary)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tvaMul = 1.0 + (vatRate / 100.0)
            AssistChip(onClick = { applyCommercialMultiplier("*$tvaMul") }, shape = RoundedCornerShape(14.dp), label = { Text("+ TVA $vatRate%", fontWeight = FontWeight.SemiBold) })
            AssistChip(onClick = { applyCommercialMultiplier("/$tvaMul") }, shape = RoundedCornerShape(14.dp), label = { Text("HT (÷${String.format(Locale.US, "%.4f", tvaMul)})", fontWeight = FontWeight.SemiBold) })
            AssistChip(onClick = { applyCommercialMultiplier("*0.90") }, shape = RoundedCornerShape(14.dp), label = { Text("-10% Remise", fontWeight = FontWeight.SemiBold) })
            AssistChip(onClick = { applyCommercialMultiplier("*1.25") }, shape = RoundedCornerShape(14.dp), label = { Text("+25% Marge", fontWeight = FontWeight.SemiBold) })
        }

        val sciRows = listOf(
            listOf("sin(" to "sin", "cos(" to "cos", "tan(" to "tan", "ln(" to "ln", "log(" to "log"),
            listOf("asin(" to "sin⁻¹", "acos(" to "cos⁻¹", "atan(" to "tan⁻¹", "sqrt(" to "√", "^2" to "x²"),
            listOf("(" to "(", ")" to ")", "^" to "xʸ", "!" to "n!", "pi" to "π"),
            listOf("e" to "e", "1/(" to "1/x", "%" to "%", "Ans" to "Ans", "exp(" to "eˣ")
        )
        sciRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { (token, label) ->
                    CalcPadButton(
                        label = label,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = ext.midnightSecondary,
                        onClick = { appendToken(token) },
                        modifier = Modifier.weight(1f).height(46.dp)
                    )
                }
            }
        }

        val numRows = listOf(
            listOf("AC", "DEL", "÷", "×"),
            listOf("7", "8", "9", "−"),
            listOf("4", "5", "6", "+"),
            listOf("1", "2", "3", "="),
            listOf("0", "00", ".", "Ans")
        )
        numRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    val isEquals = key == "="
                    val isClear = key == "AC" || key == "DEL"
                    val isOp = key in setOf("÷", "×", "−", "+")
                    val bg = when {
                        isEquals -> ext.violetPrimary
                        isClear -> MaterialTheme.colorScheme.errorContainer
                        isOp -> ext.midnightSecondary
                        else -> MaterialTheme.colorScheme.surface
                    }
                    val fg = when {
                        isEquals -> Color.White
                        isClear -> MaterialTheme.colorScheme.onErrorContainer
                        isOp -> Color.White
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    CalcPadButton(
                        label = key,
                        containerColor = bg,
                        contentColor = fg,
                        onClick = {
                            when (key) {
                                "AC" -> {
                                    expression = ""
                                    resultText = "0"
                                    errorMessage = null
                                }
                                "DEL" -> {
                                    if (expression.isNotEmpty()) {
                                        expression = expression.dropLast(1)
                                        recomputePreview(expression, useDegrees)
                                    }
                                }
                                "=" -> evaluateFinal()
                                else -> appendToken(key)
                            }
                        },
                        modifier = Modifier.weight(1f).height(54.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CalcPadButton(
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalSmartStoreColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val elevation by animateDpAsState(
        targetValue = if (isHovered) 4.dp else 1.dp,
        animationSpec = tween(140),
        label = "calc_key_hover"
    )
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier.hoverable(interactionSource),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = elevation,
        border = BorderStroke(1.dp, if (isHovered) ext.violetPrimary.copy(alpha = 0.5f) else ext.cardBorder)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (label == "DEL") {
                Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "Effacer", modifier = Modifier.size(20.dp))
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

