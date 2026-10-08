package com.windymaster.khmersync

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.Instant
import java.util.UUID

class CollectorActivity : Activity() {

    private lateinit var input: EditText
    private lateinit var apiUrlInput: EditText
    private lateinit var statusView: TextView
    private lateinit var queueView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(36), dp(24), dp(32))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "Contribute Roman Khmer"
            textSize = 28f
            setTextColor(Color.rgb(25, 25, 30))
        })

        root.addView(TextView(this).apply {
            text = "Type exactly how you normally write Khmer using English letters. Press Enter/Send to contribute the raw sentence."
            textSize = 16f
            setPadding(0, dp(12), 0, dp(12))
        })

        root.addView(TextView(this).apply {
            text = "Only submit text you intentionally want to contribute. Do not enter passwords, private messages, phone numbers, names, or other personal information."
            textSize = 14f
            setTextColor(Color.rgb(110, 70, 20))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(Color.rgb(255, 248, 230))
        }, fullWidth())

        input = EditText(this).apply {
            id = R.id.collector_input
            hint = "Example: nh jg tv psa thinh ey nham"
            textSize = 18f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEND
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND ||
                    actionId == EditorInfo.IME_ACTION_DONE
                ) {
                    submitCurrent()
                    true
                } else {
                    false
                }
            }
        }
        root.addView(input, marginTop(16))

        root.addView(Button(this).apply {
            text = "Send contribution"
            isAllCaps = false
            setOnClickListener { submitCurrent() }
        }, marginTop(10))

        statusView = TextView(this).apply {
            textSize = 15f
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.rgb(242, 244, 248))
        }
        root.addView(statusView, marginTop(14))

        queueView = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(10), 0, 0)
        }
        root.addView(queueView, fullWidth())

        root.addView(TextView(this).apply {
            text = "Dataset server"
            textSize = 18f
            setPadding(0, dp(28), 0, dp(8))
        })

        apiUrlInput = EditText(this).apply {
            id = R.id.dataset_api_url
            hint = "http://192.168.1.10:8788 or https://your-domain"
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(prefs().getString(PREF_DATASET_API_URL, "").orEmpty())
        }
        root.addView(apiUrlInput, fullWidth())

        root.addView(Button(this).apply {
            text = "Save server URL"
            isAllCaps = false
            setOnClickListener {
                val url = apiUrlInput.text.toString().trim().trimEnd('/')
                prefs().edit().putString(PREF_DATASET_API_URL, url).apply()
                statusView.text = if (url.isBlank()) {
                    "Server URL cleared. New contributions will stay queued on this phone."
                } else {
                    "Server saved. Sending queued contributions…"
                }
                flushQueue()
            }
        }, marginTop(8))

        root.addView(TextView(this).apply {
            text = "For the friend beta, set the server once. A production build can ship with this URL preconfigured so contributors never see this setting."
            textSize = 13f
            setPadding(0, dp(10), 0, 0)
        })

        setContentView(scroll)

        updateQueueCount()
        statusView.text = "Ready. Contributions are stored locally first, then synced to the dataset server."
        flushQueue()
    }

    override fun onResume() {
        super.onResume()
        updateQueueCount()
    }

    private fun submitCurrent() {
        val raw = input.text.toString().trim()
        if (raw.length < 2 || !raw.any { it.isLetter() }) {
            statusView.text = "Type a Romanized Khmer sentence before sending."
            return
        }

        val item = PendingContribution(
            submissionId = UUID.randomUUID().toString(),
            rawText = raw,
            createdAt = Instant.now().toString()
        )

        ContributionQueue.enqueue(this, item)
        input.setText("")
        statusView.text = "✓ Saved. Sending to the dataset server…"
        updateQueueCount()
        flushQueue()
    }

    private fun flushQueue() {
        val baseUrl = prefs().getString(PREF_DATASET_API_URL, "").orEmpty().trim()
        if (baseUrl.isBlank()) {
            if (ContributionQueue.size(this) > 0) {
                statusView.text = "Saved on this phone. Add the dataset server URL to sync."
            }
            updateQueueCount()
            return
        }

        val contributorId = contributorId()

        Thread {
            val pending = ContributionQueue.load(this)
            var sent = 0
            var failed = false

            for (item in pending) {
                val ok = DatasetApi.submit(baseUrl, contributorId, item)
                if (!ok) {
                    failed = true
                    break
                }

                ContributionQueue.remove(this, item.submissionId)
                sent += 1
            }

            runOnUiThread {
                updateQueueCount()
                statusView.text = when {
                    failed && sent > 0 ->
                        "Sent $sent contribution(s). Some are still queued; the app will retry later."
                    failed ->
                        "Could not reach the dataset server. Your contribution is safe on this phone and will retry later."
                    sent > 0 ->
                        "✓ Sent $sent contribution(s) to the dataset."
                    else ->
                        "Ready. Contributions are stored locally first, then synced to the dataset server."
                }
            }
        }.start()
    }

    private fun contributorId(): String {
        val existing = prefs().getString(PREF_CONTRIBUTOR_ID, null)
        if (!existing.isNullOrBlank()) return existing

        return UUID.randomUUID().toString().also { generated ->
            prefs().edit().putString(PREF_CONTRIBUTOR_ID, generated).apply()
        }
    }

    private fun updateQueueCount() {
        queueView.text = "Waiting to sync: ${ContributionQueue.size(this)}"
    }

    private fun prefs() = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)

    private fun fullWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun marginTop(value: Int) = fullWidth().apply {
        topMargin = dp(value)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val PREF_DATASET_API_URL = "dataset_api_url"
        const val PREF_CONTRIBUTOR_ID = "dataset_contributor_id"
    }
}
