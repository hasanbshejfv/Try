package com.screenmentor

import android.content.Context
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class StockfishEngine(context: Context) {

    private val engineFile =
        java.io.File(context.applicationInfo.nativeLibraryDir, "libstockfish.so")

    private var process: Process? = null
    private var writer: BufferedWriter? = null

    private val output = LinkedBlockingQueue<String>()
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    var isReady = false
        private set

    fun start(onResult: (Boolean, String) -> Unit) {
        executor.execute {
            try {
                if (!engineFile.exists()) {
                    onResult(false, "Stockfish binary not found")
                    return@execute
                }

                process = ProcessBuilder(engineFile.absolutePath)
                    .redirectErrorStream(true)
                    .start()

                writer = BufferedWriter(
                    OutputStreamWriter(process!!.outputStream)
                )

                val reader = BufferedReader(
                    InputStreamReader(process!!.inputStream)
                )

                Thread {
                    try {
                        while (true) {
                            val line = reader.readLine() ?: break
                            output.offer(line)
                        }
                    } catch (_: Exception) {
                    }
                }.start()

                send("uci")

                if (!waitFor("uciok", 10000)) {
                    error("UCI initialization timeout")
                }

                send("setoption name Threads value 1")
                send("setoption name Hash value 64")
                send("isready")

                if (!waitFor("readyok", 10000)) {
                    error("Engine readiness timeout")
                }

                isReady = true
                onResult(true, "Stockfish is ready")

            } catch (e: Exception) {
                isReady = false
                onResult(false, e.message ?: "Engine startup failed")
            }
        }
    }

    fun analyze(
        fen: String,
        moveTimeMs: Int = 1500,
        onBestMove: (String) -> Unit
    ) {
        executor.execute {
            if (!isReady) {
                onBestMove("")
                return@execute
            }

            try {
                while (output.isNotEmpty()) output.poll()

                send("position fen $fen")
                send("go movetime ${moveTimeMs.coerceIn(100, 10000)}")

                val deadline = System.currentTimeMillis() + 15000

                while (System.currentTimeMillis() < deadline) {
                    val line = output.poll(500, TimeUnit.MILLISECONDS)
                        ?: continue

                    if (line.startsWith("bestmove ")) {
                        val move = line.split(" ").getOrNull(1) ?: ""
                        onBestMove(if (move == "(none)") "" else move)
                        return@execute
                    }
                }

                onBestMove("")

            } catch (_: Exception) {
                onBestMove("")
            }
        }
    }

    private fun send(command: String) {
        writer?.apply {
            write(command)
            newLine()
            flush()
        }
    }

    private fun waitFor(
        expected: String,
        timeoutMs: Long
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            val line = output.poll(500, TimeUnit.MILLISECONDS)
                ?: continue

            if (line == expected) return true
        }

        return false
    }

    fun stop() {
        isReady = false

        try {
            send("quit")
            writer?.close()
            process?.destroy()
        } catch (_: Exception) {
        }

        writer = null
        process = null
        output.clear()
    }
}
