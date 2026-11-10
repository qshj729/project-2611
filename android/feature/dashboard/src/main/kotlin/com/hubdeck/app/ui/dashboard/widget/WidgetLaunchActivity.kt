package com.hubdeck.app.ui.dashboard.widget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Widget 点击后的中转 Activity：把 URL 交给 Custom Tab，然后立刻退出。
 *
 * 之所以要一个 Activity：Widget 的 PendingIntent 只能直接拉起 Activity，
 * 而 Custom Tab 需要一个 Activity 上下文（契约 C-11 / ADR-007）。
 */
class WidgetLaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent?.getStringExtra(EXTRA_URL)
        if (!url.isNullOrBlank()) {
            CustomTabLauncher.open(this, url)
        }
        finish()
    }

    companion object {
        const val EXTRA_URL = "com.hubdeck.app.ui.dashboard.widget.URL"

        fun intent(context: Context, url: String): Intent =
            Intent(context, WidgetLaunchActivity::class.java).putExtra(EXTRA_URL, url)
    }
}
