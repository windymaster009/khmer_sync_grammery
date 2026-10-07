package com.windymaster.khmersync

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusView: TextView
    private lateinit var autoSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(40), dp(24), dp(32))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "Khmer Sync"
            textSize = 30f
            setTextColor(Color.rgb(25, 25, 30))
        })

        root.addView(TextView(this).apply {
            text = "Type Khmer naturally with English letters. Khmer Sync converts Romanized Khmer into Khmer script while keeping normal English typing available."
            textSize = 16f
            setPadding(0, dp(12), 0, dp(24))
        })

        statusView = TextView(this).apply {
            textSize = 16f
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.rgb(242, 244, 248))
        }
        root.addView(statusView, fullWidth())

        root.addView(Button(this).apply {
            text = "1. Enable Khmer Sync Keyboard"
            isAllCaps = false
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        }, marginTop())

        root.addView(Button(this).apply {
            text = "2. Choose Khmer Sync Keyboard"
            isAllCaps = false
            setOnClickListener {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        }, marginTop())

        autoSwitch = Switch(this).apply {
            text = "Auto-convert confident Khmer on Space"
            textSize = 16f
            isChecked = prefs().getBoolean(PREF_AUTO_CONVERT, true)
            setPadding(0, dp(20), 0, dp(12))
            setOnCheckedChangeListener { _, checked ->
                prefs().edit().putBoolean(PREF_AUTO_CONVERT, checked).apply()
            }
        }
        root.addView(autoSwitch, fullWidth())

        root.addView(TextView(this).apply {
            text = "Try it"
            textSize = 20f
            setPadding(0, dp(24), 0, dp(8))
        })

        root.addView(EditText(this).apply {
            hint = "Try: nh jg tv psa thinh ey nham"
            minLines = 3
            gravity = Gravity.TOP
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(140)
        ))

        root.addView(TextView(this).apply {
            text = "Privacy: V1 has no Internet permission and does not store what you type. Conversion is disabled in password/PIN fields."
            textSize = 14f
            setPadding(0, dp(22), 0, 0)
        })

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val selected = current?.startsWith("$packageName/") == true

        statusView.text = when {
            selected -> "✓ Khmer Sync is enabled and selected as your current keyboard."
            enabled -> "✓ Khmer Sync is enabled. Choose it as your current keyboard."
            else -> "Setup needed: enable Khmer Sync, then select it as your keyboard."
        }
    }

    private fun prefs() = getSharedPreferences(PREFS, MODE_PRIVATE)

    private fun fullWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun marginTop() = fullWidth().apply {
        topMargin = dp(12)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val PREFS = "khmer_sync_prefs"
        const val PREF_AUTO_CONVERT = "auto_convert_on_space"
    }
}
