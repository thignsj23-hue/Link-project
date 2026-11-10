package com.link.assistant

import android.content.Context
import android.net.Uri
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/** Uses on-device ML Kit models; translation text is not sent to LINK's paid API. */
object LocalTranslation {
    suspend fun translate(text: String, source: String, target: String): String {
        require(text.isNotBlank()) { "Digite ou fale um texto primeiro." }
        require(text.length <= 5000) { "Limite local: até 5.000 caracteres por tradução." }
        if (source == target) return text
        val sourceCode = TranslateLanguage.fromLanguageTag(source)
            ?: throw IllegalArgumentException("O idioma de origem não é compatível com a tradução local.")
        val targetCode = TranslateLanguage.fromLanguageTag(target)
            ?: throw IllegalArgumentException("O idioma de destino não é compatível com a tradução local.")
        val translator = Translation.getClient(TranslatorOptions.Builder()
            .setSourceLanguage(sourceCode).setTargetLanguage(targetCode).build())
        return try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().requireWifi().build()).await()
            translator.translate(text).await()
        } catch (error: Exception) {
            throw IllegalStateException("Não foi possível baixar ou usar o modelo de idioma. Confira a conexão e tente novamente.", error)
        } finally { translator.close() }
    }

    suspend fun readImage(context: Context, uri: Uri): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val result = recognizer.process(InputImage.fromFilePath(context, uri)).await()
            result.text.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Não encontrei texto legível nessa imagem.")
        } catch (error: Exception) {
            if (error is IllegalArgumentException) throw error
            throw IllegalStateException("Não consegui reconhecer o texto da imagem. Tente uma foto mais nítida.", error)
        } finally { recognizer.close() }
    }
}

