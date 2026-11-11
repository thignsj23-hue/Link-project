package com.link.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.activity.ComponentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.KeyFactory
import java.security.spec.RSAPublicKeySpec
import java.math.BigInteger
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.HttpsURLConnection

data class ChatGptModel(val slug: String, val displayName: String)

/** Open-source Sign in with ChatGPT plan-usage client. It never reads ChatGPT chats or API keys. */
object ChatGptPlanClient {
    private const val ISSUER = "https://auth.openai.com"
    private const val RESOURCE = "https://api.openai.com/v1"
    private const val AUTH_URL = "$ISSUER/api/accounts/authorize"
    private const val TOKEN_URL = "$ISSUER/api/accounts/oauth/token"
    private const val JWKS_URL = "$ISSUER/.well-known/jwks.json"
    private const val CREDENTIALS_KEY = "chatgpt_plan_credentials"
    private const val REQUIRED_SCOPES = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"

    private data class Credentials(
        val clientId: String,
        val accessToken: String,
        val refreshToken: String,
        val idToken: String,
        val hostId: String,
        val expiresAt: Long,
        val email: String
    ) {
        fun json() = JSONObject().put("client_id", clientId).put("access_token", accessToken)
            .put("refresh_token", refreshToken).put("id_token", idToken).put("host_id", hostId)
            .put("expires_at", expiresAt).put("email", email).toString()
        companion object {
            fun parse(raw: String) = JSONObject(raw).let {
                Credentials(it.getString("client_id"), it.getString("access_token"), it.getString("refresh_token"),
                    it.getString("id_token"), it.getString("host_id"), it.getLong("expires_at"), it.optString("email"))
            }
        }
    }

    fun signedInEmail(context: Context): String? = credentials(context)?.email?.takeIf { it.isNotBlank() }

    suspend fun signIn(activity: ComponentActivity): String = withContext(Dispatchers.IO) {
        val prefs = activity.getSharedPreferences("link_settings", Context.MODE_PRIVATE)
        val hostId = prefs.getString("chatgpt_host_id", null) ?: "urn:uuid:${UUID.randomUUID()}".also {
            prefs.edit().putString("chatgpt_host_id", it).apply()
        }
        val state = randomToken()
        val nonce = randomToken()
        val verifier = randomToken(48)
        val challenge = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { listener ->
            listener.soTimeout = 5 * 60 * 1000
            val redirectUri = "http://127.0.0.1:${listener.localPort}/auth/callback"
            val prior = credentials(activity)
            val requestedClient = prior?.clientId ?: "dynamic_agent_client"
            val builder = Uri.parse(AUTH_URL).buildUpon()
                .appendQueryParameter("client_id", requestedClient)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", redirectUri)
                .appendQueryParameter("scope", REQUIRED_SCOPES)
                .appendQueryParameter("resource", RESOURCE)
                .appendQueryParameter("state", state)
                .appendQueryParameter("nonce", nonce)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("ext_agent_host_id", hostId)
            if (prior == null) builder.appendQueryParameter("agent_name_hint", "LINK")
            else builder.appendQueryParameter("id_token_hint", prior.idToken)
            withContext(Dispatchers.Main) {
                activity.startActivity(Intent(Intent.ACTION_VIEW, builder.build()))
            }
            val callbackSocket = listener.accept()
            val reader = callbackSocket.getInputStream().bufferedReader(Charsets.US_ASCII)
            val firstLine = reader.readLine().orEmpty()
            while (reader.readLine().orEmpty().isNotEmpty()) { /* consume headers */ }
            val requestTarget = firstLine.split(' ').getOrNull(1) ?: error("Retorno de autorização inválido.")
            val callback = Uri.parse("http://127.0.0.1:${listener.localPort}$requestTarget")
            val html = "<html><meta name='viewport' content='width=device-width'><body style='font-family:sans-serif;text-align:center;padding:3em'><h2>LINK conectado</h2><p>Volte ao aplicativo para continuar.</p></body></html>"
            val body = html.toByteArray(Charsets.UTF_8)
            callbackSocket.getOutputStream().apply {
                write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\nContent-Length: ${body.size}\r\n\r\n".toByteArray(Charsets.US_ASCII))
                write(body); flush()
            }
            callbackSocket.close()
            require(callback.getQueryParameter("state") == state) { "A validação de segurança do login falhou. Tente novamente." }
            callback.getQueryParameter("error")?.let { error("A conexão foi cancelada ou recusada ($it).") }
            val code = callback.getQueryParameter("code") ?: error("A OpenAI não retornou o código de autorização.")
            val issuedClient = callback.getQueryParameter("client_id") ?: prior?.clientId
                ?: error("O registro do LINK não foi concluído pela OpenAI.")
            require(prior == null || prior.clientId == issuedClient) { "A conta retornada não corresponde ao registro selecionado." }
            val tokenJson = postForm(TOKEN_URL, mapOf(
                "grant_type" to "authorization_code", "client_id" to issuedClient, "code" to code,
                "redirect_uri" to redirectUri, "code_verifier" to verifier, "resource" to RESOURCE
            ))
            val idToken = tokenJson.optString("id_token")
            val claims = verifyIdToken(idToken, issuedClient, nonce)
            val scopes = tokenJson.optString("scope").split(' ').toSet()
            require("chatgpt.tokens.use.direct" in scopes) { "A autorização permitiu login, mas não o uso do plano ChatGPT no LINK." }
            val expiresIn = tokenJson.optLong("expires_in", 3600)
            val credentials = Credentials(issuedClient, tokenJson.getString("access_token"), tokenJson.getString("refresh_token"),
                idToken, hostId, System.currentTimeMillis() + expiresIn * 1000, claims.optString("email"))
            TokenCipher.writeSecret(activity, CREDENTIALS_KEY, credentials.json())
            credentials.email.ifBlank { "conta ChatGPT" }
        }
    }

    suspend fun signOut(context: Context) = withContext(Dispatchers.IO) {
        val creds = credentials(context)
        if (creds != null) runCatching {
            val discovery = getJson("$ISSUER/.well-known/openid-configuration")
            val endpoint = discovery.optString("revocation_endpoint")
            if (endpoint.isNotBlank()) postForm(endpoint, mapOf("token" to creds.refreshToken, "token_type_hint" to "refresh_token", "client_id" to creds.clientId))
        }
        TokenCipher.writeSecret(context, CREDENTIALS_KEY, "")
        Unit
    }

    suspend fun availableModels(context: Context): List<ChatGptModel> = withContext(Dispatchers.IO) {
        val token = freshCredentials(context).accessToken
        val json = getJson("$RESOURCE/models", token)
        val models = json.optJSONArray("models") ?: json.optJSONArray("data") ?: JSONArray()
        (0 until models.length()).mapNotNull { i ->
            val item = models.optJSONObject(i) ?: return@mapNotNull null
            val slug = item.optString("slug", item.optString("id"))
            if (slug.isBlank() || (item.has("visibility") && item.optString("visibility") != "list")) null
            else ChatGptModel(slug, item.optString("display_name", item.optString("name", slug)))
        }
    }

    suspend fun chat(context: Context, model: String, message: String, history: List<ApiMessage>, webSearch: Boolean): String = withContext(Dispatchers.IO) {
        val creds = freshCredentials(context)
        val input = JSONArray()
        history.takeLast(20).forEach { input.put(JSONObject().put("role", it.role).put("content", it.content)) }
        input.put(JSONObject().put("role", "user").put("content", message))
        val body = JSONObject().put("model", model).put("instructions", "Você é LINK, um assistente pessoal em português brasileiro. Seja claro, prestativo, honesto e objetivo. Se a pessoa pedir informação atual, use a pesquisa na web quando ela estiver habilitada. Não afirme ter realizado ações em dispositivos sem integração e autorização.")
            .put("input", input).put("store", false).put("stream", true)
        if (webSearch) body.put("tools", JSONArray().put(JSONObject().put("type", "web_search"))).put("tool_choice", "required")
        val connection = (URL("$RESOURCE/responses").openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 20000; readTimeout = 120000; doOutput = true
            setRequestProperty("Authorization", "Bearer ${creds.accessToken}")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "text/event-stream")
        }
        return try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val errorJson = runCatching { JSONObject(errorBody) }.getOrNull()
                throw IllegalStateException(errorJson?.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() } ?: "OpenAI respondeu HTTP ${connection.responseCode}.")
            }
            val answer = StringBuilder()
            val citations = linkedMapOf<String, String>()
            var completed = false
            connection.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.filter { it.startsWith("data:") }.forEach { line ->
                    val data = line.removePrefix("data:").trim()
                    if (data.isBlank() || data == "[DONE]") return@forEach
                    val event = runCatching { JSONObject(data) }.getOrNull() ?: return@forEach
                    when (event.optString("type")) {
                        "response.output_text.delta" -> answer.append(event.optString("delta"))
                        "response.output_text.annotation.added" -> event.optJSONObject("annotation")?.let { annotation ->
                            val url = annotation.optString("url")
                            if (url.startsWith("https://") || url.startsWith("http://")) citations[url] = annotation.optString("title").ifBlank { url }
                        }
                        "response.completed" -> completed = true
                        "response.failed" -> {
                            val err = event.optJSONObject("response")?.optJSONObject("error")
                            throw IllegalStateException(err?.optString("message")?.takeIf { it.isNotBlank() } ?: "A resposta do ChatGPT falhou.")
                        }
                        "error" -> throw IllegalStateException(event.optJSONObject("error")?.optString("message") ?: "Erro ao consultar ChatGPT.")
                    }
                }
            }
            check(completed) { "A conexão terminou antes da resposta completa." }
            val text = answer.toString().trim().ifBlank { "O ChatGPT retornou uma resposta vazia." }
            if (citations.isEmpty()) text else "$text\n\nFontes consultadas:\n" + citations.entries.joinToString("\n") { "• ${it.value}: ${it.key}" }
        } finally { connection.disconnect() }
    }

    private suspend fun freshCredentials(context: Context): Credentials {
        val old = credentials(context) ?: error("Conecte sua conta ChatGPT nas configurações do LINK.")
        if (old.expiresAt > System.currentTimeMillis() + 60_000) return old
        val token = postForm(TOKEN_URL, mapOf("grant_type" to "refresh_token", "client_id" to old.clientId, "refresh_token" to old.refreshToken, "resource" to RESOURCE))
        val scopes = token.optString("scope").split(' ').toSet()
        require(scopes.isEmpty() || "chatgpt.tokens.use.direct" in scopes) { "A autorização para usar o plano ChatGPT expirou. Conecte novamente." }
        val renewed = old.copy(accessToken = token.getString("access_token"), refreshToken = token.optString("refresh_token", old.refreshToken),
            expiresAt = System.currentTimeMillis() + token.optLong("expires_in", 3600) * 1000)
        TokenCipher.writeSecret(context, CREDENTIALS_KEY, renewed.json())
        return renewed
    }

    private fun credentials(context: Context): Credentials? = TokenCipher.readSecret(context, CREDENTIALS_KEY)?.let { runCatching { Credentials.parse(it) }.getOrNull() }

    private fun verifyIdToken(jwt: String, clientId: String, nonce: String): JSONObject {
        val pieces = jwt.split('.')
        require(pieces.size == 3) { "Token de identidade inválido." }
        val header = JSONObject(String(Base64.decode(pieces[0], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8))
        require(header.optString("alg") == "RS256") { "Algoritmo de assinatura inesperado." }
        val keys = getJson(JWKS_URL).optJSONArray("keys") ?: error("Chaves públicas da OpenAI indisponíveis.")
        val jwk = (0 until keys.length()).mapNotNull { keys.optJSONObject(it) }.firstOrNull { it.optString("kid") == header.optString("kid") }
            ?: error("A chave de assinatura da OpenAI não foi encontrada.")
        require(jwk.optString("kty") == "RSA") { "Tipo de chave pública inesperado." }
        val modulus = BigInteger(1, Base64.decode(jwk.getString("n"), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
        val exponent = BigInteger(1, Base64.decode(jwk.getString("e"), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent)) as RSAPublicKey
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(publicKey); verifier.update("${pieces[0]}.${pieces[1]}".toByteArray(Charsets.US_ASCII))
        require(verifier.verify(Base64.decode(pieces[2], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))) { "A assinatura do token de identidade não é válida." }
        val claims = JSONObject(String(Base64.decode(pieces[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8))
        require(claims.optString("iss") == ISSUER) { "Emissor do token de identidade inesperado." }
        val audience = claims.opt("aud")
        val audienceMatches = audience == clientId || (audience is JSONArray && (0 until audience.length()).any { audience.optString(it) == clientId })
        require(audienceMatches) { "O token de identidade não é deste cliente." }
        require(claims.optString("nonce") == nonce) { "O token de identidade não corresponde a esta tentativa de login." }
        val now = System.currentTimeMillis() / 1000
        require(claims.optLong("exp", 0) > now) { "O token de identidade expirou." }
        require(claims.optLong("iat", 0) in 1..(now + 5)) { "Data de emissão do token de identidade inválida." }
        require(claims.optString("sub").isNotBlank()) { "O token de identidade não contém uma conta válida." }
        return claims
    }

    private fun postForm(url: String, values: Map<String, String>): JSONObject {
        val body = values.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        val connection = (URL(url).openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 20000; readTimeout = 30000; doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val content = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) throw IllegalStateException(runCatching { JSONObject(content).optJSONObject("error")?.optString("message") }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Autenticação OpenAI falhou (HTTP ${connection.responseCode}).")
            JSONObject(content)
        } finally { connection.disconnect() }
    }

    private fun getJson(url: String, bearer: String? = null): JSONObject {
        val connection = (URL(url).openConnection() as HttpsURLConnection).apply {
            connectTimeout = 15000; readTimeout = 30000
            if (bearer != null) setRequestProperty("Authorization", "Bearer $bearer")
        }
        return try {
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) throw IllegalStateException("Falha ao consultar serviço OpenAI (HTTP ${connection.responseCode}).")
            JSONObject(body)
        } finally { connection.disconnect() }
    }

    private fun randomToken(bytes: Int = 32): String = Base64.encodeToString(ByteArray(bytes).also { java.security.SecureRandom().nextBytes(it) }, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
}
