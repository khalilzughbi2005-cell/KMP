package dev.khalil.mounjarolog.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.widget.RemoteViews
import dev.khalil.mounjarolog.MainActivity
import dev.khalil.mounjarolog.R
import dev.khalil.mounjarolog.data.LoggerDatabase
import dev.khalil.mounjarolog.model.AppData
import dev.khalil.mounjarolog.model.continuitySegments
import dev.khalil.mounjarolog.model.effectiveDoseWindows
import dev.khalil.mounjarolog.model.latestInjection
import dev.khalil.mounjarolog.model.latestWeight
import dev.khalil.mounjarolog.model.startingWeight
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max

object WidgetActions {
    const val EXTRA_QUICK_ADD_MODE = "widget_quick_add_mode"
    const val MODE_WEIGHT = "weight"
    const val MODE_DOSE = "dose"
    const val MODE_BOTH = "both"
}

class QuickAddWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        HomeWidgetUpdater.updateQuickAdd(context, manager, ids)
    }
}

class GraphWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        HomeWidgetUpdater.updateGraph(context, manager, ids)
    }
}

class CompactWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        HomeWidgetUpdater.updateCompact(context, manager, ids)
    }
}

class DashboardWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        HomeWidgetUpdater.updateDashboard(context, manager, ids)
    }
}

object HomeWidgetUpdater {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        updateQuickAdd(
            context,
            manager,
            manager.getAppWidgetIds(ComponentName(context, QuickAddWidgetProvider::class.java))
        )
        updateGraph(
            context,
            manager,
            manager.getAppWidgetIds(ComponentName(context, GraphWidgetProvider::class.java))
        )
        updateCompact(
            context,
            manager,
            manager.getAppWidgetIds(ComponentName(context, CompactWidgetProvider::class.java))
        )
        updateDashboard(
            context,
            manager,
            manager.getAppWidgetIds(ComponentName(context, DashboardWidgetProvider::class.java))
        )
    }

    fun updateQuickAdd(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val data = loadData(context)
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_quick_add)
            bindHeaderStats(views, data)
            views.setOnClickPendingIntent(
                R.id.widget_add_weight,
                quickAddIntent(context, 1000 + id, WidgetActions.MODE_WEIGHT)
            )
            views.setOnClickPendingIntent(
                R.id.widget_add_dose,
                quickAddIntent(context, 2000 + id, WidgetActions.MODE_DOSE)
            )
            views.setOnClickPendingIntent(
                R.id.widget_add_both,
                quickAddIntent(context, 3000 + id, WidgetActions.MODE_BOTH)
            )
            manager.updateAppWidget(id, views)
        }
    }

    fun updateGraph(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val data = loadData(context)
        val bitmap = WidgetGraphRenderer.render(data, width = 760, height = 280)
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_graph)
            bindHeaderStats(views, data)
            views.setImageViewBitmap(R.id.widget_graph_image, bitmap)
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context, 4000 + id))
            manager.updateAppWidget(id, views)
        }
    }

    fun updateCompact(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val data = loadData(context)
        val bitmap = WidgetGraphRenderer.render(data, width = 700, height = 190)
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_compact)
            bindHeaderStats(views, data)
            views.setImageViewBitmap(R.id.widget_graph_image, bitmap)
            views.setOnClickPendingIntent(
                R.id.widget_add_both,
                quickAddIntent(context, 5000 + id, WidgetActions.MODE_BOTH)
            )
            views.setOnClickPendingIntent(R.id.widget_graph_image, openAppIntent(context, 6000 + id))
            manager.updateAppWidget(id, views)
        }
    }

    fun updateDashboard(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val data = loadData(context)
        val latest = data.latestWeight()
        val start = data.startingWeight()
        val injection = data.latestInjection()
        val totalLost = if (latest != null && start != null) start.weightKg - latest.weightKg else null
        val activeContext = latest?.date?.let { data.doseContextFor(it) }

        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_dashboard)
            views.setTextViewText(
                R.id.widget_total_lost,
                totalLost?.let { oneDecimal(it) + " kg" } ?: "—"
            )
            views.setTextViewText(
                R.id.widget_last_weight,
                latest?.let { oneDecimal(it.weightKg) + " kg" } ?: "—"
            )
            views.setTextViewText(
                R.id.widget_current_dose,
                injection?.let { doseText(it.doseMg) } ?: "—"
            )
            views.setTextViewText(
                R.id.widget_dose_window,
                when {
                    activeContext?.doseMg != null -> "Day " + activeContext.dayInWindow + " / 7"
                    injection != null -> "Outside window"
                    else -> "—"
                }
            )
            views.setTextViewText(R.id.widget_entry_count, data.weights.size.toString())
            views.setTextViewText(
                R.id.widget_last_date,
                latest?.date?.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())) ?: "—"
            )
            views.setOnClickPendingIntent(
                R.id.widget_add_weight,
                quickAddIntent(context, 7000 + id, WidgetActions.MODE_WEIGHT)
            )
            views.setOnClickPendingIntent(
                R.id.widget_add_dose,
                quickAddIntent(context, 8000 + id, WidgetActions.MODE_DOSE)
            )
            views.setOnClickPendingIntent(
                R.id.widget_add_both,
                quickAddIntent(context, 9000 + id, WidgetActions.MODE_BOTH)
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context, 10000 + id))
            manager.updateAppWidget(id, views)
        }
    }

    private fun bindHeaderStats(views: RemoteViews, data: AppData) {
        val latest = data.latestWeight()
        val injection = data.latestInjection()
        views.setTextViewText(
            R.id.widget_latest_weight,
            latest?.let { oneDecimal(it.weightKg) + " kg" } ?: "No weight yet"
        )
        views.setTextViewText(
            R.id.widget_latest_dose,
            injection?.let { doseText(it.doseMg) } ?: "No dose yet"
        )
    }

    private fun loadData(context: Context): AppData {
        val database = LoggerDatabase(context.applicationContext)
        return try {
            database.loadAll()
        } finally {
            database.close()
        }
    }

    private fun quickAddIntent(context: Context, requestCode: Int, mode: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(WidgetActions.EXTRA_QUICK_ADD_MODE, mode)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openAppIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun oneDecimal(value: Double): String =
        String.format(Locale.getDefault(), "%.1f", value)

    private fun doseText(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() + " mg"
        else oneDecimal(value) + " mg"
}

private object WidgetGraphRenderer {
    fun render(data: AppData, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        val weights = data.weights.sortedBy { it.date }
        if (weights.isEmpty()) {
            drawCenteredText(canvas, width, height, "Add a weight to start the graph")
            return bitmap
        }

        val end = LocalDate.now()
        val earliest = weights.first().date
        val sixtyDays = end.minusDays(60)
        val start = if (earliest.isAfter(sixtyDays)) earliest else sixtyDays
        val visible = weights.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }

        if (visible.isEmpty()) {
            drawCenteredText(canvas, width, height, "No recent weights")
            return bitmap
        }

        val left = 18f
        val right = width - 18f
        val top = 12f
        val bottom = height - 18f
        val chartWidth = right - left
        val chartHeight = bottom - top
        val totalDays = max(1L, ChronoUnit.DAYS.between(start, end)).toFloat()

        val minWeight = visible.minOf { it.weightKg }
        val maxWeight = visible.maxOf { it.weightKg }
        val pad = max(0.6, (maxWeight - minWeight) * 0.16)
        val yMin = minWeight - pad
        val yMax = maxWeight + pad

        fun x(date: LocalDate): Float {
            val days = ChronoUnit.DAYS.between(start, date).toFloat().coerceIn(0f, totalDays)
            return left + days / totalDays * chartWidth
        }

        fun y(weight: Double): Float {
            val f = ((weight - yMin) / (yMax - yMin)).toFloat().coerceIn(0f, 1f)
            return bottom - f * chartHeight
        }

        val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        effectiveDoseWindows(data.injections)
            .filter { it.endExclusive.isAfter(start) && !it.start.isAfter(end) }
            .forEach { window ->
                val s = if (window.start.isBefore(start)) start else window.start
                val e = if (window.endExclusive.isAfter(end)) end else window.endExclusive
                if (e.isAfter(s)) {
                    bandPaint.color = doseColor(window.injection.doseMg, alpha = 42)
                    canvas.drawRect(x(s), top, x(e), bottom, bandPaint)
                }
            }

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(23, 107, 82)
            strokeWidth = 5f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val dottedPaint = Paint(linePaint).apply {
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(13f, 10f), 0f)
        }

        visible.zipWithNext().forEach { pair ->
            val a = pair.first
            val b = pair.second
            val total = ChronoUnit.DAYS.between(a.date, b.date)
            if (total <= 0) {
                canvas.drawLine(x(a.date), y(a.weightKg), x(b.date), y(b.weightKg), linePaint)
            } else {
                fun valueAt(date: LocalDate): Double {
                    val elapsed = ChronoUnit.DAYS.between(a.date, date).toDouble()
                    return a.weightKg + (b.weightKg - a.weightKg) * elapsed / total.toDouble()
                }
                continuitySegments(
                    a.date,
                    b.date,
                    data.injections.map { it.date }
                ).forEach { segment ->
                    canvas.drawLine(
                        x(segment.start),
                        y(valueAt(segment.start)),
                        x(segment.end),
                        y(valueAt(segment.end)),
                        if (segment.dotted) dottedPaint else linePaint
                    )
                }
            }
        }

        val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        visible.forEach { weight ->
            pointPaint.color = doseColor(data.doseContextFor(weight.date).doseMg)
            canvas.drawCircle(x(weight.date), y(weight.weightKg), 7f, pointPaint)
        }

        return bitmap
    }

    private fun drawCenteredText(canvas: Canvas, width: Int, height: Int, text: String) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(90, 100, 95)
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(text, width / 2f, height / 2f, paint)
    }

    private fun doseColor(dose: Double?, alpha: Int = 255): Int {
        val base = when (dose) {
            5.0 -> Color.rgb(46, 125, 50)
            7.5 -> Color.rgb(25, 118, 210)
            10.0 -> Color.rgb(106, 27, 154)
            12.5 -> Color.rgb(239, 108, 0)
            15.0 -> Color.rgb(198, 40, 40)
            else -> Color.rgb(117, 117, 117)
        }
        return Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
    }
}
