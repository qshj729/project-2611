package app.feature.browser

import android.content.Context
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * GeckoView 运行时。
 *
 * 设计参考：/home/jack/project-2611-docs/docs/design.md §3（ADR-101 / ADR-102）、§6.3
 */
object GeckoRuntimeProvider {

    /** PAC 地址：本地代理同时提供 PAC 脚本与转发能力。 */
    const val PAC_URL = "http://127.0.0.1:1080/proxy.pac"

    @Volatile
    private var runtime: org.mozilla.geckoview.GeckoRuntime? = null

    /** 获取（必要时创建）全局 runtime。必须在主线程调用。 */
    fun get(context: Context): org.mozilla.geckoview.GeckoRuntime {
        runtime?.let { return it }
        return synchronized(this) {
            runtime ?: create(context.applicationContext).also { runtime = it }
        }
    }

    private fun create(context: Context): org.mozilla.geckoview.GeckoRuntime {
        // GeckoView 153 不支持 PAC 设置，先不配置代理，由本地代理服务器处理路由
        val settings = org.mozilla.geckoview.GeckoRuntimeSettings.Builder().build()
        return org.mozilla.geckoview.GeckoRuntime.create(context, settings)
    }
}