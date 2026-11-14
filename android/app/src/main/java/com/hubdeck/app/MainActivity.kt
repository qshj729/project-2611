package com.hubdeck.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hubdeck.app.ui.HubDeckApp
import com.hubdeck.app.ui.auth.AuthKeyInputScreen
import com.hubdeck.app.ui.theme.HubDeckTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Coroutine scope for tunnel lifecycle
    private val tunnelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPrefs = getSharedPreferences("hubdeck_prefs", android.content.Context.MODE_PRIVATE)
        val savedAuthKey = sharedPrefs.getString("tailscale_auth_key_saved", null)

        setContent {
            enableEdgeToEdge()
            HubDeckTheme {
                if (savedAuthKey == null || savedAuthKey.isBlank() || savedAuthKey == "placeholder-key-no-env") {
                    AuthKeyInputScreen(onCompleted = {
                        // Key saved by AuthKeyInputScreen; reload and re-compose
                        val editor = sharedPrefs.edit()
                        editor.putString("tailscale_auth_key_saved", "saved").apply()
                        // Re-create activity to pick up the new key
                        restartActivity()
                    })
                } else {
                    TunnelAwareApp(tunnelScope = tunnelScope)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tunnelScope.cancel()
    }

    private fun restartActivity() {
        val intent = android.content.Intent(this, MainActivity::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

@Composable
fun TunnelAwareApp(tunnelScope: CoroutineScope) {
    val context = LocalContext.current.applicationContext

    // Get Hilt entry point for dependencies
    val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
        context, com.hubdeck.app.di.HubDeckApplicationEntryPoint::class.java
    )

    // Initialize tunnel manager and start if configured
    val tunnelManager = remember {
        TunnelManager(
            context = context,
            tailnetRepository = entryPoint.tailnetRepository(),
            proxyConfigurator = entryPoint.proxyConfigurator(),
            localProxyServer = LocalProxyServer(),
            scope = tunnelScope,
        )
    }

    // Start tunnel on first composition
    val started = remember { mutableStateOf(false) }
    if (!started.value) {
        started.value = true
        // Initialize tunnel provider (loads auth key)
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