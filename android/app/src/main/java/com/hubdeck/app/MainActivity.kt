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
import androidx.compose.ui.unit.dp
import com.hubdeck.app.ui.auth.AuthKeyInputScreen
import com.hubdeck.app.ui.HubDeckApp
import com.hubdeck.app.ui.theme.HubDeckTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPrefs = getSharedPreferences("hubdeck_prefs", android.content.Context.MODE_PRIVATE)
        val savedAuthKey = sharedPrefs.getString("tailscale_auth_key_saved", null)

        setContent {
            enableEdgeToEdge()
            HubDeckTheme {
                if (savedAuthKey == null) {
                    AuthKeyInputScreen(onCompleted = {
                        // Key saved by AuthKeyInputScreen; reload and re-compose
                        val editor = sharedPrefs.edit()
                        editor.putString("tailscale_auth_key_saved", "saved").apply()
                        // Re-create activity to pick up the new key
                        restartActivity()
                    })
                } else {
                    HubDeckApp()
                }
            }
        }
    }

    private fun restartActivity() {
        val intent = android.content.Intent(this, MainActivity::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}