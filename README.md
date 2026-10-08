# Khmer Sync

Khmer Sync has two goals:

1. Keep **Gboard** as the default keyboard while converting high-confidence Romanized Khmer locally.
2. Build an opt-in Roman-Khmer dataset from examples that contributors intentionally submit.

## Gboard integration

Enable **Khmer Sync • Gboard Integration** once under Android Accessibility, then leave Gboard selected.

Examples:

- `nh jg tv psa` → `ខ្ញុំចង់ទៅផ្សារ`
- `nh ot dg te` → `ខ្ញុំអត់ដឹងទេ`

Password/PIN fields are ignored.

## Dataset collector

Open **Contribute Roman Khmer** from the app.

Contributors type exactly how they normally write Khmer using English letters, then press Send/Enter. The collector field is excluded from automatic Khmer conversion so the raw Roman text is preserved.

The phone:

- creates an anonymous random contributor ID;
- creates a unique submission ID;
- queues every submission locally first;
- retries later if the server is unavailable.

The server stores:

- `contributions` — raw untranslated sentences;
- `roman_word_stats` — word frequency;
- `roman_phrase_stats` — phrase frequency.

See [server/README.md](server/README.md) for running the API.

### Privacy boundary

Normal Gboard/Accessibility conversion stays on-device.

Only text deliberately entered into the **Dataset Collector** is uploaded. Do not collect private messages, credentials, names, phone numbers, or other personal information.

## MongoDB

Keep `MONGO_URI` on the server or in GitHub Actions secrets only. Never place the database password inside the Android APK.

Existing dictionary seed tooling remains in:

`tools/seed_mongodb.py`

## Build

GitHub Actions builds a debug APK on pushes to `main`.

Local requirements:

- JDK 17
- Android SDK 37
- Gradle 9.6.0

```bash
gradle :app:assembleDebug
```
