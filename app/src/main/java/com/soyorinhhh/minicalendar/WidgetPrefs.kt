package com.soyorinhhh.minicalendar

import android.content.Context
import android.content.SharedPreferences

/**
 * 小组件的轻量配置存储。
 * 每一项都按 appWidgetId 保存，因此桌面上添加多个日历组件时可以各自翻到不同月份。
 */
internal object WidgetPrefs {

    private const val PREFS_NAME = "mini_calendar_prefs"
    private const val KEY_MONDAY_FIRST = "week_start_monday"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 一周是否从周一开始（默认周日开始）。 */
    fun isMondayFirst(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MONDAY_FIRST, false)

    fun setMondayFirst(context: Context, mondayFirst: Boolean) {
        prefs(context).edit().putBoolean(KEY_MONDAY_FIRST, mondayFirst).apply()
    }

    /** 每个组件独立记录的月份偏移，0 表示跟随当前月份。 */
    fun monthOffsetKey(appWidgetId: Int): String = "month_offset_$appWidgetId"

    fun monthOffset(context: Context, appWidgetId: Int): Int =
        prefs(context).getInt(monthOffsetKey(appWidgetId), 0)

    fun setMonthOffset(context: Context, appWidgetId: Int, offset: Int) {
        prefs(context).edit().putInt(monthOffsetKey(appWidgetId), offset).apply()
    }

    fun clearMonthOffset(context: Context, appWidgetId: Int) {
        prefs(context).edit().remove(monthOffsetKey(appWidgetId)).apply()
    }
}
