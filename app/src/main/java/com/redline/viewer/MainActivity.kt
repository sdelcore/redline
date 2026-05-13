package com.redline.viewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val pendingCallback = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        intent?.let { capture(it) }
        setContent {
            val callback by pendingCallback.collectAsState()
            RedlineApp(
                oauthCallback = callback,
                onOauthCallbackConsumed = { pendingCallback.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        capture(intent)
    }

    private fun capture(intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme == "redline" && data.host == "oauth") {
            pendingCallback.value = data
        }
    }
}
