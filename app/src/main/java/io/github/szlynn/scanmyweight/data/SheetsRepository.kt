package io.github.szlynn.scanmyweight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class SheetsRepository(private val webhookUrl: String) {

    private val client = OkHttpClient()
    private val json = "application/json; charset=utf-8".toMediaType()

    /**
     * Uploads a single weight measurement to Google Sheets via the Apps Script webhook.
     * Throws [IOException] on network failure or if the server returns an error status.
     */
    suspend fun upload(measurement: WeightMeasurement): Result<Unit> = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
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

        runCatching {
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
