package com.lokesh.tidy.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lokesh.tidy.data.Category
import com.lokesh.tidy.data.Repository
import com.lokesh.tidy.data.TaskWithSubtasks
import com.lokesh.tidy.ui.theme.Palette
import com.lokesh.tidy.ui.theme.tidy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StatsScreen(tasks: List<TaskWithSubtasks>, categories: List<Category>) {
    val t = tidy
    val zone = ZoneId.systemDefault()
    val all = tasks.map { it.task }
    val completedDays = remember(all) {
        all.mapNotNull { it.completedAt }.groupingBy { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toEpochDay() }.eachCount()
    }
    val (streak, best) = remember(all) { Repository.streaks(all) }
    val today = LocalDate.now()
    val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val weekCounts = week.map { completedDays[it.toEpochDay()] ?: 0 }
    val totalDone = all.count { it.completed }
    val rate = if (all.isEmpty()) 0 else (totalDone * 100 / all.size)
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, tween(900)) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 18.dp).padding(top = 18.dp, bottom = 140.dp)
    ) {
        Text("Your progress", style = MaterialTheme.typography.labelLarge, color = t.textMuted)
        Text("Insights", style = MaterialTheme.typography.headlineLarge, color = t.text)
        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("🔥", "$streak", "Current streak", Modifier.weight(1f), Palette.pastels[2])
            StatTile("🏆", "$best", "Best streak", Modifier.weight(1f), Palette.pastels[5])
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("✅", "$totalDone", "Completed", Modifier.weight(1f), Palette.pastels[3])
            StatTile("📈", "$rate%", "Completion rate", Modifier.weight(1f), Palette.pastels[4])
        }

        Spacer(Modifier.height(16.dp))
        Card {
            Text("This week", style = MaterialTheme.typography.titleMedium, color = t.text)
            Text("${weekCounts.sum()} tasks completed", style = MaterialTheme.typography.bodySmall, color = t.textMuted)
            Spacer(Modifier.height(16.dp))
            val max = (weekCounts.maxOrNull() ?: 0).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                week.forEachIndexed { i, d ->
                    val isToday = d == today
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("${weekCounts[i]}", style = MaterialTheme.typography.labelSmall, color = if (weekCounts[i] > 0) t.textMuted else Color.Transparent)
                        Spacer(Modifier.height(4.dp))
                        val frac = (weekCounts[i] / max.toFloat()).coerceAtLeast(0.04f) * anim.value
                        Box(Modifier.width(22.dp).fillMaxHeight(0.78f * frac + 0.001f).clip(RoundedCornerShape(10.dp))
                            .background(if (isToday) Brush.verticalGradient(listOf(t.accent.partner, t.accentStrong)) else Brush.verticalGradient(listOf(t.accentSoft, t.accentSoft))))
                        Spacer(Modifier.height(8.dp))
                        Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2), style = MaterialTheme.typography.labelSmall, color = if (isToday) t.accentStrong else t.textFaint)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Card {
            Text("Activity", style = MaterialTheme.typography.titleMedium, color = t.text)
            Text("Last 18 weeks", style = MaterialTheme.typography.bodySmall, color = t.textMuted)
            Spacer(Modifier.height(14.dp))
            val weeks = 18
            val start = today.minusDays((today.dayOfWeek.value - 1).toLong()).minusWeeks((weeks - 1).toLong())
            val empty = t.cardAlt
            val accent = t.accentStrong
            Canvas(Modifier.fillMaxWidth().aspectRatio(weeks / 7f)) {
                val gap = 3.dp.toPx()
                val cell = (size.width - gap * (weeks - 1)) / weeks
                for (w in 0 until weeks) for (d in 0 until 7) {
                    val date = start.plusDays((w * 7 + d).toLong())
                    if (date.isAfter(today)) continue
                    val c = completedDays[date.toEpochDay()] ?: 0
                    val color = if (c == 0) empty else accent.copy(alpha = (0.3f + 0.18f * c).coerceAtMost(1f))
                    drawRoundRect(color, Offset(w * (cell + gap), d * (cell + gap)), Size(cell, cell), CornerRadius(cell * 0.3f))
                }
            }
        }

        if (categories.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Card {
                Text("By list", style = MaterialTheme.typography.titleMedium, color = t.text)
                Spacer(Modifier.height(12.dp))
                categories.forEach { c ->
                    val ct = all.filter { it.categoryId == c.id }
                    val done = ct.count { it.completed }
                    val frac = if (ct.isEmpty()) 0f else done / ct.size.toFloat()
                    val col = Palette.vivid.getOrElse(c.colorIndex) { Palette.vivid[0] }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(c.emoji.ifBlank { "•" }, fontSize = 16.sp, modifier = Modifier.width(28.dp))
                        Column(Modifier.weight(1f)) {
                            Row { Text(c.name, style = MaterialTheme.typography.labelLarge, color = t.text, modifier = Modifier.weight(1f)); Text("$done/${ct.size}", style = MaterialTheme.typography.labelMedium, color = t.textMuted) }
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(t.cardAlt)) {
                                Box(Modifier.fillMaxWidth(frac * anim.value).fillMaxHeight().clip(CircleShape).background(col))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(tidy.card).padding(20.dp), content = content)
}

@Composable
private fun StatTile(emoji: String, value: String, label: String, modifier: Modifier, tint: Color) {
    val t = tidy
    val bg = if (t.dark) androidx.compose.ui.graphics.lerp(t.card, tint, 0.12f) else tint
    Column(modifier.clip(RoundedCornerShape(26.dp)).background(bg).padding(18.dp)) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.height(10.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, color = if (t.dark) t.text else Color(0xFF1C1B22))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (t.dark) t.textMuted else Color(0xFF1C1B22).copy(alpha = 0.6f), textAlign = TextAlign.Start)
    }
}
