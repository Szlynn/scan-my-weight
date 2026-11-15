package io.github.szlynn.scanmyweight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class SheetsRepository(private val settingsRepository: SettingsRepository) {

    private val client = OkHttpClient()
    private val json = "application/json; charset=utf-8".toMediaType()

    /**
     * Uploads a single weight measurement to Google Sheets via the Apps Script webhook.
     *
     * Reads the webhook URL from [SettingsRepository] at call time.
     * [measurementId] identifies the locked measurement and is reused for retries.
     * Returns [Result.failure] with [IOException] if the URL is blank, on network failure,
     * or if the server returns an error status. Malformed URLs also return a failure.
     */
    suspend fun upload(
        measurement: WeightMeasurement,
        measurementId: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val webhookUrl = settingsRepository.webhookUrl
            .firstOrNull()
            ?.getOrNull()
            .orEmpty()

        if (webhookUrl.isBlank()) {
            return@withContext Result.failure(IOException("Webhook URL is not configured."))
        }

        runCatching {
            val body = JSONObject().apply {
                put("id", measurementId)
                put("timestamp", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now()))
                put("weight", measurement.weightKg)
                put("bf", measurement.bodyFatPercent)
                put("bw", measurement.bodyWaterPercent)
                put("bm", measurement.bodyMusclePercent)
            }.toString().toRequestBody(json)

            val request = Request.Builder()
                .url(webhookUrl)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: $responseBody")
            }
            val responseJson = JSONObject(responseBody)
            if (responseJson.optString("status") != "ok") {
                val message = responseJson.optString("message", "Unknown error")
                throw IOException("Webhook error: $message")
            }
        }
    }
}
