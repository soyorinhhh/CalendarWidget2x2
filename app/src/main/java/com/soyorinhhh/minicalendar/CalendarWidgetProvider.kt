package com.soyorinhhh.minicalendar

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import java.util.Calendar

/**
 * 2×2 桌面迷你月历。
 *
 * 布局说明：第一行是「‹ 2026年10月 ›」，下方是 7 列 × 6 行的日期网格（固定 42 个格子）。
 * 所有格子都用固定的 View id（d0..d41 / wd0..wd6），因此可以在 RemoteViews 里逐个填充文字，
 * 不需要动态创建 View —— 桌面启动器只接受这种静态布局。
 */
class CalendarWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { render(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        // 用户拖动改变组件尺寸后，按新的高度重新计算字号。
        render(context, appWidgetManager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPrefs.clearMonthOffset(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PREV_MONTH, ACTION_NEXT_MONTH, ACTION_TODAY -> {
                val widgetId = intent.getIntExtra(
                    EXTRA_WIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    handleNavigation(context, widgetId, intent.action)
                }
            }

            // 跨天、改时间、换时区、换语言后需要重新绘制（否则日期会一直停在昨天）。
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED -> refreshAll(context)
        }
        super.onReceive(context, intent)
    }

    companion object {

        private const val ACTION_PREV_MONTH = "com.soyorinhhh.minicalendar.ACTION_PREV_MONTH"
        private const val ACTION_NEXT_MONTH = "com.soyorinhhh.minicalendar.ACTION_NEXT_MONTH"
        private const val ACTION_TODAY = "com.soyorinhhh.minicalendar.ACTION_TODAY"
        private const val EXTRA_WIDGET_ID = "extra_widget_id"

        // 每个组件需要 4 个互不相同的 PendingIntent 请求码，用 id * 10 + 槽位 区分。
        private const val SLOT_PREV = 1
        private const val SLOT_NEXT = 2
        private const val SLOT_TODAY = 3
        private const val SLOT_OPEN = 4

        private const val MAX_MONTH_OFFSET = 120

        private val WEEKDAY_IDS = intArrayOf(
            R.id.wd0, R.id.wd1, R.id.wd2, R.id.wd3, R.id.wd4, R.id.wd5, R.id.wd6
        )

        private val DAY_IDS = intArrayOf(
            R.id.d0, R.id.d1, R.id.d2, R.id.d3, R.id.d4, R.id.d5, R.id.d6,
            R.id.d7, R.id.d8, R.id.d9, R.id.d10, R.id.d11, R.id.d12, R.id.d13,
            R.id.d14, R.id.d15, R.id.d16, R.id.d17, R.id.d18, R.id.d19, R.id.d20,
            R.id.d21, R.id.d22, R.id.d23, R.id.d24, R.id.d25, R.id.d26, R.id.d27,
            R.id.d28, R.id.d29, R.id.d30, R.id.d31, R.id.d32, R.id.d33, R.id.d34,
            R.id.d35, R.id.d36, R.id.d37, R.id.d38, R.id.d39, R.id.d40, R.id.d41
        )

        private val WEEKDAY_SUNDAY_FIRST = arrayOf("日", "一", "二", "三", "四", "五", "六")
        private val WEEKDAY_MONDAY_FIRST = arrayOf("一", "二", "三", "四", "五", "六", "日")

        /** 刷新桌面上所有已添加的日历组件（设置变更、跨天时调用）。 */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CalendarWidgetProvider::class.java)
            manager.getAppWidgetIds(component)
                .forEach { render(context, manager, it) }
        }

        private fun handleNavigation(context: Context, widgetId: Int, action: String?) {
            val current = WidgetPrefs.monthOffset(context, widgetId)
            val next = when (action) {
                ACTION_PREV_MONTH -> (current - 1).coerceAtLeast(-MAX_MONTH_OFFSET)
                ACTION_NEXT_MONTH -> (current + 1).coerceAtMost(MAX_MONTH_OFFSET)
                else -> 0
            }
            WidgetPrefs.setMonthOffset(context, widgetId, next)

            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CalendarWidgetProvider::class.java)
            if (manager.getAppWidgetIds(component).contains(widgetId)) {
                render(context, manager, widgetId)
            }
        }

        private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.calendar_widget)

            val today = Calendar.getInstance()
            val offset = WidgetPrefs.monthOffset(context, widgetId)
            val mondayFirst = WidgetPrefs.isMondayFirst(context)

            val shown = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, offset)
            }

            // ---------- 标题 & 字号 ----------
            views.setTextViewText(
                R.id.month_title,
                context.getString(
                    R.string.month_title,
                    shown.get(Calendar.YEAR),
                    shown.get(Calendar.MONTH) + 1
                )
            )

            val sizes = fontSizes(availableHeightDp(manager, widgetId))
            views.setTextViewTextSize(
                R.id.month_title, TypedValue.COMPLEX_UNIT_SP, sizes.titleSp
            )
            views.setTextViewTextSize(R.id.btn_prev, TypedValue.COMPLEX_UNIT_SP, sizes.arrowSp)
            views.setTextViewTextSize(R.id.btn_next, TypedValue.COMPLEX_UNIT_SP, sizes.arrowSp)

            // 正在浏览其它月份时，标题改成强调色，提示「这不是本月」。
            views.setTextColor(
                R.id.month_title,
                context.getColor(if (offset == 0) R.color.text_primary else R.color.accent)
            )

            // ---------- 星期表头 ----------
            val labels = if (mondayFirst) WEEKDAY_MONDAY_FIRST else WEEKDAY_SUNDAY_FIRST
            val primaryColor = context.getColor(R.color.text_primary)
            val weekendColor = context.getColor(R.color.text_weekend)
            val secondaryColor = context.getColor(R.color.text_secondary)

            WEEKDAY_IDS.forEachIndexed { column, id ->
                views.setTextViewText(id, labels[column])
                views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sizes.weekdaySp)
                views.setTextColor(
                    id,
                    if (isWeekendColumn(column, mondayFirst)) weekendColor else secondaryColor
                )
            }

            // ---------- 日期网格 ----------
            val daysInMonth = shown.getActualMaximum(Calendar.DAY_OF_MONTH)
            val leadingBlanks = if (mondayFirst) {
                (shown.get(Calendar.DAY_OF_WEEK) + 5) % 7
            } else {
                shown.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
            }
            val todayDay = today.get(Calendar.DAY_OF_MONTH)
            val todayTextColor = context.getColor(R.color.today_text)

            DAY_IDS.forEachIndexed { index, id ->
                val day = index - leadingBlanks + 1
                if (day in 1..daysInMonth) {
                    views.setTextViewText(id, day.toString())
                    views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sizes.daySp)
                    if (offset == 0 && day == todayDay) {
                        views.setTextColor(id, todayTextColor)
                        views.setInt(id, "setBackgroundResource", R.drawable.day_today_bg)
                    } else {
                        views.setTextColor(
                            id,
                            if (isWeekendColumn(index % 7, mondayFirst)) weekendColor
                            else primaryColor
                        )
                        views.setInt(id, "setBackgroundResource", 0)
                    }
                } else {
                    views.setTextViewText(id, "")
                    views.setInt(id, "setBackgroundResource", 0)
                }
            }

            // ---------- 交互 ----------
            views.setOnClickPendingIntent(
                R.id.btn_prev,
                broadcast(context, widgetId, ACTION_PREV_MONTH, SLOT_PREV)
            )
            views.setOnClickPendingIntent(
                R.id.btn_next,
                broadcast(context, widgetId, ACTION_NEXT_MONTH, SLOT_NEXT)
            )
            views.setOnClickPendingIntent(
                R.id.month_title,
                broadcast(context, widgetId, ACTION_TODAY, SLOT_TODAY)
            )
            views.setOnClickPendingIntent(R.id.widget_root, openCalendar(context, widgetId))

            views.setContentDescription(
                R.id.btn_prev, context.getString(R.string.action_prev_month)
            )
            views.setContentDescription(
                R.id.btn_next, context.getString(R.string.action_next_month)
            )
            views.setContentDescription(
                R.id.month_title, context.getString(R.string.action_today)
            )

            manager.updateAppWidget(widgetId, views)
        }

        /** 组件当前高度（dp）。尺寸信息拿不到时退回 2×2 的默认高度。 */
        private fun availableHeightDp(manager: AppWidgetManager, widgetId: Int): Int {
            val options = manager.getAppWidgetOptions(widgetId)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            return if (height > 0) height else 110
        }

        /**
         * 按组件高度推算字号：2×2 大约 8sp，拉到 4×4 时能长到 14sp。
         * 网格固定 7 行（1 行表头 + 6 行日期），所以每行高度约等于 (高度 - 标题区) / 7。
         */
        private fun fontSizes(heightDp: Int): FontSizes {
            val rowHeight = ((heightDp - 28f) / 7f).coerceIn(9f, 40f)
            return FontSizes(
                daySp = (rowHeight * 0.62f).coerceIn(8f, 14f),
                weekdaySp = (rowHeight * 0.50f).coerceIn(7f, 11f),
                titleSp = (rowHeight * 0.72f).coerceIn(10f, 17f),
                arrowSp = (rowHeight * 0.80f).coerceIn(11f, 20f)
            )
        }

        private fun isWeekendColumn(column: Int, mondayFirst: Boolean): Boolean =
            if (mondayFirst) column >= 5 else column == 0 || column == 6

        private fun broadcast(
            context: Context,
            widgetId: Int,
            action: String,
            slot: Int
        ): PendingIntent {
            val intent = Intent(context, CalendarWidgetProvider::class.java)
                .setAction(action)
                .putExtra(EXTRA_WIDGET_ID, widgetId)
            return PendingIntent.getBroadcast(
                context,
                widgetId * 10 + slot,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** 点组件空白处打开系统日历；找不到日历应用就回到本应用的说明页。 */
        private fun openCalendar(context: Context, widgetId: Int): PendingIntent {
            val calendarIntent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_APP_CALENDAR)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            val target = if (calendarIntent.resolveActivity(context.packageManager) != null) {
                calendarIntent
            } else {
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            return PendingIntent.getActivity(
                context,
                widgetId * 10 + SLOT_OPEN,
                target,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private data class FontSizes(
            val daySp: Float,
            val weekdaySp: Float,
            val titleSp: Float,
            val arrowSp: Float
        )
    }
}
