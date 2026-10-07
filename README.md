# Khmer Sync

Khmer Sync is an Android keyboard/IME prototype that lets people type **Romanized Khmer using English letters** and convert it into Khmer script.

Examples:

- `nh jg tv psa` → `ខ្ញុំចង់ទៅផ្សារ`
- `nh jg tv psa thinh ey nham` → `ខ្ញុំចង់ទៅផ្សារទិញអីញ៉ាំ`
- `nh ot dg te` → `ខ្ញុំអត់ដឹងទេ`
- `realy` → `really` (starter English typo correction)

## V1 behavior

- Normal QWERTY keyboard.
- Romanized Khmer suggestions appear above the keyboard.
- Confident multi-word Khmer can auto-convert when Space is pressed.
- After Khmer context is established, known Romanized Khmer words can auto-convert one by one.
- English text stays English.
- A small starter English typo map corrects common mistakes.
- Password and PIN fields disable conversion/suggestions.
- No Internet permission and no typed-text logging.

## Setup

1. Open the project in Android Studio.
2. Use JDK 17.
3. The project uses Android Gradle Plugin 9.4.0 and compile/target SDK 37.
4. If you want a Gradle wrapper for CLI builds, run:
   ```bash
   gradle wrapper --gradle-version 9.6.0
   ```
5. Build and install the app.
6. Open **Khmer Sync**.
7. Tap **Enable Khmer Sync Keyboard**.
8. Enable it in Android settings.
9. Return to the app and tap **Choose Khmer Sync Keyboard**.

## Extending Khmer recognition

The converter is deliberately data-driven so Cambodian texting variants can be added without changing the IME code.

Word variants:

`app/src/main/assets/roman_khmer_dictionary.json`

Phrase overrides/context:

`app/src/main/assets/roman_khmer_phrases.json`

For example:

```json
{
  "nh": "ខ្ញុំ",
  "jg": "ចង់",
  "tv": "ទៅ",
  "psa": "ផ្សារ"
}
```

## Architecture

```
English letters
      |
      v
KhmerImeService
      |
      +--> RomanKhmerConverter --> Khmer suggestion / auto-convert
      |
      +--> EnglishCorrectionEngine --> English typo suggestion/correction
      |
      v
Android InputConnection
      |
      v
Messenger / Telegram / Chrome / Notes / etc.
```

## Next steps

V1 is intentionally a prototype. The important next work is collecting real Romanized-Khmer typing samples and improving ranking/context. After that we can add a much larger English dictionary, personalized local learning, better punctuation/number layouts, emoji, themes, and optional on-device ML.
