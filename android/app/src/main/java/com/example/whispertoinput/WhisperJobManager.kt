package com.example.whispertoinput

import com.aallam.openai.api.audio.TranscriptionRequest
import com.aallam.openai.api.file.FileSource
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.*
import okio.FileSystem
import okio.Path.Companion.toPath

class WhisperJobManager
{
    private var currentTranscriptionJob : Job? = null

    fun startTranscriptionJobAsync(filename: String, callback: (String?) -> Unit)
    {
        suspend fun whisperTranscription(): String {
            val openai = OpenAI(
                token = "<API-KEY>"
            )
            val request = TranscriptionRequest(
                audio = FileSource(name = filename, source = FileSystem.SYSTEM.source(filename.toPath())),
                model = ModelId("whisper-1"),
            )
            val transcription = openai.transcription(request)

            return transcription.text
        }

        // Create a cancellable job in the main thread (for UI updating)
        val job = CoroutineScope(Dispatchers.Main).launch {

            // Within the job, make a suspend call at the I/O thread
            // It suspends before result is obtained.
            val result = withContext(Dispatchers.IO) {
                try {
                    // Perform transcription here
                    return@withContext whisperTranscription()
                } catch (e: CancellationException) {
                    // Task was canceled
                    return@withContext null
                }
            }

            // This callback is within the main thread.
            if (!result.isNullOrEmpty())
            {
                callback.invoke(result)
            }
        }

        registerTranscriptionJob(job)
    }

    fun clearTranscriptionJob()
    {
        registerTranscriptionJob(null)
    }

    private fun registerTranscriptionJob(job : Job?)
    {
        if (currentTranscriptionJob != null)
        {
            currentTranscriptionJob!!.cancel()
        }

        currentTranscriptionJob = job
    }
}
