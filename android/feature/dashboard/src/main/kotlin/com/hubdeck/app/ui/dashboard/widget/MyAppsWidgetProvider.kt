package com.hubdeck.app.ui.dashboard.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * 桌面 Widget：展示收藏服务，点击直达 Custom Tab。
 *
 * 数据来自与仪表盘同一套端口（C-02 / C-05 / C-09），
 * 所以 Widget 与 App 里的收藏列表永远一致。
 */
class MyAppsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { widgetId ->
            val views = RemoteViews(
                context.packageName,
                com.hubdeck.feature.dashboard.R.layout.widget_myapps,
            )
            // 显式指向 RemoteViewsService：系统会去绑定它，于是 Hilt 的注入能生效。
            @Suppress("DEPRECATION")
            views.setRemoteAdapter(
                com.hubdeck.feature.dashboard.R.id.widget_list,
                Intent(context, WidgetRemoteViewsService::class.java),
            )
            views.setEmptyView(
                com.hubdeck.feature.dashboard.R.id.widget_list,
                com.hubdeck.feature.dashboard.R.layout.widget_myapps_empty,
            )
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            onUpdate(
                context,
                AppWidgetManager.getInstance(context),
                intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: IntArray(0),
            )
        }
    }
}
