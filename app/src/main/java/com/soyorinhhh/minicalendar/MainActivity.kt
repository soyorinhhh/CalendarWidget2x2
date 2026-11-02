package com.soyorinhhh.minicalendar

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * 应用主界面：说明怎么添加小组件 + 一个「一键添加」按钮 + 少量设置。
 * 日历本身是桌面小组件，这里只负责安装后的引导。
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Switch>(R.id.switch_monday_first).apply {
            isChecked = WidgetPrefs.isMondayFirst(this@MainActivity)
            setOnCheckedChangeListener { _, isChecked ->
                WidgetPrefs.setMondayFirst(this@MainActivity, isChecked)
                // 设置改了以后立刻重绘桌面上所有组件。
                CalendarWidgetProvider.refreshAll(this@MainActivity)
            }
        }

        findViewById<Button>(R.id.button_add_widget).setOnClickListener { pinWidget() }
        findViewById<Button>(R.id.button_open_calendar).setOnClickListener { openCalendar() }

        findViewById<TextView>(R.id.text_version).text =
            getString(R.string.version_label, versionName())
    }

    @Suppress("DEPRECATION")
    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
    } catch (e: Exception) {
        "1.0"
    }

    /** Android 8+ 支持直接弹出「添加到桌面」的确认框。 */
    private fun pinWidget() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            toast(R.string.pin_unsupported)
            return
        }
        val manager = getSystemService(AppWidgetManager::class.java) ?: run {
            toast(R.string.pin_unsupported)
            return
        }
        if (!manager.isRequestPinAppWidgetSupported) {
            toast(R.string.pin_unsupported)
            return
        }
        val provider = ComponentName(this, CalendarWidgetProvider::class.java)
        if (manager.requestPinAppWidget(provider, null, null)) {
            toast(R.string.pin_requested)
        } else {
            toast(R.string.pin_unsupported)
        }
    }

    private fun openCalendar() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast(R.string.no_calendar_app)
        }
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }
}
