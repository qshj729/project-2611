package com.hubdeck.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hubdeck.app.core.tunnel.TunnelProvider
import com.hubdeck.app.ui.HubDeckApp
import com.hubdeck.app.ui.theme.HubDeckTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HubDeckTheme {
                TunnelAwareApp()
            }
        }
    }
}

@Composable
fun TunnelAwareApp() {
    val context = LocalContext.current.applicationContext

    // Get Hilt entry point for dependencies
    val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
        context, com.hubdeck.app.di.HubDeckApplicationEntryPoint::class.java
    )

    // Use Hilt-provided TunnelManager (singleton)
    val tunnelManager = entryPoint.tunnelManager()

    // Start tunnel on first composition if auth key is saved
    val started = remember { mutableStateOf(false) }
    if (!started.value) {
        started.value = true
        // Initialize tunnel provider (loads auth key if any)
        val authKey = TunnelProvider.initTunnel(context)
        if (authKey != null) {
            tunnelManager.start(authKey)
        } else {
            tunnelManager.startIfConfigured()
        }
    }

    // Observe tunnel state for logging/debugging
    val tunnelState by tunnelManager.tunnelState.collectAsStateWithLifecycle()

    // Apply initial proxy config (disconnected = all DIRECT) - will be updated by tunnel manager
    LaunchedEffect(tunnelState) {
        // TunnelManager handles proxy config updates internally
    }

    HubDeckApp()
}