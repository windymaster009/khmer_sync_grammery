package com.windymaster.khmersync

import android.content.Context
import org.json.JSONObject
import java.util.Locale

data class ConversionResult(
    val output: String,
    val confidence: Double,
    val matchedTokens: Int,
    val totalTokens: Int
)

class RomanKhmerConverter(context: Context) {

    private val words = loadMap(context, "roman_khmer_dictionary.json")
    private val phrases = loadMap(context, "roman_khmer_phrases.json")

    fun convert(input: String): ConversionResult? {
        val normalized = normalize(input)
        if (normalized.isBlank()) return null

        phrases[normalized]?.let { phrase ->
            val count = normalized.split(" ").size
            return ConversionResult(phrase, 1.0, count, count)
        }

        val tokens = normalized.split(" ")
        if (tokens.isEmpty()) return null

        data class Part(val text: String, val mapped: Boolean)

        val parts = tokens.map { token ->
            val mapped = words[token]
            if (mapped != null) Part(mapped, true) else Part(token, false)
        }

        val matched = parts.count { it.mapped }
        if (matched == 0) return null

        val ratio = matched.toDouble() / tokens.size.toDouble()
        if (ratio < 0.55) return null

        val confidence = when {
            matched == tokens.size && tokens.size >= 2 -> 0.98
            matched == tokens.size -> 0.90
            ratio >= 0.80 -> 0.88
            ratio >= 0.66 -> 0.78
            else -> 0.62
        }

        return ConversionResult(
            output = smartJoin(parts.map { it.text to it.mapped }),
            confidence = confidence,
            matchedTokens = matched,
            totalTokens = tokens.size
        )
    }

    fun convertWord(word: String): String? {
        val normalized = normalize(word)
        if (normalized.contains(" ")) return null
        return words[normalized]
    }

    fun isKnownRomanKhmer(word: String): Boolean = convertWord(word) != null

    private fun smartJoin(parts: List<Pair<String, Boolean>>): String {
        if (parts.isEmpty()) return ""

        val output = StringBuilder()
        parts.forEachIndexed { index, part ->
            if (index > 0) {
                val previousMapped = parts[index - 1].second
                val currentMapped = part.second
                if (!(previousMapped && currentMapped)) {
                    output.append(' ')
                }
            }
            output.append(part.first)
        }
        return output.toString()
    }

    private fun normalize(value: String): String {
        return value
            .trim()
            .lowercase(Locale.US)
            .replace(Regex("\\s+"), " ")
    }

    private fun loadMap(context: Context, fileName: String): Map<String, String> {
        val json = context.assets.open(fileName).bufferedReader().use { it.readText() }
        val objectMap = JSONObject(json)
        val result = mutableMapOf<String, String>()
        val keys = objectMap.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            result[normalize(key)] = objectMap.getString(key)
        }

        return result
    }
}
