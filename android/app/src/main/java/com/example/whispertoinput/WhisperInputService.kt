package com.example.whispertoinput

import android.inputmethodservice.InputMethodService
import android.view.View
import kotlinx.coroutines.*


class WhisperInputService : InputMethodService() {
    private var whisperKeyboard: WhisperKeyboard = WhisperKeyboard()
    private var whisperJobManager: WhisperJobManager = WhisperJobManager()

    private fun transcriptionCallback(text: String?) {
        if (text == null) {
            return
        }

        currentInputConnection?.commitText(text, text.length)
        whisperKeyboard.reset()
    }

    override fun onCreateInputView(): View {
        return whisperKeyboard.setup(
            layoutInflater,
            { },
            { },
            { whisperJobManager.startTranscriptionJobAsync { transcriptionCallback(it) } },
            { whisperJobManager.clearTranscriptionJob() })
    }

    override fun onWindowShown() {
        super.onWindowShown()
        whisperJobManager.clearTranscriptionJob()
        whisperKeyboard.reset()
    }

    override fun onWindowHidden() {

        super.onWindowHidden()
        whisperJobManager.clearTranscriptionJob()
        whisperKeyboard.reset()
    }
}
