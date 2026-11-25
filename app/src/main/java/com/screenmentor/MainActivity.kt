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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val Background = Color(0xFF090B14)
private val Panel = Color(0xFF151A2A)
private val Purple = Color(0xFF9B7BFF)
private val Cyan = Color(0xFF55D6E8)
private val Muted = Color(0xFF9AA3B8)

class MainActivity : ComponentActivity() {

    private var mode by mutableStateOf("main")

    private val projection =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { r ->
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

    private val perms =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->

            if (result[Manifest.permission.RECORD_AUDIO] == true) {
                val manager =
                    getSystemService(MediaProjectionManager::class.java)

                projection.launch(manager.createScreenCaptureIntent())
            } else {
                Toast.makeText(
                    this,
                    "Microphone permission is required.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private fun start() {

        // CHESS is not connected to Stockfish yet.
        if (mode == "chess") {
            Toast.makeText(
                this,
                R.string.chess_coming,
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            Toast.makeText(
                this,
                R.string.no_key,
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        perms.launch(permissions.toTypedArray())
    }

    private fun stop() {
        startService(
            Intent(this, AssistantService::class.java)
                .setAction(ACTION_STOP)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val status by SessionState.status.collectAsState()
            val transcript by SessionState.transcript.collectAsState()

            val active =
                status == Status.Live ||
                status == Status.Connecting

            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Background,
                    surface = Panel,
                    primary = Purple,
                    secondary = Cyan
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Background
                ) {

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .systemBarsPadding()
                            .padding(horizontal = 22.dp)
                            .padding(top = 30.dp, bottom = 24.dp),

                        verticalArrangement = Arrangement.spacedBy(22.dp)
                    ) {

                        // HEADER

                        Text(
                            "SCREEN MENTOR",
                            color = Color.White,
                            fontSize = 27.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp
                        )

                        Text(
                            "Your screen. Your intelligence.",
                            color = Muted,
                            fontSize = 14.sp
                        )

                        // STATUS

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Panel
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = when (status) {
                                        Status.Live -> Color(0xFF164C3A)
                                        Status.Connecting -> Color(0xFF55451B)
                                        Status.Error -> Color(0xFF592B38)
                                        else -> Color(0xFF293044)
                                    }
                                ) {
                                    Spacer(
                                        Modifier
                                            .size(12.dp)
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                Column {
                                    Text(
                                        "SESSION STATUS",
                                        color = Muted,
                                        fontSize = 10.sp,
                                        letterSpacing = 1.5.sp
                                    )

                                    Text(
                                        text = stringResource(
                                            when (status) {
                                                Status.Idle -> R.string.st_idle
                                                Status.Connecting -> R.string.st_connecting
                                                Status.Live -> R.string.st_live
                                                Status.Error -> R.string.st_error
                                            }
                                        ),
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // MODE TITLE

                        Text(
                            "CHOOSE YOUR MODE",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )

                        // MAIN CARD

                        ModeCard(
                            title = "MAIN",
                            subtitle = "GEMINI ASSISTANT",
                            description = stringResource(R.string.main_desc),
                            symbol = "✦",
                            accent = Purple,
                            selected = mode == "main",
                            enabled = !active,
                            onClick = { mode = "main" }
                        )

                        // CHESS CARD

                        ModeCard(
                            title = "CHESS",
                            subtitle = "STOCKFISH ENGINE",
                            description = stringResource(R.string.chess_desc),
                            symbol = "♟",
                            accent = Cyan,
                            selected = mode == "chess",
                            enabled = !active,
                            onClick = { mode = "chess" }
                        )

                        // START BUTTON

                        Button(
                            onClick = {
                                if (active) stop() else start()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(62.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (active)
                                    Color(0xFFB83F58)
                                else
                                    Purple
                            )
                        ) {
                            Text(
                                stringResource(
                                    if (active) R.string.stop
                                    else R.string.start
                                ),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        // TRANSCRIPT

                        if (transcript.isNotBlank()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Panel
                                )
                            ) {
                                Column(
                                    Modifier.padding(18.dp)
                                ) {
                                    Text(
                                        "LIVE TRANSCRIPT",
                                        color = Cyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(Modifier.height(10.dp))

                                    Text(
                                        transcript,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            stringResource(R.string.privacy),
                            color = Muted,
                            fontSize = 11.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    description: String,
    symbol: String,
    accent: Color,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                onClick = onClick
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                Color(0xFF20243A)
            else
                Panel
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected)
                accent
            else
                Color(0xFF293044)
        )
    ) {

        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Surface(
                modifier = Modifier.size(58.dp),
                shape = RoundedCornerShape(17.dp),
                color = accent.copy(alpha = 0.15f)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        symbol,
                        color = accent,
                        fontSize = 30.sp
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    title,
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Text(
                    subtitle,
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    description,
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }

            if (selected) {
                Text(
                    "✓",
                    color = accent,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
