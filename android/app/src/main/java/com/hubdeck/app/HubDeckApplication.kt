package com.hubdeck.app

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import com.hubdeck.app.di.HubDeckApplicationEntryPoint

@HiltAndroidApp
class HubDeckApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply initial proxy configuration (disconnected = all DIRECT)
        val entryPoint = EntryPointAccessors.fromApplication(this, HubDeckApplicationEntryPoint::class.java)
        entryPoint.proxyConfigurator().apply(this, com.hubdeck.app.core.model.TailnetState())
    }
}
