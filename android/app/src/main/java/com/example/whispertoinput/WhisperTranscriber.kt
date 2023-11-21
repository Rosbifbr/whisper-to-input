package com.example.whispertoinput

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class WhisperTranscriber {
    private var currentTranscriptionJob: Job? = null

    fun startAsync(
        context: Context,
        filename: String,
        mediaType: String,
        callback: (String?) -> Unit,
        exceptionCallback: (String) -> Unit
    ) {
        suspend fun makeWhisperRequest(): String {
            // Retrieve configs
            val endpoint = context.dataStore.data.map { preferences ->
                preferences[ENDPOINT]
            }.first() ?: ""
            val languageCode = context.dataStore.data.map { preferences ->
                preferences[LANGUAGE_CODE]
            }.first() ?: "en"
            val apiKey = context.dataStore.data.map { preferences ->
                preferences[API_KEY]
            }.first() ?: ""

            // Make request
            val client = OkHttpClient()
            val request = buildWhisperRequest(
                filename,
                "$endpoint?encode=true&task=transcribe&language=$languageCode&word_timestamps=false&output=txt",
                mediaType,
                apiKey
            )
            val response = client.newCall(request).execute()
            return response.body!!.string()
        }

        // Create a cancellable job in the main thread (for UI updating)
        val job = CoroutineScope(Dispatchers.Main).launch {

            // Within the job, make a suspend call at the I/O thread
            // It suspends before result is obtained.
            // Returns (transcribed string, exception message)
            val (transcribedText, exceptionMessage) = withContext(Dispatchers.IO) {
                try {
                    // Perform transcription here
                    return@withContext Pair(makeWhisperRequest(), null)
                } catch (e: CancellationException) {
                    // Task was canceled
                    return@withContext Pair(null, null)
                } catch (e: Exception) {
                    return@withContext Pair(null, e.message)
                }
            }

            // This callback is within the main thread.
            callback.invoke(transcribedText)

            // If exception message is not null
            if (!exceptionMessage.isNullOrEmpty()) {
                exceptionCallback(exceptionMessage)
            }
        }

        registerTranscriptionJob(job)
    }

    fun stop() {
        registerTranscriptionJob(null)
    }

    private fun registerTranscriptionJob(job: Job?) {
        currentTranscriptionJob?.cancel()
        currentTranscriptionJob = job
    }

    private fun buildWhisperRequest(filename: String, url: String, mediaType: String, apiKey: String): Request {
        val file: File = File(filename)
        val fileBody: RequestBody = file.asRequestBody(mediaType.toMediaTypeOrNull())
        val requestBody: RequestBody = MultipartBody.Builder().apply {
            setType(MultipartBody.FORM)
            addFormDataPart("audio_file", "@audio.m4a", fileBody)
            addFormDataPart("file", "@audio.m4a", fileBody)
            addFormDataPart("model", "whisper-1")
            addFormDataPart("response_format", "text")
        }.build()

        val requestHeaders: Headers = Headers.Builder()
            .add("Authorization", "Bearer $apiKey")
            .add("Content-Type", "multipart/form-data")
            .build()

        return Request.Builder()
            .headers(requestHeaders)
            .url(url)
            .post(requestBody)
            .build()
    }
}
