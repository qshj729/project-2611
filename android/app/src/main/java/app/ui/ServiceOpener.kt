package app.ui

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/**
 * Opens a service in a Custom Tab.
 *
 * Design reference: docs/design.md ADR-007 - HubDeck never proxies traffic, it hands the
 * URL to the system browser so that password managers, WebAuthn and cookies keep working.
 */
object ServiceOpener {

    fun open(context: Context, url: String): Boolean {
        val uri: Uri = url.toUri()
        return runCatching {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(context, uri)
            true
        }.getOrElse {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                true
            }.getOrDefault(false)
        }
    }
}
