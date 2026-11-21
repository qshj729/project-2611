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
        // Apply initial proxy configuration (disconnected = all DIRECT)
        val entryPoint = EntryPointAccessors.fromApplication(this, MyAppsApplicationEntryPoint::class.java)
        entryPoint.proxyConfigurator().apply(this, app.core.model.TailnetState())
    }
}
