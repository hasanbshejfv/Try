package com.screenmentor

import kotlinx.coroutines.flow.MutableStateFlow

enum class Status { Idle, Connecting, Live, Error }

object SessionState {
    val status = MutableStateFlow(Status.Idle)
    val transcript = MutableStateFlow("")

    fun append(text: String) {
        transcript.value = (transcript.value + text).takeLast(700)
    }
}
