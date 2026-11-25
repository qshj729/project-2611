package app.feature.browser

import android.content.Context
import android.util.Log
import org.mozilla.geckoview.GeckoPreferenceController
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * GeckoView 运行时。
 *
 * 设计参考：design.md §2（ADR-201 / ADR-202）、§4.8（C-07 浏览器底座）
 */
object GeckoRuntimeProvider {

    /** PAC 地址：本地代理同时提供 PAC 脚本与转发能力。 */
    const val PAC_URL = "http://127.0.0.1:1080/proxy.pac"

    /** Gecko 偏好：代理类型（2 = PAC 自动配置）。 */
    private const val PREF_PROXY_TYPE = "network.proxy.type"
    private const val PROXY_TYPE_PAC = 2

    /** Gecko 偏好：PAC 脚本 URL。 */
    private const val PREF_PAC_URL = "network.proxy.pac_url"

    /** 偏好分支：用户级（持久化到 prefs.js）。 */
    private val PREF_BRANCH_USER = GeckoPreferenceController.PREF_BRANCH_USER

    private const val TAG = "GeckoRuntimeProvider"

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
        // 启用 about:config 以便通过 GeckoPreferenceController 设置偏好
        val settings = org.mozilla.geckoview.GeckoRuntimeSettings.Builder()
            .aboutConfigEnabled(true)
            .build()
        val rt = org.mozilla.geckoview.GeckoRuntime.create(context, settings)
        applyProxyPreferences(rt)
        return rt
    }

    /**
     * 应用代理偏好：PAC 自动配置。
     * 偏好必须在 runtime 创建后设置；使用 GeckoPreferenceController。
     * 使用 accept(successConsumer, errorConsumer) 处理 GeckoResult。
     */
    private fun applyProxyPreferences(rt: GeckoRuntime) {
        // network.proxy.type = 2 (PAC)
        GeckoPreferenceController.setGeckoPref(PREF_PROXY_TYPE, PROXY_TYPE_PAC, PREF_BRANCH_USER)
            .accept(
                { /* success */ },
                { e -> Log.e(TAG, "Failed to set $PREF_PROXY_TYPE", e) }
            )

        // network.proxy.pac_url = http://127.0.0.1:1080/proxy.pac
        GeckoPreferenceController.setGeckoPref(PREF_PAC_URL, PAC_URL, PREF_BRANCH_USER)
            .accept(
                { /* success */ },
                { e -> Log.e(TAG, "Failed to set $PREF_PAC_URL", e) }
            )
    }

    /** 供单测使用的内部常量（不改变公开 API）。 */
    internal object TestConstants {
        const val PREF_PROXY_TYPE = "network.proxy.type"
        const val PROXY_TYPE_PAC = 2
        const val PREF_PAC_URL = "network.proxy.pac_url"
        const val PAC_URL = "http://127.0.0.1:1080/proxy.pac"
        // PREF_BRANCH_USER 非常量，通过函数暴露
        fun prefBranchUser() = GeckoRuntimeProvider.PREF_BRANCH_USER
    }
}

