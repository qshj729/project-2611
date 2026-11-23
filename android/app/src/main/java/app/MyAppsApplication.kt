package app

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import app.di.MyAppsApplicationEntryPoint

@HiltAndroidApp
class MyAppsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 初始化本地代理（不连接时全直连），并让 PAC 一直可取
        val entryPoint = EntryPointAccessors.fromApplication(this, MyAppsApplicationEntryPoint::class.java)
        entryPoint.localProxyServer().start(this, null)
    }
}