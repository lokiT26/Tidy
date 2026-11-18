package com.lokesh.tidy.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lokesh.tidy.data.Category
import com.lokesh.tidy.data.Subtask
import com.lokesh.tidy.data.TaskWithSubtasks
import com.lokesh.tidy.ui.Dates
import com.lokesh.tidy.ui.theme.Palette
import com.lokesh.tidy.ui.theme.tidy
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Soft gradient blobs behind content — gives the glass elements something to sit on. */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val t = tidy
    Box(
        modifier
            .fillMaxSize()
            .background(t.bg)
            .drawBehind {
                drawCircle(Brush.radialGradient(listOf(t.blobA, Color.Transparent), center = Offset(size.width * 0.05f, size.height * 0.02f), radius = size.width * 0.85f), radius = size.width * 0.85f, center = Offset(size.width * 0.05f, size.height * 0.02f))
                drawCircle(Brush.radialGradient(listOf(t.blobB, Color.Transparent), center = Offset(size.width * 1.0f, size.height * 0.16f), radius = size.width * 0.7f), radius = size.width * 0.7f, center = Offset(size.width * 1.0f, size.height * 0.16f))
                drawCircle(Brush.radialGradient(listOf(t.blobA.copy(alpha = t.blobA.alpha * 0.6f), Color.Transparent), center = Offset(size.width * 0.3f, size.height * 1.0f), radius = size.width * 0.8f), radius = size.width * 0.8f, center = Offset(size.width * 0.3f, size.height * 1.0f))
            },
        content = content
    )
}

/** Emulated frosted glass (real blur needs Android 12): translucent fill + top highlight + hairline border + soft shadow. */
fun Modifier.glass(shape: androidx.compose.ui.graphics.Shape, fill: Color, border: Color, dark: Boolean, elevation: Dp = 18.dp): Modifier = this
    .shadow(elevation, shape, ambientColor = Color.Black.copy(alpha = if (dark) 0.5f else 0.10f), spotColor = Color.Black.copy(alpha = if (dark) 0.6f else 0.14f))
    .clip(shape)
    .background(fill)
    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.06f else 0.55f), Color.Transparent)))
    .border(1.dp, Brush.verticalGradient(listOf(border, border.copy(alpha = border.alpha * 0.2f))), shape)

/** Press-to-shrink spring feedback. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bouncyClick(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "s")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(interactionSource = src, indication = null, onClick = onClick, onLongClick = onLongClick)
}

@Composable
fun ProgressRing(progress: Float, modifier: Modifier = Modifier, stroke: Dp = 10.dp, track: Color, colors: List<Color>) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), spring(dampingRatio = 0.8f, stiffness = 60f), label = "ring")
    Canvas(modifier) {
        val s = stroke.toPx()
        val arcSize = Size(size.width - s, size.height - s)
        val tl = Offset(s / 2, s / 2)
        drawArc(track, 0f, 360f, false, tl, arcSize, style = Stroke(s, cap = StrokeCap.Round))
        rotate(-90f) {
            drawArc(Brush.sweepGradient(colors + colors.first()), 0f, 360f * animated, false, tl, arcSize, style = Stroke(s, cap = StrokeCap.Round))
        }
    }
}

@Composable
fun TidyCheckbox(checked: Boolean, color: Color, onToggle: () -> Unit, size: Dp = 26.dp) {
    val t = tidy
    val haptic = LocalHapticFeedback.current
    val fill by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "fill")
    val ringColor = if (color == Palette.priority[0]) t.textFaint else color
    Box(
        Modifier
            .size(size + 14.dp)
            .clip(CircleShape)
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onToggle() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2
            drawCircle(ringColor.copy(alpha = 0.12f + 0.0f * fill), r)
            drawCircle(ringColor, r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            if (fill > 0f) drawCircle(if (color == Palette.priority[0]) t.accentStrong else color, r * fill)
        }
        if (fill > 0.3f) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(size * 0.62f).graphicsLayer { scaleX = fill; scaleY = fill })
    }
}

@Composable
fun MetaChip(text: String, icon: ImageVector? = null, tint: Color = tidy.textMuted, bg: Color = tidy.cardAlt) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { Icon(icon, null, tint = tint, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(3.dp)) }
        Text(text, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp), color = tint, maxLines = 1)
    }
}

@Composable
fun TaskCard(
    item: TaskWithSubtasks,
    category: Category?,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onSubtaskToggle: (Subtask) -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = true,
    dragging: Boolean = false,
) {
    val t = tidy
    val task = item.task
    var expanded by rememberSaveable(task.id) { mutableStateOf(false) }
    val elevation by animateDpAsState(if (dragging) 16.dp else 0.dp, label = "e")
    val alpha by animateFloatAsState(if (task.completed) 0.55f else 1f, label = "a")
    val pColor = Palette.priority[task.priority.coerceIn(0, 3)]
    val today = Dates.today()
    val overdue = !task.completed && task.dueDate != null && task.dueDate < today

    Column(
        modifier
            .fillMaxWidth()
            .shadow(elevation, RoundedCornerShape(24.dp), ambientColor = t.accentStrong, spotColor = t.accentStrong)
            .clip(RoundedCornerShape(24.dp))
            .background(t.card)
            .clickable(onClick = onClick)
            .graphicsLayer { this.alpha = alpha }
            .padding(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
            .animateContentSize(spring(stiffness = 500f))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TidyCheckbox(task.completed, pColor, onToggle)
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = t.text,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                val hasMeta = (showDate && task.dueDate != null) || task.dueTime != null || category != null || item.subtasks.isNotEmpty() || task.repeat != "NONE" || task.reminderOffset != null
                if (hasMeta) {
                    Spacer(Modifier.height(6.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (task.dueDate != null && (showDate || overdue)) {
                            MetaChip(
                                Dates.label(task.dueDate) + (task.dueTime?.let { " · " + Dates.time(it) } ?: ""),
                                Icons.Rounded.CalendarToday,
                                tint = if (overdue) Palette.danger else t.textMuted,
                                bg = if (overdue) Palette.danger.copy(alpha = 0.12f) else t.cardAlt
                            )
                        } else if (task.dueTime != null) {
                            MetaChip(Dates.time(task.dueTime), Icons.Rounded.Schedule)
                        }
                        if (category != null) {
                            val c = Palette.vivid.getOrElse(category.colorIndex) { Palette.vivid[0] }
                            MetaChip("${category.emoji} ${category.name}".trim(), null, tint = c, bg = c.copy(alpha = 0.13f))
                        }
                        if (item.subtasks.isNotEmpty()) {
                            val done = item.subtasks.count { it.done }
                            Box(Modifier.clip(RoundedCornerShape(50)).clickable { expanded = !expanded }) {
                                MetaChip("$done/${item.subtasks.size}", if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.Checklist,
                                    tint = if (done == item.subtasks.size) Palette.success else t.textMuted)
                            }
                        }
                        if (task.repeat != "NONE") MetaChip(Dates.repeatLabel(task.repeat), Icons.Rounded.Repeat)
                        if (task.reminderOffset != null && task.dueDate != null) MetaChip("", Icons.Rounded.NotificationsActive)
                    }
                }
            }
            if (task.priority == 3 && !task.completed) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(pColor))
            }
        }
        if (expanded && item.subtasks.isNotEmpty()) {
            Column(Modifier.padding(start = 44.dp, top = 2.dp)) {
                item.subtasks.sortedBy { it.sortOrder }.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onSubtaskToggle(s) }.padding(vertical = 5.dp)) {
                        Icon(
                            if (s.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                            tint = if (s.done) t.accentStrong else t.textFaint, modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(s.title, style = MaterialTheme.typography.bodyMedium, color = if (s.done) t.textFaint else t.text,
                            textDecoration = if (s.done) TextDecoration.LineThrough else null)
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, count: Int? = null, color: Color = tidy.textMuted, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold), color = color)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text("$count", style = MaterialTheme.typography.labelSmall, color = tidy.textFaint)
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun SelectChip(text: String, selected: Boolean, color: Color = tidy.accentStrong, leading: String? = null, onClick: () -> Unit) {
    val t = tidy
    val bg by animateColorAsState(if (selected) color else t.card, label = "bg")
    val fg by animateColorAsState(if (selected) (if (t.dark) Color(0xFF121214) else Color.White) else t.text, label = "fg")
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else t.divider, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) { Text(leading, fontSize = 13.sp); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
fun IconBubble(icon: ImageVector, onClick: () -> Unit, tint: Color = tidy.text, bg: Color = tidy.card, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.08f)).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.48f)) }
}

@Composable
fun EmptyState(emoji: String, title: String, subtitle: String, modifier: Modifier = Modifier) {
    val t = tidy
    val inf = rememberInfiniteTransition(label = "float")
    val dy by inf.animateFloat(-6f, 6f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dy")
    Column(modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).graphicsLayer { translationY = dy }.clip(CircleShape).background(t.accentSoft), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 42.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = t.text)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = t.textMuted)
    }
}

private data class Particle(val angle: Float, val speed: Float, val color: Color, val size: Float, val spin: Float)

/** One-shot confetti burst. Change [trigger] to fire. */
@Composable
fun Confetti(trigger: Int, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    val colors = listOf(Color(0xFFB9A7FF), Color(0xFFFFC7A8), Color(0xFFA8E6CF), Color(0xFFA8D8FF), Color(0xFFFFE08A), Color(0xFFFFB3C8))
    val particles = remember(trigger) { List(70) { Particle(Random.nextFloat() * 360f, 0.5f + Random.nextFloat(), colors.random(), 6f + Random.nextFloat() * 8f, Random.nextFloat() * 720f) } }
    val progress = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) { progress.animateTo(1f, tween(1600, easing = LinearOutSlowInEasing)) }
    if (progress.value >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        val origin = Offset(size.width / 2, size.height * 0.28f)
        val p = progress.value
        particles.forEach { pt ->
            val dist = pt.speed * size.width * 0.65f * p
            val rad = Math.toRadians(pt.angle.toDouble())
            val x = origin.x + (cos(rad) * dist).toFloat()
            val y = origin.y + (sin(rad) * dist).toFloat() + 900f * p * p
            rotate(pt.spin * p, Offset(x, y)) {
                drawRect(pt.color.copy(alpha = 1f - p), Offset(x, y), Size(pt.size, pt.size * 0.55f))
            }
        }
    }
}
