package com.link.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import java.net.URLEncoder

/** Explicit, user-spoken Android actions using public intents and a small app allowlist. */
object AndroidActionRouter {
    fun run(context: Context, command: String): String? {
        val normalized = command.trim().lowercase()
        val opening = listOf("abrir", "abra", "iniciar", "abre").any { normalized.startsWith(it) }
        val searching = listOf("pesquisar", "pesquise", "buscar", "busque", "procure", "tocar", "toque").any { normalized.startsWith(it) }

        if ((opening || searching) && normalized.contains("youtube")) {
            val query = command.replace(Regex("(?i)\\b(abrir|abra|iniciar|abre|pesquisar|pesquise|buscar|busque|procure|tocar|toque|música|musica|no|na|youtube)\\b"), " ").trim()
            if (searching && query.isNotBlank()) {
                val uri = Uri.parse("https://www.youtube.com/results?search_query=${URLEncoder.encode(query, "UTF-8")}")
                return if (launch(context, Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.youtube"), Intent(Intent.ACTION_VIEW, uri)))
                    "Abri a busca do YouTube para você escolher a música. A seleção e a reprodução ficam no YouTube."
                else "Não há aplicativo disponível para abrir a busca do YouTube."
            }
            return if (launchKnown(context, "com.google.android.youtube")) "Abrindo YouTube." else "O YouTube não está instalado neste aparelho."
        }

        if ((opening || searching || normalized.contains("navegar") || normalized.contains("rota")) &&
            listOf("maps", "mapa", "endereço", "endereco", "navegar", "rota").any { normalized.contains(it) }) {
            val destination = command.replace(Regex("(?i)\\b(abrir|abra|iniciar|abre|pesquisar|pesquise|buscar|busque|procure|mapas?|maps|navegar|para|até|ate|rota|endereço|endereco|do|da|no|na)\\b"), " ").trim()
            val geo = Uri.parse(if (destination.isBlank()) "geo:0,0?q=" else "geo:0,0?q=${Uri.encode(destination)}")
            if (!launch(context, Intent(Intent.ACTION_VIEW, geo).setPackage("com.google.android.apps.maps"), Intent(Intent.ACTION_VIEW, geo))) return "Não há aplicativo de mapas disponível."
            return if (destination.isBlank()) "Abri o mapa. Diga um local para pesquisar." else "Abri a busca de $destination no app de mapas."
        }

        if (opening) {
            val app = when {
                normalized.contains("whatsapp") -> "com.whatsapp"
                normalized.contains("chrome") || normalized.contains("navegador") -> "com.android.chrome"
                normalized.contains("câmera") || normalized.contains("camera") -> return openCamera(context)
                normalized.contains("configurações") || normalized.contains("configuracoes") -> {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return "Abrindo as configurações do Android."
                }
                normalized.contains("youtube") -> "com.google.android.youtube"
                normalized.contains("maps") -> "com.google.android.apps.maps"
                else -> null
            }
            if (app != null) return if (launchKnown(context, app)) "Aplicativo aberto. Você pode chamar o LINK pelo avatar flutuante." else "Esse aplicativo não está instalado ou não pode ser aberto."
        }
        return null
    }

    private fun openCamera(context: Context): String {
        return try {
            context.startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Abrindo a câmera."
        } catch (_: Exception) { "Não há aplicativo de câmera disponível." }
    }

    private fun launchKnown(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
    }

    private fun launch(context: Context, preferred: Intent, fallback: Intent): Boolean =
        runCatching { context.startActivity(preferred.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }
            .getOrElse { runCatching { context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false) }
}
