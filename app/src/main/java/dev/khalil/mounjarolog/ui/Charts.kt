package dev.khalil.mounjarolog.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.khalil.mounjarolog.model.AppData
import dev.khalil.mounjarolog.model.continuitySegments
import dev.khalil.mounjarolog.model.doseContextFor
import dev.khalil.mounjarolog.model.effectiveDoseWindows
import dev.khalil.mounjarolog.model.startingWeight
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max

enum class RangePreset(val label: String) {
    MONTH("1M"),
    TWO_MONTHS("2M"),
    THREE_MONTHS("3M"),
    SIX_MONTHS("6M"),
    YEAR("1Y"),
    ALL("All"),
    CUSTOM("Custom")
}

enum class GraphMode(val label: String, val unit: String) {
    WEIGHT("Weight", "kg"),
    LOST_KG("Weight lost", "kg"),
    LOST_PERCENT("Percent lost", "%"),
    WEEKLY("Weekly rate", "kg/wk"),
    DOSE("Dose", "mg")
}

data class GraphOptions(
    val doseBands: Boolean = true,
    val injectionPosts: Boolean = true,
    val milestones: Boolean = true
)

fun graphStart(
    data: AppData,
    preset: RangePreset,
    customStart: LocalDate?,
    today: LocalDate = LocalDate.now()
): LocalDate = when (preset) {
    RangePreset.MONTH -> today.minusMonths(1)
    RangePreset.TWO_MONTHS -> today.minusMonths(2)
    RangePreset.THREE_MONTHS -> today.minusMonths(3)
    RangePreset.SIX_MONTHS -> today.minusMonths(6)
    RangePreset.YEAR -> today.minusYears(1)
    RangePreset.ALL -> listOfNotNull(
        data.weights.minByOrNull { it.date }?.date,
        data.injections.minByOrNull { it.date }?.date,
        data.dayLogs.minByOrNull { it.date }?.date
    ).minOrNull() ?: today.minusMonths(1)
    RangePreset.CUSTOM -> customStart ?: today.minusMonths(2)
}

@Composable
fun ProgressChart(
    data: AppData,
    mode: GraphMode,
    start: LocalDate,
    end: LocalDate,
    options: GraphOptions,
    modifier: Modifier = Modifier
) {
    val weights = data.weights
        .filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
        .sortedBy { it.date }
    val injections = data.injections
        .filter { !it.date.plusDays(7).isBefore(start) && !it.date.isAfter(end) }
        .sortedWith(compareBy({ it.date }, { it.id }))
    val doseWindows = effectiveDoseWindows(data.injections)
        .filter { it.endExclusive.isAfter(start) && !it.start.isAfter(end) }

    val startWeight = data.startingWeight()?.weightKg
    val points: List<Pair<LocalDate, Double>> = when (mode) {
        GraphMode.WEIGHT -> weights.map { it.date to it.weightKg }
        GraphMode.LOST_KG -> weights.map {
            it.date to ((startWeight ?: it.weightKg) - it.weightKg)
        }
        GraphMode.LOST_PERCENT -> weights.map {
            val base = startWeight ?: it.weightKg
            it.date to if (base == 0.0) 0.0 else ((base - it.weightKg) / base * 100.0)
        }
        GraphMode.WEEKLY -> weights.zipWithNext().mapNotNull { pair ->
            val a = pair.first
            val b = pair.second
            val days = ChronoUnit.DAYS.between(a.date, b.date).toDouble()
            if (days <= 0) null else b.date to ((a.weightKg - b.weightKg) * 7.0 / days)
        }
        GraphMode.DOSE -> injections.map { it.date to it.doseMg }
    }

    if (points.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            EmptyState(
                if (mode == GraphMode.WEEKLY) "Add at least two measurements to see a weekly rate."
                else "No data in this range yet."
            )
        }
        return
    }

    val density = LocalDensity.current
    val surface = MaterialTheme.colorScheme.surface
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val grid = MaterialTheme.colorScheme.outlineVariant

    val rawMin = points.minOf { it.second }
    val rawMax = points.maxOf { it.second }
    val yPad = max(
        when (mode) {
            GraphMode.DOSE -> 1.0
            GraphMode.LOST_PERCENT -> 1.0
            else -> 0.5
        },
        (rawMax - rawMin) * 0.16
    )
    val yMin = if (rawMin == rawMax) rawMin - yPad else rawMin - yPad
    val yMax = if (rawMin == rawMax) rawMax + yPad else rawMax + yPad
    val totalDays = max(1L, ChronoUnit.DAYS.between(start, end)).toFloat()

    val leftPx = with(density) { 46.dp.toPx() }
    val rightPadPx = with(density) { 10.dp.toPx() }
    val topPx = with(density) { 18.dp.toPx() }
    val bottomPadPx = with(density) { 30.dp.toPx() }

    var selectedIndex by remember(points, mode, start, end) { mutableIntStateOf(-1) }

    val gestureModifier = Modifier.pointerInput(points, start, end) {
        detectTapGestures { tap ->
            val chartWidth = (size.width - leftPx - rightPadPx).coerceAtLeast(1f)
            val clampedX = tap.x.coerceIn(leftPx, size.width - rightPadPx)
            val tappedDay = ((clampedX - leftPx) / chartWidth * totalDays).toLong()
            val tappedDate = start.plusDays(tappedDay)
            selectedIndex = points.indices.minByOrNull { index ->
                abs(ChronoUnit.DAYS.between(tappedDate, points[index].first))
            } ?: -1
        }
    }

    Canvas(
        modifier = modifier
            .then(gestureModifier)
            .background(surface, RoundedCornerShape(14.dp))
    ) {
        val right = size.width - rightPadPx
        val bottom = size.height - bottomPadPx
        val chartWidth = (right - leftPx).coerceAtLeast(1f)
        val chartHeight = (bottom - topPx).coerceAtLeast(1f)

        fun x(date: LocalDate): Float {
            val elapsed = ChronoUnit.DAYS.between(start, date).toFloat().coerceIn(0f, totalDays)
            return leftPx + elapsed / totalDays * chartWidth
        }

        fun y(value: Double): Float {
            val fraction = ((value - yMin) / (yMax - yMin)).toFloat().coerceIn(0f, 1f)
            return bottom - fraction * chartHeight
        }

        if (mode != GraphMode.DOSE && options.doseBands) {
            doseWindows.forEach { window ->
                val bandStart = if (window.start.isBefore(start)) start else window.start
                val bandEnd = if (window.endExclusive.isAfter(end)) end else window.endExclusive
                if (bandEnd.isAfter(bandStart)) {
                    val color = doseColor(window.injection.doseMg)
                    val x1 = x(bandStart)
                    val x2 = x(bandEnd)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                color.copy(alpha = 0.025f),
                                color.copy(alpha = 0.20f)
                            ),
                            startY = topPx,
                            endY = bottom
                        ),
                        topLeft = Offset(x1, topPx),
                        size = Size((x2 - x1).coerceAtLeast(2f), chartHeight)
                    )
                }
            }
        }

        if (mode != GraphMode.DOSE && options.injectionPosts) {
            doseWindows.forEach { window ->
                val color = doseColor(window.injection.doseMg)
                if (!window.start.isBefore(start) && !window.start.isAfter(end)) {
                    drawLine(
                        color.copy(alpha = 0.72f),
                        Offset(x(window.start), topPx),
                        Offset(x(window.start), bottom),
                        2.dp.toPx()
                    )
                }
                val endPost = window.endExclusive
                if (!endPost.isBefore(start) && !endPost.isAfter(end)) {
                    drawLine(
                        color.copy(alpha = 0.52f),
                        Offset(x(endPost), topPx),
                        Offset(x(endPost), bottom),
                        2.dp.toPx()
                    )
                }
            }
        }

        repeat(5) { index ->
            val fraction = index / 4f
            val yy = topPx + chartHeight * fraction
            drawLine(
                grid.copy(alpha = 0.65f),
                Offset(leftPx, yy),
                Offset(right, yy),
                1.dp.toPx()
            )
            val labelValue = yMax - (yMax - yMin) * fraction
            drawContext.canvas.nativeCanvas.drawText(
                oneDecimal(labelValue),
                4.dp.toPx(),
                yy + 4.dp.toPx(),
                Paint().apply {
                    color = onSurface.copy(alpha = 0.65f).toArgb()
                    textSize = 10.dp.toPx()
                    isAntiAlias = true
                }
            )
        }

        if (mode == GraphMode.WEIGHT && options.milestones) {
            data.milestones
                .filter { it.targetKg in yMin..yMax }
                .forEach { milestone ->
                    drawLine(
                        Color.Gray.copy(alpha = 0.55f),
                        Offset(leftPx, y(milestone.targetKg)),
                        Offset(right, y(milestone.targetKg)),
                        1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(8.dp.toPx(), 6.dp.toPx())
                        )
                    )
                }
        }

        val dotted = PathEffect.dashPathEffect(
            floatArrayOf(9.dp.toPx(), 7.dp.toPx())
        )

        if (mode == GraphMode.DOSE) {
            points.forEachIndexed { index, point ->
                val next = points.getOrNull(index + 1)
                val x1 = x(point.first)
                val x2 = if (next != null) x(next.first) else right
                val yy = y(point.second)
                drawLine(
                    doseColor(point.second),
                    Offset(x1, yy),
                    Offset(x2, yy),
                    3.dp.toPx()
                )
                if (next != null) {
                    drawLine(
                        doseColor(next.second),
                        Offset(x2, yy),
                        Offset(x2, y(next.second)),
                        2.dp.toPx()
                    )
                }
            }
        } else {
            points.zipWithNext().forEach { pair ->
                val a = pair.first
                val b = pair.second
                val totalSpanDays = ChronoUnit.DAYS.between(a.first, b.first)

                if (totalSpanDays <= 0) {
                    drawLine(
                        primary,
                        Offset(x(a.first), y(a.second)),
                        Offset(x(b.first), y(b.second)),
                        3.dp.toPx()
                    )
                } else {
                    fun valueAt(date: LocalDate): Double {
                        val elapsed = ChronoUnit.DAYS.between(a.first, date).toDouble()
                        val fraction = elapsed / totalSpanDays.toDouble()
                        return a.second + (b.second - a.second) * fraction
                    }

                    continuitySegments(
                        start = a.first,
                        end = b.first,
                        injectionDates = data.injections.map { it.date }
                    ).forEach { segment ->
                        drawLine(
                            primary,
                            Offset(x(segment.start), y(valueAt(segment.start))),
                            Offset(x(segment.end), y(valueAt(segment.end))),
                            3.dp.toPx(),
                            pathEffect = if (segment.dotted) dotted else null
                        )
                    }
                }
            }

            if (
                mode in listOf(GraphMode.WEIGHT, GraphMode.LOST_KG, GraphMode.LOST_PERCENT) &&
                points.last().first.isBefore(end)
            ) {
                val last = points.last()
                continuitySegments(
                    start = last.first,
                    end = end,
                    injectionDates = data.injections.map { it.date }
                ).forEach { segment ->
                    drawLine(
                        primary.copy(alpha = 0.72f),
                        Offset(x(segment.start), y(last.second)),
                        Offset(x(segment.end), y(last.second)),
                        2.dp.toPx(),
                        pathEffect = if (segment.dotted) dotted else null
                    )
                }
            }
        }

        points.forEachIndexed { index, point ->
            val pointColor = when (mode) {
                GraphMode.WEIGHT, GraphMode.LOST_KG, GraphMode.LOST_PERCENT -> {
                    val context = data.doseContextFor(point.first)
                    doseColor(context.doseMg)
                }
                GraphMode.DOSE -> doseColor(point.second)
                GraphMode.WEEKLY -> primary
            }
            drawCircle(
                pointColor,
                radius = if (selectedIndex == index) 7.dp.toPx() else 5.dp.toPx(),
                center = Offset(x(point.first), y(point.second))
            )
            if (selectedIndex == index) {
                drawCircle(
                    surface,
                    radius = 3.dp.toPx(),
                    center = Offset(x(point.first), y(point.second))
                )
            }
        }

        val axisPaint = Paint().apply {
            color = onSurface.copy(alpha = 0.68f).toArgb()
            textSize = 10.dp.toPx()
            isAntiAlias = true
        }
        drawContext.canvas.nativeCanvas.drawText(
            start.format(DisplayDateFormatter),
            leftPx,
            size.height - 8.dp.toPx(),
            axisPaint
        )
        val endLabel = end.format(DisplayDateFormatter)
        axisPaint.textAlign = Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText(
            endLabel,
            right,
            size.height - 8.dp.toPx(),
            axisPaint
        )

        if (selectedIndex in points.indices) {
            val selected = points[selectedIndex]
            val px = x(selected.first)
            val py = y(selected.second)
            val label = selected.first.format(DisplayDateFormatter) + " · " +
                oneDecimal(selected.second) + " " + mode.unit
            val tooltipPaint = Paint().apply {
                color = onSurface.toArgb()
                textSize = 11.dp.toPx()
                isAntiAlias = true
                textAlign = if (px > size.width * 0.62f) Paint.Align.RIGHT else Paint.Align.LEFT
            }
            val textX = if (px > size.width * 0.62f) px - 8.dp.toPx() else px + 8.dp.toPx()
            val textY = (py - 12.dp.toPx()).coerceAtLeast(topPx + 10.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(label, textX, textY, tooltipPaint)
        }
    }
}

@Composable
fun CompactProgressChart(
    data: AppData,
    modifier: Modifier = Modifier
) {
    ProgressChart(
        data = data,
        mode = GraphMode.WEIGHT,
        start = graphStart(data, RangePreset.TWO_MONTHS, null),
        end = LocalDate.now(),
        options = GraphOptions(),
        modifier = modifier.fillMaxWidth().height(230.dp)
    )
}
