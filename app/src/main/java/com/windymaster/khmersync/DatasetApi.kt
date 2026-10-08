package com.windymaster.khmersync

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class PendingContribution(
    val submissionId: String,
    val rawText: String,
    val createdAt: String
)

object ContributionQueue {
    private const val PREFS = "khmer_sync_dataset_queue"
    private const val KEY_QUEUE = "pending"

    @Synchronized
    fun enqueue(context: Context, item: PendingContribution) {
        val items = load(context).toMutableList()
        items.add(item)
        save(context, items)
    }

    @Synchronized
    fun load(context: Context): List<PendingContribution> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_QUEUE, "[]") ?: "[]"

        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.getJSONObject(index)
                    add(
                        PendingContribution(
                            submissionId = value.getString("submissionId"),
                            rawText = value.getString("rawText"),
                            createdAt = value.getString("createdAt")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun remove(context: Context, submissionId: String) {
        save(
            context,
            load(context).filterNot { it.submissionId == submissionId }
        )
    }

    fun size(context: Context): Int = load(context).size

    private fun save(context: Context, items: List<PendingContribution>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("submissionId", item.submissionId)
                    .put("rawText", item.rawText)
                    .put("createdAt", item.createdAt)
            )
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_QUEUE, array.toString())
            .apply()
    }
}

object DatasetApi {

    fun submit(
        baseUrl: String,
        contributorId: String,
        item: PendingContribution
    ): Boolean {
        val endpoint = baseUrl.trim().trimEnd('/') + "/api/contributions"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 8_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }

        return try {
            val payload = JSONObject()
                .put("submissionId", item.submissionId)
                .put("contributorId", contributorId)
                .put("rawText", item.rawText)
                .put("createdAt", item.createdAt)
                .put("consentVersion", 1)

            connection.outputStream.use { output ->
                output.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            connection.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }
}
