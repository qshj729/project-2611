package com.hubdeck.app.ui.dashboard.widget

import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/**
 * Widget 点击后打开 Custom Tab。
 *
 * 与 `:app` 的 `com.hubdeck.app.ui.ServiceOpener`（契约 C-11）等价：
 * Custom Tabs 优先，失败回退 `ACTION_VIEW`，永远不内置 WebView（ADR-007）。
 *
 * 为什么这里自己有一份：Widget 位于 `:feature:dashboard`，
 * 按冻结契约 §6 不能反向依赖 `:app`，而 C-11 的实现还留在装配壳里（过渡期）。
 */
internal object CustomTabLauncher {

    fun open(context: Context, url: String): Boolean {
        val uri = runCatching { url.toUri() }.getOrNull() ?: return false
        return runCatching {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(context, uri)
            true
        }.getOrElse {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                true
            }.getOrDefault(false)
        }
    }
}
