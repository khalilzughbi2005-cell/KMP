package dev.khalil.mounjarolog.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.res.ResourcesCompat
import dev.khalil.mounjarolog.R
import dev.khalil.mounjarolog.model.DosePhase
import dev.khalil.mounjarolog.model.Milestone
import dev.khalil.mounjarolog.model.TrendPoint
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max

enum class RangePreset(val label: String, val months: Long?) {
    ONE("1 M", 1), THREE("3 M", 3), SIX("6 M", 6), YEAR("1 J", 12), ALL("Alles", null)
}

private val MonthLabel = DateTimeFormatter.ofPattern("MMM", German)

/**
 * Smoothed trend as the main line, the actual daily weigh-ins as faint dots.
 * Dose changes are thin markers, milestones dashed lines. Tap shows a value.
 */
@Composable
fun TrendChart(
    series: List<TrendPoint>,
    phases: List<DosePhase>,
    milestones: List<Milestone>,
    start: LocalDate,
    end: LocalDate,
    modifier: Modifier = Modifier,
    yAxis: Boolean = false
) {
    val points = series.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
    if (points.isEmpty()) {
        EmptyState("Noch keine Messungen in diesem Zeitraum.")
        return
    }
    val context = LocalContext.current
    val typeface = remember { ResourcesCompat.getFont(context, R.font.funnel_sans_medium) }
    var selected by remember(points) { mutableStateOf<TrendPoint?>(null) }

    val lo = points.minOf { minOf(it.weightKg, it.trendKg) }
    val hi = points.maxOf { maxOf(it.weightKg, it.trendKg) }
    val pad = max(0.6, (hi - lo) * 0.12)
    val yMin = lo - pad
    val yMax = hi + pad
    val totalDays = max(1L, ChronoUnit.DAYS.between(start, end)).toFloat()
    val visibleGoals = milestones.filter { it.targetKg in yMin..yMax }
    val description = "Gewichtstrend von " + oneDecimal(points.first().trendKg) + " auf " + oneDecimal(points.last().trendKg) + " Kilogramm"

    Canvas(
        modifier
            .semantics { contentDescription = description }
            .pointerInput(points, start, end) {
                detectTapGestures { tap ->
                    val day = start.plusDays((tap.x / size.width * totalDays).toLong())
                    selected = points.minByOrNull { abs(ChronoUnit.DAYS.between(day, it.date)) }
                }
            }
    ) {
        val native = drawContext.canvas.nativeCanvas
        val labelPx = 11 * density
        val left = 0f
        val right = size.width - if (yAxis) 34 * density else 12 * density
        val top = 22 * density
        val bottom = size.height - 22 * density
        fun x(date: LocalDate) = left + ChronoUnit.DAYS.between(start, date).toFloat().coerceIn(0f, totalDays) / totalDays * (right - left)
        fun y(kg: Double) = (bottom - (kg - yMin) / (yMax - yMin) * (bottom - top)).toFloat()
        val paint = Paint().apply {
            isAntiAlias = true
            textSize = labelPx
            this.typeface = typeface
            color = Faint.toArgb()
        }

        if (yAxis) {
            val step = if (yMax - yMin > 12) 5.0 else if (yMax - yMin > 4) 2.0 else 1.0
            var v = kotlin.math.ceil(yMin / step) * step
            while (v <= yMax) {
                drawLine(Hairline, Offset(left, y(v)), Offset(right, y(v)), density)
                native.drawText(v.toInt().toString(), right + 6 * density, y(v) + labelPx / 3, paint)
                v += step
            }
        }

        // dose changes
        paint.color = Muted.toArgb()
        phases.filter { !it.start.isBefore(start) && !it.start.isAfter(end) }.forEach { phase ->
            val px = x(phase.start)
            drawLine(Ink.copy(alpha = 0.12f), Offset(px, top - 8 * density), Offset(px, bottom), density)
            native.drawText(formatDose(phase.doseMg), px + 5 * density, top - 6 * density, paint)
        }

        // milestones
        visibleGoals.forEach { goal ->
            drawLine(
                Ink.copy(alpha = 0.35f), Offset(left, y(goal.targetKg)), Offset(right, y(goal.targetKg)),
                density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4 * density, 5 * density))
            )
        }

        // area + trend line
        val line = Path()
        points.forEachIndexed { i, p -> if (i == 0) line.moveTo(x(p.date), y(p.trendKg)) else line.lineTo(x(p.date), y(p.trendKg)) }
        val area = Path().apply {
            addPath(line)
            lineTo(x(points.last().date), bottom)
            lineTo(x(points.first().date), bottom)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(Ink.copy(alpha = 0.07f), Ink.copy(alpha = 0f)), top, bottom))
        points.forEach { drawCircle(Faint, 2.4f * density, Offset(x(it.date), y(it.weightKg))) }
        drawPath(line, Ink, style = Stroke(3 * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val last = points.last()
        drawCircle(Paper, 6.5f * density, Offset(x(last.date), y(last.trendKg)))
        drawCircle(Ink, 6.5f * density, Offset(x(last.date), y(last.trendKg)), style = Stroke(3 * density))

        // month ticks
        paint.color = Faint.toArgb()
        var month = start.withDayOfMonth(1).plusMonths(1)
        val monthStep = if (totalDays > 200) 2L else 1L
        if (totalDays < 45) month = start
        while (!month.isAfter(end)) {
            val label = if (totalDays < 45) month.format(ShortDateFormatter) else month.format(MonthLabel)
            native.drawText(label, x(month) + 2 * density, size.height - 4 * density, paint)
            month = if (totalDays < 45) month.plusDays(7) else month.plusMonths(monthStep)
        }

        selected?.let { p ->
            val px = x(p.date)
            drawLine(Ink.copy(alpha = 0.3f), Offset(px, top), Offset(px, bottom), density)
            drawCircle(Ink, 4 * density, Offset(px, y(p.weightKg)))
            val text = p.date.format(ShortDateFormatter) + " · " + oneDecimal(p.weightKg) + " kg · Trend " + oneDecimal(p.trendKg)
            paint.color = Ink.toArgb()
            paint.textAlign = if (px > size.width / 2) Paint.Align.RIGHT else Paint.Align.LEFT
            native.drawText(text, px + if (px > size.width / 2) -6 * density else 6 * density, top + labelPx, paint)
            paint.textAlign = Paint.Align.LEFT
        }
    }
}

fun rangeStart(preset: RangePreset, series: List<TrendPoint>, today: LocalDate): LocalDate {
    val first = series.firstOrNull()?.date ?: today.minusMonths(1)
    val wanted = preset.months?.let { today.minusMonths(it) } ?: first
    return maxOf(wanted, first.minusDays(1))
}
