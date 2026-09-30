#!/usr/bin/env python3
"""Turn the rule engine's screened output into training pairs.

Input: the TSV written by RuleEngineBatchTool (lang, confidence, preserved,
source, classic). Output: JSONL of {"lang", "input", "output"} plus a stats
line per language.

A pair is kept only when all three hold:
  - the engine's word-form check passed (preserved == true),
  - the engine actually moved something (word order differs from the source),
  - parse confidence is at least --min-confidence.
Low-confidence lines are the imperatives and light-verb sentences the engine
handles badly; training on them would teach the student those mistakes.
"""

import argparse
import csv
import json
import sys
from collections import Counter

from yoda_tokenize import tokenize, is_punctuation


def word_order(text: str) -> list[str]:
    return [t.lower() for t in tokenize(text) if not is_punctuation(t)]


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--screened", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--min-confidence", type=float, default=0.75)
    args = ap.parse_args()

    kept: list[dict] = []
    why = Counter()
    per_lang = Counter()
    with open(args.screened, encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f, delimiter="\t", quoting=csv.QUOTE_NONE):
            lang = row["lang"]
            if row["preserved"] != "true":
                why[f"{lang} word-form check failed"] += 1
            elif word_order(row["source"]) == word_order(row["classic"]):
                why[f"{lang} untouched"] += 1
            elif float(row["confidence"]) < args.min_confidence:
                why[f"{lang} confidence < {args.min_confidence}"] += 1
            else:
                kept.append({"lang": lang, "input": row["source"], "output": row["classic"]})
                per_lang[lang] += 1

    with open(args.out, "w", encoding="utf-8") as f:
        for pair in kept:
            f.write(json.dumps(pair, ensure_ascii=False) + "\n")

    for lang, n in sorted(per_lang.items()):
        print(f"{lang}: kept {n}")
    for reason, n in sorted(why.items()):
        print(f"dropped {n}: {reason}")
    print(f"wrote {len(kept)} -> {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
