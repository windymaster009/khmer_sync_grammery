#!/usr/bin/env python3
"""Seed the current Romanized-Khmer dictionary into MongoDB.

The MongoDB URI is read only from MONGO_URI. It is never stored in source code.
The script is idempotent: rerunning it upserts existing mappings instead of
creating duplicates.
"""

from __future__ import annotations

import json
import os
from datetime import datetime, timezone
from pathlib import Path

from pymongo import ASCENDING, MongoClient, UpdateOne


ROOT = Path(__file__).resolve().parents[1]
WORD_FILE = ROOT / "app/src/main/assets/roman_khmer_dictionary.json"
PHRASE_FILE = ROOT / "app/src/main/assets/roman_khmer_phrases.json"


def load_json(path: Path) -> dict[str, str]:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def normalize_roman(value: str) -> str:
    return " ".join(value.strip().lower().split())


def seed_collection(collection, mappings: dict[str, str], mapping_type: str) -> int:
    now = datetime.now(timezone.utc)
    operations = []

    for roman, khmer in mappings.items():
        normalized = normalize_roman(roman)
        operations.append(
            UpdateOne(
                {"normalizedRoman": normalized, "khmer": khmer},
                {
                    "$set": {
                        "roman": roman,
                        "normalizedRoman": normalized,
                        "khmer": khmer,
                        "type": mapping_type,
                        "enabled": True,
                        "source": "repo-seed",
                        "updatedAt": now,
                    },
                    "$setOnInsert": {
                        "frequency": 0,
                        "createdAt": now,
                    },
                },
                upsert=True,
            )
        )

    if operations:
        collection.bulk_write(operations, ordered=False)

    return len(operations)


def main() -> None:
    uri = os.getenv("MONGO_URI")
    if not uri:
        raise SystemExit("MONGO_URI is required.")

    words = load_json(WORD_FILE)
    phrases = load_json(PHRASE_FILE)

    client = MongoClient(
        uri,
        serverSelectionTimeoutMS=15_000,
        connectTimeoutMS=15_000,
        appname="KhmerSyncSeeder",
    )

    try:
        client.admin.command("ping")
        db = client.get_default_database()

        word_collection = db["words"]
        phrase_collection = db["phrases"]

        word_collection.create_index(
            [("normalizedRoman", ASCENDING), ("khmer", ASCENDING)],
            unique=True,
            name="roman_khmer_unique",
        )
        word_collection.create_index(
            [("normalizedRoman", ASCENDING), ("enabled", ASCENDING)],
            name="roman_lookup",
        )
        word_collection.create_index(
            [("khmer", ASCENDING)],
            name="khmer_lookup",
        )

        phrase_collection.create_index(
            [("normalizedRoman", ASCENDING), ("khmer", ASCENDING)],
            unique=True,
            name="phrase_khmer_unique",
        )
        phrase_collection.create_index(
            [("normalizedRoman", ASCENDING), ("enabled", ASCENDING)],
            name="phrase_lookup",
        )

        word_count = seed_collection(word_collection, words, "word")
        phrase_count = seed_collection(phrase_collection, phrases, "phrase")

        print(
            f"MongoDB seed complete: {word_count} word mappings and "
            f"{phrase_count} phrase mappings upserted."
        )
        print(
            f"Database totals: {word_collection.count_documents({})} words, "
            f"{phrase_collection.count_documents({})} phrases."
        )
    finally:
        client.close()


if __name__ == "__main__":
    main()
