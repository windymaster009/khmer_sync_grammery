# Khmer Sync

Khmer Sync converts **Romanized Khmer typed with English letters** into Khmer script.

The recommended mode keeps **Gboard (or another normal keyboard) as the default**. Khmer Sync runs as an Android Accessibility service and replaces only high-confidence Romanized-Khmer chunks after Space or punctuation.

Examples:

- `nh jg tv psa` → `ខ្ញុំចង់ទៅផ្សារ`
- `nh jg tv psa thinh ey nham` → `ខ្ញុំចង់ទៅផ្សារទិញអីញ៉ាំ`
- `nh ot dg te` → `ខ្ញុំអត់ដឹងទេ`

## Recommended: Gboard Integration

1. Install the APK.
2. Keep **Gboard** selected as the default keyboard.
3. Open **Khmer Sync**.
4. Tap **Turn on Gboard Integration**.
5. Android opens Accessibility settings; enable **Khmer Sync • Gboard Integration** once.
6. Return to Khmer Sync and type in the test field using Gboard.

Android does not allow apps to silently grant themselves Accessibility access, so this one approval cannot be skipped.

### Safety behavior

- Password/PIN fields are ignored.
- Conversion is local.
- The Android app currently has no `INTERNET` permission.
- Typed text is not logged or uploaded.
- Single ambiguous words such as `tv` are not aggressively converted unless Khmer context already exists.
- Mixed text such as `bro nh jg tv psa` can preserve the English portion and convert the strong Khmer suffix.

## Fallback: Khmer Sync Keyboard

The original custom IME remains available for testing/fallback. You do **not** need to select it when Gboard Integration is enabled.

## Dictionary

Word variants:

`app/src/main/assets/roman_khmer_dictionary.json`

Phrase overrides:

`app/src/main/assets/roman_khmer_phrases.json`

MongoDB seed tooling:

`tools/seed_mongodb.py`

The Mongo URI must stay in an environment variable or GitHub secret named `MONGO_URI`; never place it inside the APK or repository.

## Architecture

```
Gboard / Samsung Keyboard / other IME
                |
                v
        Android text field
                |
        Space / punctuation
                |
                v
KhmerAccessibilityService
                |
                v
RomanKhmerConverter
                |
     high-confidence match?
          |           |
         yes          no
          |           |
          v           v
 replace chunk     leave text
          |
          v
      Khmer text
```

## Build

GitHub Actions builds a debug APK on pushes to `main`.

Local build requirements:

- JDK 17
- Android SDK 37
- Gradle 9.6.0

```bash
gradle :app:assembleDebug
```
