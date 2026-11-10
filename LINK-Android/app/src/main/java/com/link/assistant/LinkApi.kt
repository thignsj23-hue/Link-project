package com.link.assistant

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ApiMessage(val role: String, val content: String)

object LinkApi {
    suspend fun chat(
        baseUrl: String,
        accessToken: String,
        message: String,
        history: List<ApiMessage>,
        personality: String
    ): String = withContext(Dispatchers.IO) {
        val historyJson = JSONArray()
        history.takeLast(20).forEach { item ->
            historyJson.put(JSONObject().put("role", item.role).put("content", item.content))
        }
        post(
            baseUrl,
            accessToken,
            "/v1/chat",
            JSONObject()
                .put("message", message)
                .put("history", historyJson)
                .put("personality", personality)
        )
    }

    suspend fun analyzeImage(
        baseUrl: String,
        accessToken: String,
        bytes: ByteArray,
        mimeType: String,
        prompt: String
    ): String = withContext(Dispatchers.IO) {
        require(bytes.size <= 10 * 1024 * 1024) { "A imagem deve ter até 10 MB." }
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        post(
            baseUrl,
            accessToken,
            "/v1/vision",
            JSONObject()
                .put("image_base64", encoded)
                .put("mime_type", mimeType)
                .put("prompt", prompt)
        )
    }

    suspend fun webSearch(baseUrl: String, accessToken: String, query: String): String =
        withContext(Dispatchers.IO) {
            post(baseUrl, accessToken, "/v1/web/search", JSONObject().put("query", query))
        }

    private fun post(baseUrl: String, accessToken: String, path: String, payload: JSONObject): String {
        require(baseUrl.startsWith("https://")) { "Use uma URL HTTPS para o backend." }
        require(accessToken.isNotBlank()) { "Informe o token de acesso do backend." }
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 75_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(body).optString("detail") }.getOrDefault("")
                throw IllegalStateException(detail.ifBlank { "Backend respondeu HTTP $code." })
            }
            return JSONObject(body).optString("reply").ifBlank { "O backend não retornou texto." }
        } finally {
            connection.disconnect()
        }
    }
}
