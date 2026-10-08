package com.windymaster.khmersync

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Gboard-friendly mode.
 *
 * Khmer Sync does not become the active keyboard. Instead, this service watches
 * normal editable text fields and converts high-confidence Romanized Khmer only
 * after the user commits a boundary such as Space or punctuation.
 *
 * Password fields are ignored. The app currently has no INTERNET permission, so
 * typed text never leaves the device.
 */
class KhmerAccessibilityService : AccessibilityService() {

    private lateinit var converter: RomanKhmerConverter

    private var lastAppliedText: String? = null
    private var lastAppliedPackage: String? = null
    private var lastAppliedAtMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        converter = RomanKhmerConverter(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return

        val autoConvert = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
            .getBoolean(MainActivity.PREF_AUTO_CONVERT, true)
        if (!autoConvert) return

        val source = event.source ?: return
        if (!source.isEditable || source.isPassword || event.isPassword) return

        val eventPackage = event.packageName?.toString().orEmpty()
        if (eventPackage.isBlank()) return

        val text = source.text?.toString() ?: return
        if (text.isBlank()) return

        val cursor = source.textSelectionStart
        if (cursor <= 0 || cursor > text.length) return

        // ACTION_SET_TEXT triggers another text-changed event. Ignore our own echo.
        val now = SystemClock.uptimeMillis()
        if (
            now - lastAppliedAtMs < SELF_CHANGE_GUARD_MS &&
            eventPackage == lastAppliedPackage &&
            text == lastAppliedText
        ) {
            return
        }

        val beforeCursor = text.substring(0, cursor)
        val boundaryMatch = TRAILING_BOUNDARY.find(beforeCursor) ?: return
        val boundary = boundaryMatch.value
        if (boundary.isEmpty()) return

        val bodyBeforeBoundary = beforeCursor.dropLast(boundary.length)
        if (bodyBeforeBoundary.isBlank()) return

        val candidate = findBestCandidate(bodyBeforeBoundary) ?: return

        val newBeforeCursor =
            bodyBeforeBoundary.substring(0, candidate.startIndex) +
                candidate.result.output +
                boundary
        val newText = newBeforeCursor + text.substring(cursor)

        if (newText == text) return
        if (!source.supportsAction(AccessibilityNodeInfo.ACTION_SET_TEXT)) return

        val textArgs = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                newText
            )
        }

        if (!source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, textArgs)) return

        val newCursor = newBeforeCursor.length.coerceIn(0, newText.length)
        val selectionArgs = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newCursor)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newCursor)
        }
        source.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)

        lastAppliedText = newText
        lastAppliedPackage = eventPackage
        lastAppliedAtMs = now
    }

    override fun onInterrupt() = Unit

    private fun findBestCandidate(body: String): Candidate? {
        val latinTail = LATIN_TAIL.find(body) ?: return null
        val segment = latinTail.value
        val absoluteStart = latinTail.range.first

        val tokenMatches = TOKEN.findAll(segment).toList()
        if (tokenMatches.isEmpty()) return null

        // Longest safe suffix wins. This lets:
        // "bro nh jg tv psa" -> "bro ខ្ញុំចង់ទៅផ្សារ"
        // while avoiding aggressive conversion of ordinary English.
        for (tokenIndex in tokenMatches.indices) {
            val relativeStart = tokenMatches[tokenIndex].range.first
            val candidateText = segment.substring(relativeStart).trim()
            val result = converter.convert(candidateText) ?: continue

            val fullyMappedPhrase =
                result.totalTokens >= 2 &&
                    result.matchedTokens == result.totalTokens &&
                    result.confidence >= 0.90

            val strongMixedPhrase =
                result.totalTokens >= 2 &&
                    result.matchedTokens >= 2 &&
                    result.matchedTokens.toDouble() / result.totalTokens >= 0.80 &&
                    result.confidence >= 0.88

            val candidateStart = absoluteStart + relativeStart
            val singleInKhmerContext =
                result.totalTokens == 1 &&
                    result.matchedTokens == 1 &&
                    result.confidence >= 0.90 &&
                    hasKhmerBefore(body, candidateStart)

            if (fullyMappedPhrase || strongMixedPhrase || singleInKhmerContext) {
                return Candidate(
                    startIndex = candidateStart,
                    result = result
                )
            }
        }

        return null
    }

    private fun hasKhmerBefore(body: String, startIndex: Int): Boolean {
        if (startIndex <= 0) return false

        val prefix = body.substring(0, startIndex).trimEnd()
        val last = prefix.lastOrNull() ?: return false
        return last in '\u1780'..'\u17FF'
    }

    private fun AccessibilityNodeInfo.supportsAction(actionId: Int): Boolean {
        return actionList.any { it.id == actionId }
    }

    private data class Candidate(
        val startIndex: Int,
        val result: ConversionResult
    )

    companion object {
        private const val SELF_CHANGE_GUARD_MS = 900L

        private val TRAILING_BOUNDARY = Regex("""[\s.,!?;:]+$""")
        private val LATIN_TAIL = Regex("""[A-Za-z']+(?:\s+[A-Za-z']+)*$""")
        private val TOKEN = Regex("""[A-Za-z']+""")
    }
}
