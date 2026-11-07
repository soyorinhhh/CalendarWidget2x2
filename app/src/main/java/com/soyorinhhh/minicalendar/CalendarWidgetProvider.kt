package com.soyorinhhh.minicalendar

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import java.util.Calendar

/**
 * 2×2 桌面迷你月历。
 *
 * 布局说明：第一行是「‹ 2026年10月 ›」，下方是 7 列 × 6 行的日期网格（固定 42 个格子）。
 * 所有格子都用固定的 View id（d0..d41 / wd0..wd6），因此可以在 RemoteViews 里逐个填充文字，
 * 不需要动态创建 View —— 桌面启动器只接受这种静态布局。
 *
 * 为什么行高写死、不按组件尺寸自适应（v1.3 走过的弯路，记在这里免得再踩）：
 * 1. 各启动器上报的 OPTION_APPWIDGET_MIN/MAX 宽高不可信。实测 MuMu 的 Lawnchair
 *    把像素当 dp 报（真实 230×150px 的组件上报成 250×167dp），按它反推行高必然算大，
 *    最后一行被切掉；
 * 2. RemoteViews 改行高只能走 API 31 的 setViewLayoutHeight，而那套 API 一旦参数用错
 *    （比如把 px 传给 setViewLayoutHeightDimen，它要的是 dimen 资源 ID），
 *    启动器会直接判定「无法加载微件」；
 * 3. 结果：自适应在 2×2 这种小尺寸上收益很小，风险却很高。
 * 用固定行高 + 居中，任何尺寸下行为都一致，只是大尺寸会多留白 —— 这是可接受的。
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
        // 用户拖动改变组件尺寸后重新绘制（翻月状态、今天高亮都要重新算）。
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

        private const val TAG = "MiniCalendar"

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

        /** 一个月的日期最多占 6 行（7 列 × 6 行 = 42 格）。 */
        private const val MAX_DATE_ROWS = 6

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

            // ---------- 标题 ----------
            views.setTextViewText(
                R.id.month_title,
                context.getString(
                    R.string.month_title,
                    shown.get(Calendar.YEAR),
                    shown.get(Calendar.MONTH) + 1
                )
            )

            // 正在浏览其它月份时，标题改成强调色，提示「这不是本月」。
            views.setTextColor(
                R.id.month_title,
                context.getColor(if (offset == 0) R.color.text_primary else R.color.accent)
            )

            // ---------- 本月占几周 ----------
            // 第 6 行在很多月份是完全空的，留着它只会把每格压扁、
            // 或者在最底部留一条空带，所以按真实周数决定显示几行。
            val daysInMonth = shown.getActualMaximum(Calendar.DAY_OF_MONTH)
            val leadingBlanks = if (mondayFirst) {
                (shown.get(Calendar.DAY_OF_WEEK) + 5) % 7
            } else {
                shown.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
            }
            val weeks = ((leadingBlanks + daysInMonth + 6) / 7).coerceIn(1, MAX_DATE_ROWS)

            Log.d(TAG, "render id=$widgetId weeks=$weeks days=$daysInMonth lead=$leadingBlanks")

            // ---------- 星期表头 ----------
            val labels = if (mondayFirst) WEEKDAY_MONDAY_FIRST else WEEKDAY_SUNDAY_FIRST
            val primaryColor = context.getColor(R.color.text_primary)
            val weekendColor = context.getColor(R.color.text_weekend)
            val secondaryColor = context.getColor(R.color.text_secondary)

            WEEKDAY_IDS.forEachIndexed { column, id ->
                views.setTextViewText(id, labels[column])
                views.setTextColor(
                    id,
                    if (isWeekendColumn(column, mondayFirst)) weekendColor else secondaryColor
                )
            }

            // ---------- 日期网格 ----------
            val todayDay = today.get(Calendar.DAY_OF_MONTH)
            val todayTextColor = context.getColor(R.color.today_text)
            val visibleCells = weeks * 7

            DAY_IDS.forEachIndexed { index, id ->
                // 本月用不到的格子直接隐藏：GridLayout 布局时会跳过 GONE 的子 View，
                // 空出来的高度就还给剩下的行，而不是撑出一条空带。
                if (index >= visibleCells) {
                    views.setViewVisibility(id, View.GONE)
                    return@forEachIndexed
                }
                views.setViewVisibility(id, View.VISIBLE)

                val day = index - leadingBlanks + 1
                if (day in 1..daysInMonth) {
                    views.setTextViewText(id, day.toString())
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
    }
}
