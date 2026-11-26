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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val Background = Color(0xFF090B14)
private val Panel = Color(0xFF151A2A)
private val Purple = Color(0xFF9B8CFF)
private val Cyan = Color(0xFF55D6D0)
private val Muted = Color(0xFF929AAF)

class MainActivity : ComponentActivity() {

    private var mode by mutableStateOf("main")

    private var chessEngine: StockfishEngine? = null

    private var chessRunning by mutableStateOf(false)

    private var chessMessage by mutableStateOf("")

    private val projection =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            val data = result.data

            if (result.resultCode == RESULT_OK && data != null) {
                val intent = Intent(this, AssistantService::class.java)
                    .setAction(ACTION_START)
                    .putExtra("resultCode", result.resultCode)
                    .putExtra("data", data)
                    .putExtra("mode", mode)

                ContextCompat.startForegroundService(this, intent)
            }
        }

    private val permissions =
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

        if (mode == "chess") {

            if (chessRunning) return

            chessRunning = true
            chessMessage = "Starting Stockfish..."

            val engine = StockfishEngine(applicationContext)
            chessEngine = engine

            Toast.makeText(
                this,
                "Starting Stockfish...",
                Toast.LENGTH_SHORT
            ).show()

            engine.start { success, message ->

                runOnUiThread {

                    if (!success) {
                        chessRunning = false
                        chessMessage = message

                        chessEngine?.stop()
                        chessEngine = null

                        Toast.makeText(
                            this,
                            message,
                            Toast.LENGTH_LONG
                        ).show()

                        return@runOnUiThread
                    }

                    chessMessage = "Stockfish is ready"

                    Toast.makeText(
                        this,
                        "Stockfish is ready!",
                        Toast.LENGTH_SHORT
                    ).show()

                    val startingFen =
                        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

                    engine.analyze(
                        fen = startingFen,
                        moveTimeMs = 1500
                    ) { move ->

                        runOnUiThread {

                            if (move.isNotBlank()) {
                                chessMessage = "Best move: $move"

                                Toast.makeText(
                                    this,
                                    "Stockfish move: $move",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                chessMessage = "No move returned"
                            }
                        }
                    }
                }
            }

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

        val requestedPermissions =
            mutableListOf(Manifest.permission.RECORD_AUDIO)

        if (Build.VERSION.SDK_INT >= 33) {
            requestedPermissions.add(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        permissions.launch(requestedPermissions.toTypedArray())
    }

    private fun stop() {

        if (mode == "chess") {

            chessEngine?.stop()
            chessEngine = null

            chessRunning = false
            chessMessage = "Stockfish stopped"

            Toast.makeText(
                this,
                "Stockfish stopped",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        startService(
            Intent(this, AssistantService::class.java)
                .setAction(ACTION_STOP)
        )
    }

    override fun onDestroy() {
        chessEngine?.stop()
        chessEngine = null
        chessRunning = false

        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            val status by SessionState.status.collectAsState()
            val transcript by SessionState.transcript.collectAsState()

            val active =
                status == Status.Live ||
                status == Status.Connecting ||
                chessRunning

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Purple,
                    secondary = Cyan,
                    background = Background,
                    surface = Panel
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
                            .padding(22.dp),

                        verticalArrangement =
                            Arrangement.spacedBy(20.dp)
                    ) {

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = "SCREEN MENTOR",
                            color = Purple,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp
                        )

                        Text(
                            text = "Your smart\nscreen companion.",
                            color = Color.White,
                            fontSize = 32.sp,
                            lineHeight = 39.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Choose your experience",
                            color = Muted,
                            fontSize = 15.sp
                        )

                        ModeCard(
                            title = "MAIN",
                            subtitle = stringResource(R.string.main_desc),
                            label = "GEMINI AI",
                            selected = mode == "main",
                            accent = Purple,
                            enabled = !active,
                            onClick = {
                                mode = "main"
                            }
                        )

                        ModeCard(
                            title = "CHESS",
                            subtitle = stringResource(R.string.chess_desc),
                            label = "STOCKFISH ENGINE",
                            selected = mode == "chess",
                            accent = Cyan,
                            enabled = !active,
                            onClick = {
                                mode = "chess"
                            }
                        )

                        Spacer(Modifier.height(4.dp))

                        Button(
                            onClick = {
                                if (active) {
                                    stop()
                                } else {
                                    start()
                                }
                            },

                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp),

                            shape = RoundedCornerShape(18.dp),

                            colors = ButtonDefaults.buttonColors(
                                containerColor =
                                    if (active) Color(0xFFB94B61)
                                    else Purple,

                                contentColor = Color.White
                            )
                        ) {

                            Text(
                                text =
                                    if (active) "STOP SESSION"
                                    else if (mode == "chess") "OPEN CHESS"
                                    else "START SESSION",

                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),

                            shape = RoundedCornerShape(16.dp),

                            colors = CardDefaults.cardColors(
                                containerColor = Panel
                            )
                        ) {

                            Column(
                                modifier = Modifier.padding(18.dp),

                                verticalArrangement =
                                    Arrangement.spacedBy(8.dp)
                            ) {

                                Text(
                                    text = "SESSION STATUS",
                                    color = Muted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp
                                )

                                Text(
                                    text =
                                        if (mode == "chess" && chessRunning) {
                                            chessMessage
                                        } else {
                                            stringResource(
                                                when (status) {
                                                    Status.Idle ->
                                                        R.string.st_idle

                                                    Status.Connecting ->
                                                        R.string.st_connecting

                                                    Status.Live ->
                                                        R.string.st_live

                                                    Status.Error ->
                                                        R.string.st_error
                                                }
                                            )
                                        },

                                    color =
                                        if (mode == "chess" && chessRunning) {
                                            Cyan
                                        } else {
                                            when (status) {
                                                Status.Live -> Cyan
                                                Status.Error ->
                                                    Color(0xFFFF7185)

                                                else -> Color.White
                                            }
                                        },

                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (transcript.isNotBlank()) {

                            Card(
                                modifier = Modifier.fillMaxWidth(),

                                shape = RoundedCornerShape(16.dp),

                                colors = CardDefaults.cardColors(
                                    containerColor = Panel
                                )
                            ) {

                                Column(
                                    modifier = Modifier.padding(16.dp),

                                    verticalArrangement =
                                        Arrangement.spacedBy(8.dp)
                                ) {

                                    Text(
                                        text = "CONVERSATION",
                                        color = Cyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Text(
                                        text = transcript,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.privacy),
                            color = Muted,
                            fontSize = 11.sp,
                            lineHeight = 17.sp
                        )

                        Spacer(Modifier.height(12.dp))
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
    label: String,
    selected: Boolean,
    accent: Color,
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
            containerColor = Panel
        ),

        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,

            color = if (selected) accent
            else Color(0xFF292F42)
        )
    ) {

        Column(
            modifier = Modifier.padding(20.dp),

            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceBetween,

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    text = title,
                    color = accent,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )

                Text(
                    text = if (selected) "SELECTED" else label,
                    color = if (selected) accent else Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Text(
                text = subtitle,
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
        }
    }
}
