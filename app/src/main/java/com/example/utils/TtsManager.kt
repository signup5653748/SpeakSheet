package com.example.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<Voice>>(emptyList())
    val availableVoices: StateFlow<List<Voice>> = _availableVoices.asStateFlow()

    init {
        tts = TextToSpeech(context, this, "com.google.android.tts")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            _isInitialized.value = true
            loadAvailableVoices()
        } else {
            // Fallback to default engine if Google TTS is not available
            if (tts?.defaultEngine != "com.google.android.tts") {
                 tts = TextToSpeech(context, this)
            } else {
                 Log.e("TtsManager", "Initialization Failed!")
            }
        }
    }

    private fun loadAvailableVoices() {
        val voices = tts?.voices?.toList()?.filter { !it.isNetworkConnectionRequired }
        if (voices != null) {
            _availableVoices.value = voices.sortedBy { it.locale.displayName }
        }
    }

    fun speak(text: String) {
        if (_isInitialized.value) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }
    
    fun setVoice(voiceName: String) {
        val voice = _availableVoices.value.find { it.name == voiceName }
        if (voice != null) {
            tts?.voice = voice
        }
    }

    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate)
    }

    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
