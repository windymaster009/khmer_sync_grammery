# Khmer Sync Dataset API

This small API accepts only text that contributors explicitly submit from the **Contribute Roman Khmer** screen.

It does **not** receive text from the Accessibility/Gboard conversion service.

## MongoDB collections

- `contributions` — one raw sentence per intentional submission
- `roman_word_stats` — frequency for each Roman token
- `roman_phrase_stats` — frequency for normalized Roman phrases

New contributions start with:

```
status: untranslated
reviewStatus: pending
khmerText: null
```

## Run locally

PowerShell:

```powershell
python -m pip install -r server/requirements.txt
$env:MONGO_URI="<your MongoDB Atlas URI>"
python -m uvicorn server.main:app --host 0.0.0.0 --port 8788
```

Test:

```
http://localhost:8788/health
http://localhost:8788/api/stats
```

For a phone on the same Wi-Fi, set the app's Dataset Server URL to:

```
http://<YOUR-PC-LAN-IP>:8788
```

For friends outside your network, deploy this API behind HTTPS and use that public HTTPS base URL in the app.

## Important

Keep `MONGO_URI` on the server only. Never place the MongoDB URI/password inside the Android APK.
