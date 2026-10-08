package com.windymaster.khmersync

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
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

    private lateinit var gboardStatusView: TextView
    private lateinit var legacyKeyboardStatusView: TextView
    private lateinit var autoSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(36), dp(24), dp(32))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "Khmer Sync"
            textSize = 30f
            setTextColor(Color.rgb(25, 25, 30))
        })

        root.addView(TextView(this).apply {
            text = "Keep using Gboard. Khmer Sync converts Romanized Khmer in the background after a one-time Android permission."
            textSize = 16f
            setPadding(0, dp(12), 0, dp(22))
        })

        root.addView(TextView(this).apply {
            text = "Recommended • Gboard Integration"
            textSize = 20f
            setTextColor(Color.rgb(25, 25, 30))
        })

        gboardStatusView = statusCard()
        root.addView(gboardStatusView, marginTop(10))

        root.addView(Button(this).apply {
            text = "Turn on Gboard Integration"
            isAllCaps = false
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }, marginTop())

        root.addView(TextView(this).apply {
            text = "Android requires this one-time Accessibility approval. After it is enabled, leave Gboard as your default keyboard."
            textSize = 14f
            setPadding(0, dp(10), 0, dp(8))
        })

        autoSwitch = Switch(this).apply {
            text = "Auto-convert confident Khmer on Space"
            textSize = 16f
            isChecked = prefs().getBoolean(PREF_AUTO_CONVERT, true)
            setPadding(0, dp(14), 0, dp(12))
            setOnCheckedChangeListener { _, checked ->
                prefs().edit().putBoolean(PREF_AUTO_CONVERT, checked).apply()
            }
        }
        root.addView(autoSwitch, fullWidth())

        root.addView(TextView(this).apply {
            text = "Try it with Gboard"
            textSize = 20f
            setPadding(0, dp(20), 0, dp(8))
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
            text = "Help build the Khmer dataset"
            textSize = 20f
            setPadding(0, dp(26), 0, dp(6))
        })

        root.addView(TextView(this).apply {
            text = "Friends can intentionally submit the Romanized Khmer they normally type. We store it untranslated first, then translate/review the most common words and phrases later."
            textSize = 14f
            setPadding(0, 0, 0, dp(8))
        })

        root.addView(Button(this).apply {
            text = "Contribute Roman Khmer"
            isAllCaps = false
            setOnClickListener {
                startActivity(Intent(this@MainActivity, CollectorActivity::class.java))
            }
        }, fullWidth())

        root.addView(TextView(this).apply {
            text = "Fallback • Khmer Sync Keyboard"
            textSize = 18f
            setPadding(0, dp(26), 0, dp(6))
        })

        legacyKeyboardStatusView = TextView(this).apply {
            textSize = 14f
        }
        root.addView(legacyKeyboardStatusView, fullWidth())

        root.addView(Button(this).apply {
            text = "Enable fallback keyboard"
            isAllCaps = false
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        }, marginTop(8))

        root.addView(Button(this).apply {
            text = "Choose fallback keyboard"
            isAllCaps = false
            setOnClickListener {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        }, marginTop(8))

        root.addView(TextView(this).apply {
            text = "Privacy: normal Gboard conversion stays on-device. Only text deliberately submitted from the Dataset Collector is sent to the dataset server. Password/PIN fields are ignored."
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
        val accessibilityEnabled = isAccessibilityServiceEnabled()

        gboardStatusView.text = if (accessibilityEnabled) {
            "✓ Gboard Integration is ON. Keep Gboard selected and type normally."
        } else {
            "Setup needed: turn on Khmer Sync under Android Accessibility."
        }

        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val fallbackEnabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        )
        val fallbackSelected = current?.startsWith("$packageName/") == true

        legacyKeyboardStatusView.text = when {
            fallbackSelected -> "Fallback keyboard is currently selected."
            fallbackEnabled -> "Fallback keyboard is enabled but not selected."
            else -> "Fallback keyboard is off. That is fine when Gboard Integration is ON."
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(
            this,
            KhmerAccessibilityService::class.java
        ).flattenToString()

        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) {
                return true
            }
        }

        return false
    }

    private fun statusCard() = TextView(this).apply {
        textSize = 16f
        setPadding(dp(16), dp(16), dp(16), dp(16))
        setBackgroundColor(Color.rgb(242, 244, 248))
    }

    private fun prefs() = getSharedPreferences(PREFS, MODE_PRIVATE)

    private fun fullWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun marginTop(value: Int = 12) = fullWidth().apply {
        topMargin = dp(value)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val PREFS = "khmer_sync_prefs"
        const val PREF_AUTO_CONVERT = "auto_convert_on_space"
    }
}
