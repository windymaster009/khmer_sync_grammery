package com.windymaster.khmersync

import java.util.Locale

class EnglishCorrectionEngine {

    private val commonTypos = mapOf(
        "realy" to "really",
        "teh" to "the",
        "adn" to "and",
        "becuase" to "because",
        "definately" to "definitely",
        "recieve" to "receive",
        "wierd" to "weird",
        "seperate" to "separate",
        "occured" to "occurred",
        "untill" to "until",
        "thier" to "their",
        "freind" to "friend",
        "tomorow" to "tomorrow",
        "goverment" to "government",
        "langauge" to "language",
        "grammer" to "grammar",
        "adress" to "address",
        "alot" to "a lot",
        "woudl" to "would",
        "shoudl" to "should",
        "coudl" to "could"
    )

    fun suggest(word: String): String? {
        if (word.isBlank()) return null

        val lower = word.lowercase(Locale.US)
        val corrected = commonTypos[lower] ?: return null

        return when {
            word.all { it.isUpperCase() } -> corrected.uppercase(Locale.US)
            word.firstOrNull()?.isUpperCase() == true ->
                corrected.replaceFirstChar { it.uppercaseChar() }
            else -> corrected
        }
    }
}
