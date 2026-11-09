package com.link.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ApiMessage(val role: String, val content: String)

object LinkApi {
    suspend fun chat(baseUrl: String, accessToken: String, message: String, history: List<ApiMessage>): String =
        withContext(Dispatchers.IO) {
            require(baseUrl.startsWith("https://")) { "Informe uma URL HTTPS válida para o backend." }
            require(accessToken.isNotBlank()) { "Configure o token de acesso do LINK." }
            val payload = JSONObject().apply {
                put("message", message)
                put("history", JSONArray().apply {
                    history.takeLast(20).forEach { item ->
                        put(JSONObject().put("role", item.role).put("content", item.content))
                    }
                })
            }
            val connection = (URL(baseUrl.trimEnd('/') + "/v1/chat").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 60000
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
                    val detail = runCatching { JSONObject(body).optString("detail") }.getOrNull()
                    throw IllegalStateException(detail?.takeIf { it.isNotBlank() } ?: "Backend respondeu HTTP $code.")
                }
                JSONObject(body).optString("reply").takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("O backend retornou uma resposta vazia.")
            } finally {
                connection.disconnect()
            }
        }
}
