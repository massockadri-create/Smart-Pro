package com.example.util

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class StructurePreset(
    val key: String,
    val label: String,
    val tagline: String,
    val icon: ImageVector,
    val primaryHex: String,
    val secondaryHex: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val badgeTag: String
)

object StructureCatalog {
    val presets: List<StructurePreset> = listOf(
        StructurePreset(
            key = "GENERAL",
            label = "Commerce Général",
            tagline = "Boutique polyvalente & distribution",
            icon = Icons.Rounded.Storefront,
            primaryHex = "#10B981",
            secondaryHex = "#059669",
            primaryColor = Color(0xFF10B981),
            secondaryColor = Color(0xFF059669),
            badgeTag = "CG"
        ),
        StructurePreset(
            key = "SUPERMARCHE",
            label = "Supermarché / Alimentation",
            tagline = "Rayons frais, boissons & épicerie",
            icon = Icons.Rounded.ShoppingCart,
            primaryHex = "#06B6D4",
            secondaryHex = "#0284C7",
            primaryColor = Color(0xFF06B6D4),
            secondaryColor = Color(0xFF0284C7),
            badgeTag = "SM"
        ),
        StructurePreset(
            key = "PHARMACIE",
            label = "Pharmacie / Santé",
            tagline = "Parapharmacie, soins & laboratoire",
            icon = Icons.Rounded.LocalPharmacy,
            primaryHex = "#22C55E",
            secondaryHex = "#0D9488",
            primaryColor = Color(0xFF22C55E),
            secondaryColor = Color(0xFF0D9488),
            badgeTag = "RX"
        ),
        StructurePreset(
            key = "ELECTRONIQUE",
            label = "Électronique / High-Tech",
            tagline = "Téléphones, informatique & accessoires",
            icon = Icons.Rounded.Devices,
            primaryHex = "#6366F1",
            secondaryHex = "#3B82F6",
            primaryColor = Color(0xFF6366F1),
            secondaryColor = Color(0xFF3B82F6),
            badgeTag = "HT"
        ),
        StructurePreset(
            key = "MODE",
            label = "Mode & Cosmétique",
            tagline = "Prêt-à-porter, parfums & beauté",
            icon = Icons.Rounded.Checkroom,
            primaryHex = "#EC4899",
            secondaryHex = "#A855F7",
            primaryColor = Color(0xFFEC4899),
            secondaryColor = Color(0xFFA855F7),
            badgeTag = "MC"
        ),
        StructurePreset(
            key = "RESTAURANT",
            label = "Restaurant / Snack / Bar",
            tagline = "Restauration rapide, cave & lounge",
            icon = Icons.Rounded.Restaurant,
            primaryHex = "#F59E0B",
            secondaryHex = "#EA580C",
            primaryColor = Color(0xFFF59E0B),
            secondaryColor = Color(0xFFEA580C),
            badgeTag = "RS"
        ),
        StructurePreset(
            key = "QUINCAILLERIE",
            label = "Quincaillerie / BTP",
            tagline = "Matériaux, outillage & plomberie",
            icon = Icons.Rounded.Hardware,
            primaryHex = "#F97316",
            secondaryHex = "#B45309",
            primaryColor = Color(0xFFF97316),
            secondaryColor = Color(0xFFB45309),
            badgeTag = "BT"
        ),
        StructurePreset(
            key = "GROSSISTE",
            label = "Grossiste / Dépôt",
            tagline = "Vente en gros, palettes & entrepôt",
            icon = Icons.Rounded.Warehouse,
            primaryHex = "#14B8A6",
            secondaryHex = "#0F766E",
            primaryColor = Color(0xFF14B8A6),
            secondaryColor = Color(0xFF0F766E),
            badgeTag = "GD"
        ),
        StructurePreset(
            key = "LIBRAIRIE",
            label = "Librairie / Services",
            tagline = "Fournitures, bureautique & multi-services",
            icon = Icons.Rounded.MenuBook,
            primaryHex = "#8B5CF6",
            secondaryHex = "#6D28D9",
            primaryColor = Color(0xFF8B5CF6),
            secondaryColor = Color(0xFF6D28D9),
            badgeTag = "LB"
        )
    )

    fun presetFor(key: String?): StructurePreset {
        return presets.find { it.key.equals(key, ignoreCase = true) } ?: presets.first()
    }

    /**
     * Generates a clean inline SVG emblem tailored to the structure's type for printed HTML
     * invoices and the synchronized desktop web portal.
     */
    fun buildStructureLogoSvg(key: String?, sizePx: Int = 54): String {
        val p = presetFor(key)
        val innerShape = when (p.key) {
            "PHARMACIE" -> """<path d="M23 14h8v9h9v8h-9v9h-8v-9h-9v-8h9z" fill="#ffffff"/>"""
            "SUPERMARCHE" -> """<path d="M16 18h24l-3 14H20l-2-10h-4v-4h2zm5 18a3 3 0 110 6 3 3 0 010-6zm13 0a3 3 0 110 6 3 3 0 010-6z" fill="#ffffff"/>"""
            "ELECTRONIQUE" -> """<rect x="15" y="16" width="24" height="16" rx="2" fill="none" stroke="#ffffff" stroke-width="3"/><path d="M12 36h30" stroke="#ffffff" stroke-width="3" stroke-linecap="round"/>"""
            "MODE" -> """<path d="M27 14c3 0 5 2 4 5l10 8-3 4-4-2v11H20V29l-4 2-3-4 10-8c-1-3 1-5 4-5z" fill="#ffffff"/>"""
            "RESTAURANT" -> """<path d="M19 14v12a4 4 0 004 4v10h4V30a4 4 0 004-4V14h-3v9h-2v-9h-2v9h-2v-9h-3zm17 0c-3 2-4 7-4 13h4v13h4V14h-4z" fill="#ffffff"/>"""
            "QUINCAILLERIE" -> """<path d="M17 20l5-5 15 15-5 5-15-15zm17-5l5 5-4 4-5-5 4-4z" fill="#ffffff"/>"""
            "GROSSISTE" -> """<path d="M13 24l14-9 14 9v16H35V30H19v10h-6V24z" fill="#ffffff"/>"""
            "LIBRAIRIE" -> """<path d="M15 16h11v22H15a2 2 0 01-2-2V18a2 2 0 012-2zm13 0h11a2 2 0 012 2v18a2 2 0 01-2 2H28V16z" fill="#ffffff"/>"""
            else -> """<path d="M14 22l3-7h20l3 7v3a4 4 0 01-6 3.5A4 4 0 0127 28a4 4 0 01-7 .5A4 4 0 0114 25v-3zm3 9h20v9H17v-9z" fill="#ffffff"/>"""
        }
        return """
            <svg xmlns="http://www.w3.org/2000/svg" width="$sizePx" height="$sizePx" viewBox="0 0 54 54">
              <defs>
                <linearGradient id="lg_${p.key}" x1="0" y1="0" x2="1" y2="1">
                  <stop offset="0%" stop-color="${p.primaryHex}"/>
                  <stop offset="100%" stop-color="${p.secondaryHex}"/>
                </linearGradient>
              </defs>
              <rect width="54" height="54" rx="14" fill="url(#lg_${p.key})"/>
              $innerShape
            </svg>
        """.trimIndent()
    }
}

@Composable
fun StructureLogoBadge(
    logoKey: String?,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val preset = StructureCatalog.presetFor(logoKey)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape((size.value * 0.28f).dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(preset.primaryColor, preset.secondaryColor)
                )
            )
            .border(
                width = 1.5.dp,
                color = Color.White.copy(alpha = 0.28f),
                shape = RoundedCornerShape((size.value * 0.28f).dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = preset.icon,
            contentDescription = preset.label,
            tint = Color.White,
            modifier = Modifier.size((size.value * 0.56f).dp)
        )
    }
}
