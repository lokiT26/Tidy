package com.lokesh.tidy.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** A named accent palette: strong colour for contrast + soft pastel + a gradient partner. */
data class Accent(val name: String, val strong: Color, val pastel: Color, val soft: Color, val partner: Color)

object Palette {
    val accents = listOf(
        Accent("Lavender", Color(0xFF7C6CF2), Color(0xFFB9A7FF), Color(0xFFEDE8FF), Color(0xFFFFB79A)),
        Accent("Peach", Color(0xFFF07B4F), Color(0xFFFFC7A8), Color(0xFFFFEEE4), Color(0xFFFF9EC0)),
        Accent("Mint", Color(0xFF22A97A), Color(0xFFA8E6CF), Color(0xFFE3F7EE), Color(0xFF9FD8FF)),
        Accent("Sky", Color(0xFF3A8FE0), Color(0xFFA8D8FF), Color(0xFFE4F2FF), Color(0xFFC1A8FF)),
        Accent("Rose", Color(0xFFE5577F), Color(0xFFFFB3C8), Color(0xFFFFE7EE), Color(0xFFFFCF9E)),
        Accent("Butter", Color(0xFFD99A00), Color(0xFFFFE08A), Color(0xFFFFF5D6), Color(0xFFA8E6CF)),
        Accent("Graphite", Color(0xFF4A4A55), Color(0xFFBDBDC9), Color(0xFFEEEEF3), Color(0xFF9FB4FF)),
    )

    /** Pastel note / category colours. Index 0 = neutral. */
    val pastels = listOf(
        Color(0xFFFFFFFF), // neutral
        Color(0xFFDCD3FF), // lavender
        Color(0xFFFFD9C4), // peach
        Color(0xFFC6EEDD), // mint
        Color(0xFFCDE6FF), // sky
        Color(0xFFFFEBA8), // butter
        Color(0xFFFFCCDA), // rose
        Color(0xFFE6E0D6), // sand
    )
    val pastelNames = listOf("Default", "Lavender", "Peach", "Mint", "Sky", "Butter", "Rose", "Sand")
    /** Saturated version of pastels for dots, chips, strokes. */
    val vivid = listOf(
        Color(0xFF8E8E9A), Color(0xFF8B7BF5), Color(0xFFF2865C), Color(0xFF2FB383),
        Color(0xFF3D9BE9), Color(0xFFE0A800), Color(0xFFE8628C), Color(0xFFA68F6B),
    )

    fun noteColor(index: Int, dark: Boolean): Color {
        val c = pastels.getOrElse(index) { pastels[0] }
        return if (!dark) c else if (index == 0) Color(0xFF1E1E23) else lerp(Color(0xFF1C1C21), vivid[index], 0.28f)
    }

    val priority = listOf(Color(0xFFB8B8C4), Color(0xFF4C9AFF), Color(0xFFFFA24C), Color(0xFFFF5C6C))
    val priorityNames = listOf("None", "Low", "Medium", "High")

    val success = Color(0xFF2FB383)
    val danger = Color(0xFFFF5C6C)
}
