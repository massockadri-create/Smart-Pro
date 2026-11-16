package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalSmartStoreColors
import com.example.util.Formatters
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.oned.Code128Writer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Clean, modern white elevated card (16dp rounded corners, soft light shadow, subtle hover feedback).
 */
@Composable
fun PremiumSurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accentBorderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val ext = LocalSmartStoreColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val elevation by animateDpAsState(
        targetValue = if (isHovered) 5.dp else 2.dp,
        animationSpec = tween(160),
        label = "card_hover_elevation"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isHovered -> ext.violetPrimary.copy(alpha = 0.45f)
            accentBorderColor != null -> accentBorderColor
            else -> ext.cardBorder
        },
        animationSpec = tween(160),
        label = "card_hover_border"
    )

    if (onClick != null) {
        Surface(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = modifier
                .fillMaxWidth()
                .hoverable(interactionSource)
                .animateContentSize(animationSpec = tween(200)),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = elevation,
            tonalElevation = 0.dp,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column(content = content)
        }
    } else {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .hoverable(interactionSource)
                .animateContentSize(animationSpec = tween(200)),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = elevation,
            tonalElevation = 0.dp,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column(content = content)
        }
    }
}

/**
 * Executive Midnight-Blue -> Deep-Violet Hero Banner with subtle Gold premium highlight chip.
 */
@Composable
fun FintechHeroBanner(
    title: String,
    subtitle: String,
    badgeText: String,
    primaryMetricLabel: String,
    primaryMetricValue: String,
    secondaryMetricLabel: String,
    secondaryMetricValue: String,
    icon: ImageVector,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val ext = LocalSmartStoreColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(ext.heroGradientStart, ext.heroGradientEnd)
                    )
                )
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(ext.goldPremium.copy(alpha = 0.16f), Color.Transparent),
                            center = Offset(size.width * 0.92f, size.height * 0.16f),
                            radius = size.width * 0.45f
                        )
                    )
                }
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f))
                        ) {
                            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                Icon(icon, contentDescription = title, tint = ext.goldPremium, modifier = Modifier.size(22.dp))
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ext.goldPremium.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, ext.goldPremium.copy(alpha = 0.55f))
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = ext.goldPremium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            maxLines = 1
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = primaryMetricLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White.copy(alpha = 0.75f)
                            )
                            Text(
                                text = primaryMetricValue,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = ext.goldPremium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = secondaryMetricLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White.copy(alpha = 0.75f)
                            )
                            Text(
                                text = secondaryMetricValue,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (actions != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions
                    )
                }
            }
        }
    }
}

@Composable
fun KpiMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val ext = LocalSmartStoreColors.current
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.96f,
        animationSpec = tween(220),
        label = "kpi_scale"
    )
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val elevation by animateDpAsState(
        targetValue = if (isHovered) 5.dp else 2.dp,
        animationSpec = tween(160),
        label = "kpi_hover"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .hoverable(interactionSource)
            .animateContentSize(animationSpec = tween(200)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = elevation,
        border = BorderStroke(
            1.dp,
            if (isHovered) accentColor.copy(alpha = 0.45f) else ext.cardBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = 0.12f)
                ) {
                    Box(
                        modifier = Modifier.size(36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = accentColor,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Normal),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun StatusBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f),
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            maxLines = 1
        )
    }
}

/**
 * Gold highlight badge used strictly for premium indicators, rewards, or high-value VIP details.
 */
@Composable
fun GoldPremiumBadge(
    label: String,
    modifier: Modifier = Modifier
) {
    val ext = LocalSmartStoreColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = ext.goldSoftBg,
        border = BorderStroke(1.dp, ext.goldPremium.copy(alpha = 0.65f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = ext.goldOnSoft,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            maxLines = 1
        )
    }
}

@Composable
fun LiveClockBadge(
    epochMs: Long,
    isOnlineSync: Boolean,
    showDate: Boolean = false,
    modifier: Modifier = Modifier
) {
    val ext = LocalSmartStoreColors.current
    val accent = if (isOnlineSync) ext.successGreen else ext.goldPremium
    val pulseOn = ((epochMs / 1000L) % 2L) == 0L
    val dotScale by animateFloatAsState(
        targetValue = if (pulseOn) 1.15f else 0.82f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "clock_pulse_scale"
    )
    val dotColor by animateColorAsState(
        targetValue = if (pulseOn) accent else accent.copy(alpha = 0.45f),
        animationSpec = tween(250),
        label = "clock_pulse_color"
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, ext.cardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(dotScale)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = if (showDate) {
                    "${Formatters.liveClockDate(epochMs)} · ${Formatters.timeWithSeconds(epochMs)}"
                } else {
                    Formatters.timeWithSeconds(epochMs)
                },
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (action != null) {
            action()
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    message: String,
    icon: ImageVector = Icons.Rounded.Inbox,
    modifier: Modifier = Modifier
) {
    val ext = LocalSmartStoreColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, ext.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SalesLineChart(
    series: List<Pair<String, Double>>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    val ext = LocalSmartStoreColors.current
    val gridColor = ext.cardBorder
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    var reveal by remember { mutableStateOf(false) }
    LaunchedEffect(series) { reveal = true }
    val progress by animateFloatAsState(
        targetValue = if (reveal) 1f else 0f,
        animationSpec = tween(durationMillis = 360),
        label = "chart_reveal"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, ext.cardBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Évolution des ventes (7 derniers jours)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            if (series.isEmpty() || series.all { it.second == 0.0 }) {
                Text(
                    "Aucune vente enregistrée sur la période.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = labelColor
                )
            } else {
                val maxVal = (series.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val padTop = 12f
                    val padBottom = 16f
                    val usableH = h - padTop - padBottom

                    for (i in 0..3) {
                        val y = padTop + (usableH * i / 3f)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.5f
                        )
                    }

                    val stepX = if (series.size > 1) w / (series.size - 1) else w
                    val points = series.mapIndexed { idx, (_, value) ->
                        val x = idx * stepX
                        val animatedRatio = ((value / maxVal).toFloat()) * progress
                        val y = padTop + usableH * (1f - animatedRatio)
                        Offset(x, y)
                    }

                    val fillPath = Path().apply {
                        moveTo(points.first().x, h - padBottom)
                        points.forEach { lineTo(it.x, it.y) }
                        lineTo(points.last().x, h - padBottom)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(lineColor.copy(alpha = 0.24f), Color.Transparent)
                        )
                    )

                    val strokePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            lineTo(points[i].x, points[i].y)
                        }
                    }
                    drawPath(
                        path = strokePath,
                        color = lineColor,
                        style = Stroke(width = 5f, cap = StrokeCap.Round)
                    )
                    points.forEach { pt ->
                        drawCircle(color = Color.White, radius = 8f, center = pt)
                        drawCircle(color = lineColor, radius = 5.5f, center = pt)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    series.forEach { (day, amt) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(day, style = MaterialTheme.typography.labelSmall, color = labelColor)
                            Text(
                                Formatters.fcfaCompact(amt).replace(" FCFA", ""),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = lineColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HorizontalBarChart(
    title: String,
    items: List<Pair<String, Double>>,
    barColor: Color,
    valueFormatter: (Double) -> String = { Formatters.fcfa(it) },
    modifier: Modifier = Modifier
) {
    val ext = LocalSmartStoreColors.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, ext.cardBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            if (items.isEmpty()) {
                Text(
                    "Aucune donnée disponible pour ce graphique.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val maxVal = (items.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0)
                items.forEach { (label, value) ->
                    val targetRatio = (value / maxVal).toFloat().coerceIn(0.05f, 1f)
                    val ratio by animateFloatAsState(
                        targetValue = targetRatio,
                        animationSpec = tween(280),
                        label = "bar_ratio_$label"
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                valueFormatter(value),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = barColor
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(ratio)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(barColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Real scannable Code-128 Barcode rendered via ZXing Code128Writer.
 */
@Composable
fun VisualBarcodeView(
    code: String,
    modifier: Modifier = Modifier
) {
    val ext = LocalSmartStoreColors.current
    val safeCode = code.ifBlank { "617000000000" }
    val matrix = remember(safeCode) {
        runCatching {
            Code128Writer().encode(safeCode, BarcodeFormat.CODE_128, 1, 1)
        }.getOrNull()
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ext.barcodeBg)
            .border(1.dp, ext.cardBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
        ) {
            if (matrix != null && matrix.width > 0) {
                val cols = matrix.width
                val moduleW = size.width / cols.toFloat()
                for (x in 0 until cols) {
                    if (matrix.get(x, 0)) {
                        drawRect(
                            color = ext.barcodeInk,
                            topLeft = Offset(x * moduleW, 0f),
                            size = Size(moduleW + 0.4f, size.height)
                        )
                    }
                }
            } else {
                val totalBars = 42
                val barUnit = size.width / totalBars
                var x = 0f
                for (i in 0 until totalBars) {
                    val ch = safeCode[i % safeCode.length].code
                    val isDark = (i == 0 || i == 2 || i == totalBars - 1 || i == totalBars - 3 || ((ch + i * 7) % 2 == 0))
                    if (isDark) {
                        val widthFactor = if ((ch + i) % 3 == 0) 0.9f else 0.55f
                        drawRoundRect(
                            color = ext.barcodeInk,
                            topLeft = Offset(x, 0f),
                            size = Size(barUnit * widthFactor, size.height),
                            cornerRadius = CornerRadius(1f, 1f)
                        )
                    }
                    x += barUnit
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = safeCode,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = ext.barcodeInk
        )
    }
}

/**
 * Real scannable 2D QR Code rendered via ZXing QRCodeWriter (ISO/IEC 18004).
 */
@Composable
fun RealQrCodeView(
    content: String,
    caption: String = "",
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val ext = LocalSmartStoreColors.current
    val safeContent = content.ifBlank { "SMARTSTORE|CM|XAF|0" }
    val qrMatrix = remember(safeContent) {
        runCatching {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1
            )
            QRCodeWriter().encode(safeContent, BarcodeFormat.QR_CODE, 0, 0, hints)
        }.getOrNull()
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = ext.barcodeBg,
        shadowElevation = 3.dp,
        border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (qrMatrix != null) {
                Canvas(modifier = Modifier.size(168.dp)) {
                    val w = qrMatrix.width
                    val h = qrMatrix.height
                    val cellW = size.width / w.toFloat()
                    val cellH = size.height / h.toFloat()
                    for (y in 0 until h) {
                        for (x in 0 until w) {
                            if (qrMatrix.get(x, y)) {
                                drawRect(
                                    color = ext.barcodeInk,
                                    topLeft = Offset(x * cellW, y * cellH),
                                    size = Size(cellW + 0.5f, cellH + 0.5f)
                                )
                            }
                        }
                    }
                }
            }
            if (caption.isNotBlank()) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = ext.barcodeInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
