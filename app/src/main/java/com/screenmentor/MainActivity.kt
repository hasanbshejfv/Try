package com.screenmentor

import android.Manifest
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var mode by mutableStateOf("assist")

    private val projection = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == RESULT_OK && data != null) {
            val i = Intent(this, AssistantService::class.java)
                .setAction(ACTION_START)
                .putExtra("resultCode", r.resultCode)
                .putExtra("data", data)
                .putExtra("mode", mode)
            ContextCompat.startForegroundService(this, i)
        }
    }

    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.RECORD_AUDIO] == true) {
            val mpm = getSystemService(MediaProjectionManager::class.java)
            projection.launch(mpm.createScreenCaptureIntent()) // نافذة موافقة النظام
        }
    }

    private fun start() {
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            Toast.makeText(this, R.string.no_key, Toast.LENGTH_LONG).show(); return
        }
        val p = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.POST_NOTIFICATIONS
        perms.launch(p.toTypedArray())
    }

    private fun stop() {
        startService(Intent(this, AssistantService::class.java).setAction(ACTION_STOP))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    val status by SessionState.status.collectAsState()
                    val transcript by SessionState.transcript.collectAsState()
                    val active = status == Status.Live || status == Status.Connecting
                    Column(
                        Modifier.fillMaxSize().padding(24.dp).systemBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(mode == "assist", { mode = "assist" }, { Text(stringResource(R.string.mode_assist)) }, enabled = !active)
                                FilterChip(mode == "learn", { mode = "learn" }, { Text(stringResource(R.string.mode_learn)) }, enabled = !active)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(mode == "chess_coach", { mode = "chess_coach" }, { Text(stringResource(R.string.mode_chess_coach)) }, enabled = !active)
                                FilterChip(mode == "chess_training", { mode = "chess_training" }, { Text(stringResource(R.string.mode_chess_training)) }, enabled = !active)
                            }
                        }
                        Button(
                            onClick = { if (active) stop() else start() },
                            modifier = Modifier.fillMaxWidth().height(64.dp)
                        ) { Text(stringResource(if (active) R.string.stop else R.string.start)) }
                        Text(stringResource(when (status) {
                            Status.Idle -> R.string.st_idle
                            Status.Connecting -> R.string.st_connecting
                            Status.Live -> R.string.st_live
                            Status.Error -> R.string.st_error
                        }))
                        if (transcript.isNotBlank()) Card(Modifier.fillMaxWidth()) {
                            Text(transcript, Modifier.padding(16.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        Text(stringResource(R.string.privacy), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
