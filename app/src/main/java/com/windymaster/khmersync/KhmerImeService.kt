package com.windymaster.khmersync

import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class KhmerImeService : InputMethodService() {

    private lateinit var converter: RomanKhmerConverter
    private val englishCorrection = EnglishCorrectionEngine()
    private val composing = StringBuilder()

    private var secureField = false
    private var shift = false
    private var suggestionButtons: List<Button> = emptyList()
    private val letterButtons = mutableListOf<Button>()

    override fun onCreate() {
        super.onCreate()
        converter = RomanKhmerConverter(this)
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
            setBackgroundColor(Color.rgb(238, 240, 244))
        }

        val suggestions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        suggestionButtons = List(3) {
            Button(this).apply {
                text = ""
                isAllCaps = false
                textSize = 16f
                visibility = View.INVISIBLE
                setPadding(dp(6), 0, dp(6), 0)
            }.also { button ->
                suggestions.addView(
                    button,
                    LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                        setMargins(dp(2), dp(2), dp(2), dp(4))
                    }
                )
            }
        }
        root.addView(suggestions)

        root.addView(letterRow("qwertyuiop"))
        root.addView(letterRow("asdfghjkl"))
        root.addView(letterRow("zxcvbnm", withShift = true))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        bottom.addView(actionKey(",", 1f) { handlePunctuation(",") })
        bottom.addView(actionKey("space", 4f) { finishCurrentWord(" ") })
        bottom.addView(actionKey(".", 1f) { handlePunctuation(".") })
        bottom.addView(actionKey("⌫", 1.2f) { handleBackspace() })
        bottom.addView(actionKey("↵", 1.2f) { handleEnter() })

        root.addView(bottom)
        refreshSuggestions()

        return root
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        composing.clear()
        secureField = isSecureInput(attribute?.inputType ?: 0)
        shift = false
        updateShiftLabels()
        refreshSuggestions()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        composing.clear()
        clearSuggestions()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    private fun letterRow(chars: String, withShift: Boolean = false): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        if (withShift) {
            row.addView(actionKey("⇧", 1.25f) { toggleShift() })
        }

        chars.forEach { char ->
            val button = actionKey(char.toString(), 1f) {
                handleLetter(if (shift) char.uppercaseChar() else char)
            }
            button.tag = char
            letterButtons.add(button)
            row.addView(button)
        }

        return row
    }

    private fun actionKey(label: String, weight: Float, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = if (label.length <= 2) 18f else 14f
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(3), 0, dp(3), 0)
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(0, dp(50), weight).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
        }
    }

    private fun handleLetter(char: Char) {
        val input = currentInputConnection ?: return

        composing.append(char)
        input.setComposingText(composing.toString(), 1)

        if (shift) {
            shift = false
            updateShiftLabels()
        }

        refreshSuggestions()
    }

    private fun finishCurrentWord(boundary: String) {
        val input = currentInputConnection ?: return

        if (secureField) {
            input.finishComposingText()
            composing.clear()
            input.commitText(boundary, 1)
            clearSuggestions()
            return
        }

        val tail = romanTail()
        val khmer = converter.convert(tail)
        val autoConvert = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
            .getBoolean(MainActivity.PREF_AUTO_CONVERT, true)

        val shouldConvertPhrase = khmer != null &&
            khmer.matchedTokens >= 2 &&
            khmer.confidence >= 0.78

        val shouldConvertSingleInKhmerContext = khmer != null &&
            khmer.matchedTokens == 1 &&
            khmer.confidence >= 0.90 &&
            hasKhmerBeforeTail(tail)

        if (autoConvert && (shouldConvertPhrase || shouldConvertSingleInKhmerContext)) {
            replaceTail(tail, khmer!!.output)
        } else {
            val english = englishCorrection.suggest(composing.toString())
            if (english != null) {
                input.setComposingText(english, 1)
            }
            input.finishComposingText()
            composing.clear()
        }

        input.commitText(boundary, 1)
        refreshSuggestions()
    }

    private fun handlePunctuation(punctuation: String) {
        finishCurrentWord("")
        currentInputConnection?.commitText(punctuation, 1)
    }

    private fun handleEnter() {
        finishCurrentWord("")

        val input = currentInputConnection ?: return
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_NONE

        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            input.performEditorAction(action)
        } else {
            input.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            input.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    private fun handleBackspace() {
        val input = currentInputConnection ?: return

        if (composing.isNotEmpty()) {
            composing.deleteCharAt(composing.lastIndex)
            if (composing.isEmpty()) {
                input.finishComposingText()
            } else {
                input.setComposingText(composing.toString(), 1)
            }
        } else {
            input.deleteSurroundingText(1, 0)
        }

        refreshSuggestions()
    }

    private fun toggleShift() {
        shift = !shift
        updateShiftLabels()
    }

    private fun updateShiftLabels() {
        letterButtons.forEach { button ->
            val base = button.tag as? Char ?: return@forEach
            button.text = if (shift) base.uppercaseChar().toString() else base.toString()
        }
    }

    private fun refreshSuggestions() {
        if (suggestionButtons.isEmpty()) return
        if (secureField) {
            clearSuggestions()
            return
        }

        data class UiSuggestion(val label: String, val action: () -> Unit)

        val items = mutableListOf<UiSuggestion>()
        val tail = romanTail()
        val currentWord = composing.toString()

        converter.convert(tail)?.let { result ->
            if (tail.contains(" ") && result.output != tail) {
                items.add(UiSuggestion(result.output) {
                    replaceTail(tail, result.output)
                    refreshSuggestions()
                })
            }
        }

        converter.convertWord(currentWord)?.let { khmerWord ->
            if (items.none { it.label == khmerWord }) {
                items.add(UiSuggestion(khmerWord) {
                    replaceCurrentWord(khmerWord)
                    refreshSuggestions()
                })
            }
        }

        englishCorrection.suggest(currentWord)?.let { corrected ->
            if (items.none { it.label == corrected }) {
                items.add(UiSuggestion(corrected) {
                    replaceCurrentWord(corrected)
                    refreshSuggestions()
                })
            }
        }

        suggestionButtons.forEachIndexed { index, button ->
            val item = items.getOrNull(index)
            if (item == null) {
                button.text = ""
                button.visibility = View.INVISIBLE
                button.setOnClickListener(null)
            } else {
                button.text = item.label
                button.visibility = View.VISIBLE
                button.setOnClickListener { item.action() }
            }
        }
    }

    private fun replaceCurrentWord(replacement: String) {
        val input = currentInputConnection ?: return
        input.setComposingText(replacement, 1)
        input.finishComposingText()
        composing.clear()
    }

    private fun replaceTail(tail: String, replacement: String) {
        if (tail.isBlank()) return

        val input = currentInputConnection ?: return
        input.finishComposingText()
        composing.clear()
        input.deleteSurroundingText(tail.length, 0)
        input.commitText(replacement, 1)
    }

    private fun romanTail(): String {
        val input = currentInputConnection ?: return composing.toString()
        val before = input.getTextBeforeCursor(120, 0)?.toString().orEmpty()

        val match = Regex("([A-Za-z']+(?:\\s+[A-Za-z']+)*)$").find(before)
        val found = match?.value?.trim().orEmpty()

        if (found.isNotBlank()) return found

        return composing.toString()
    }

    private fun hasKhmerBeforeTail(tail: String): Boolean {
        val input = currentInputConnection ?: return false
        val before = input.getTextBeforeCursor(160, 0)?.toString().orEmpty()
        if (tail.isBlank() || before.length < tail.length) return false

        val prefix = before.dropLast(tail.length).trimEnd()
        val last = prefix.lastOrNull() ?: return false
        return last in '\u1780'..'\u17FF'
    }

    private fun isSecureInput(inputType: Int): Boolean {
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION

        val textPassword = inputClass == InputType.TYPE_CLASS_TEXT &&
            variation in setOf(
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            )

        val numberPassword = inputClass == InputType.TYPE_CLASS_NUMBER &&
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD

        return textPassword || numberPassword
    }

    private fun clearSuggestions() {
        suggestionButtons.forEach { button ->
            button.text = ""
            button.visibility = View.INVISIBLE
            button.setOnClickListener(null)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
