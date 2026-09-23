package com.example.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tts: TextToSpeech? = null
    
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<Voice>>(emptyList())
    val availableVoices: StateFlow<List<Voice>> = _availableVoices.asStateFlow()

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e("TtsManager", "Failed to construct TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                val result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.getDefault()
                }
                _isInitialized.value = true
                managerScope.launch {
                    loadAvailableVoices()
                }
            } catch (e: Exception) {
                Log.e("TtsManager", "Error setting language", e)
                _isInitialized.value = true
            }
        } else {
            Log.e("TtsManager", "TextToSpeech Initialization Failed with status $status")
        }
    }

    private fun loadAvailableVoices() {
        try {
            val voices = tts?.voices?.toList()?.filter { !it.isNetworkConnectionRequired }
            if (voices != null) {
                _availableVoices.value = voices.sortedBy { it.locale.displayName }
            }
        } catch (e: Exception) {
            Log.e("TtsManager", "Error loading voices", e)
        }
    }

    fun speak(text: String) {
        if (_isInitialized.value && text.isNotBlank()) {
            try {
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "speaksheet_utterance")
            } catch (e: Exception) {
                Log.e("TtsManager", "Error speaking text", e)
            }
        }
    }
    
    fun setVoice(voiceName: String) {
        try {
            val voice = _availableVoices.value.find { it.name == voiceName }
            if (voice != null) {
                tts?.voice = voice
            }
        } catch (e: Exception) {
            Log.e("TtsManager", "Error setting voice", e)
        }
    }

    fun setSpeechRate(rate: Float) {
        try {
            tts?.setSpeechRate(rate)
        } catch (e: Exception) {
            Log.e("TtsManager", "Error setting speech rate", e)
        }
    }

    fun setPitch(pitch: Float) {
        try {
            tts?.setPitch(pitch)
        } catch (e: Exception) {
            Log.e("TtsManager", "Error setting pitch", e)
        }
    }

    fun shutdown() {
        try {
            managerScope.cancel()
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("TtsManager", "Error shutting down TTS", e)
        } finally {
            tts = null
            _isInitialized.value = false
        }
    }
}
