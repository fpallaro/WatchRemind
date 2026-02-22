package com.npal.phoneremind

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun generateAnnouncement(
        apiKey: String,
        title: String,
        time: String,
        location: String
    ): String {
        val locationLine = if (location.isNotBlank()) "\nLuogo: $location" else ""
        val prompt = """Sei un assistente vocale su smartphone.
Genera un promemoria vocale in italiano, naturale e conciso (massimo 2 frasi brevi),
per questo appuntamento che inizia tra 5 minuti:
Titolo: $title
Ora: $time$locationLine
Inizia con "Attenzione" o simile. Rispondi solo con il testo vocale, nient'altro."""

        val body = JSONObject()
            .put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", prompt)
                ))
            )).toString()

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response")

        return JSONObject(responseBody)
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .trim()
    }
}
