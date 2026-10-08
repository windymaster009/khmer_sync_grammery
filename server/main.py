from __future__ import annotations

import os
import re
from collections import Counter
from datetime import datetime, timezone

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from pymongo import ASCENDING, MongoClient, ReturnDocument, UpdateOne
from dotenv import load_dotenv


load_dotenv()
MONGO_URI = os.getenv("MONGO_URI")
if not MONGO_URI:
    raise RuntimeError("MONGO_URI is required")

client = MongoClient(
    MONGO_URI,
    serverSelectionTimeoutMS=15_000,
    connectTimeoutMS=15_000,
    appname="KhmerSyncDatasetApi",
)
db = client.get_default_database()

contributions = db["contributions"]
word_stats = db["roman_word_stats"]
phrase_stats = db["roman_phrase_stats"]

TOKEN_RE = re.compile(r"[A-Za-z']+")
LATIN_RE = re.compile(r"[A-Za-z]")

app = FastAPI(title="Khmer Sync Dataset API", version="0.1.0")


class ContributionIn(BaseModel):
    submissionId: str = Field(min_length=8, max_length=100)
    contributorId: str = Field(min_length=8, max_length=100)
    rawText: str = Field(min_length=2, max_length=300)
    createdAt: str | None = Field(default=None, max_length=80)
    consentVersion: int = Field(default=1, ge=1, le=100)


@app.on_event("startup")
def startup() -> None:
    client.admin.command("ping")

    contributions.create_index(
        [("submissionId", ASCENDING)],
        unique=True,
        name="submission_id_unique",
    )
    contributions.create_index(
        [("status", ASCENDING), ("createdAtServer", ASCENDING)],
        name="review_queue",
    )
    contributions.create_index(
        [("contributorId", ASCENDING), ("createdAtServer", ASCENDING)],
        name="contributor_history",
    )

    word_stats.create_index(
        [("roman", ASCENDING)],
        unique=True,
        name="roman_word_unique",
    )
    phrase_stats.create_index(
        [("roman", ASCENDING)],
        unique=True,
        name="roman_phrase_unique",
    )


@app.get("/health")
def health() -> dict:
    client.admin.command("ping")
    return {"ok": True}


@app.get("/api/stats")
def stats() -> dict:
    return {
        "contributions": contributions.count_documents({}),
        "untranslated": contributions.count_documents({"status": "untranslated"}),
        "uniqueRomanWords": word_stats.count_documents({}),
        "uniqueRomanPhrases": phrase_stats.count_documents({}),
    }


@app.post("/api/contributions")
def create_contribution(payload: ContributionIn) -> dict:
    raw = " ".join(payload.rawText.strip().split())
    if not LATIN_RE.search(raw):
        raise HTTPException(
            status_code=422,
            detail="Contribution must contain Roman/Latin letters.",
        )

    tokens = [token.lower() for token in TOKEN_RE.findall(raw)]
    if not tokens:
        raise HTTPException(status_code=422, detail="No Roman words found.")

    normalized = " ".join(tokens)
    now = datetime.now(timezone.utc)

    document = {
        "submissionId": payload.submissionId,
        "contributorId": payload.contributorId,
        "rawText": raw,
        "normalizedText": normalized,
        "tokens": tokens,
        "status": "untranslated",
        "khmerText": None,
        "reviewStatus": "pending",
        "source": "android-contributor",
        "consentVersion": payload.consentVersion,
        "createdAtClient": payload.createdAt,
        "createdAtServer": now,
        "updatedAt": now,
    }

    existing = contributions.find_one_and_update(
        {"submissionId": payload.submissionId},
        {"$setOnInsert": document},
        upsert=True,
        return_document=ReturnDocument.BEFORE,
    )

    inserted = existing is None
    if inserted:
        word_counts = Counter(tokens)
        word_operations = [
            UpdateOne(
                {"roman": roman},
                {
                    "$setOnInsert": {
                        "roman": roman,
                        "createdAt": now,
                    },
                    "$inc": {"count": count},
                    "$set": {"updatedAt": now},
                },
                upsert=True,
            )
            for roman, count in word_counts.items()
        ]
        if word_operations:
            word_stats.bulk_write(word_operations, ordered=False)

        phrase_stats.update_one(
            {"roman": normalized},
            {
                "$setOnInsert": {
                    "roman": normalized,
                    "createdAt": now,
                },
                "$inc": {"count": 1},
                "$set": {"updatedAt": now},
            },
            upsert=True,
        )

    return {
        "ok": True,
        "inserted": inserted,
        "submissionId": payload.submissionId,
    }
