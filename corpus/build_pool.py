#!/usr/bin/env python3
"""Build the source-sentence pool for the distillation corpus.

Input: Tatoeba per-language sentence dumps (id \t lang \t text).
Output: JSONL of {"id", "lang", "text"} candidates.

Selection is deliberately conservative. The teacher is asked to MOVE
constituents, so a source sentence is only useful if it has constituents worth
moving: a finite verb and enough material around it. Sentences that would come
back untouched are wasted teacher time, and sentences with quotes, ellipses or
embedded punctuation give the teacher an excuse to rewrite rather than reorder.
"""

import argparse
import json
import random
import re
import sys

sys.path.insert(0, str(__import__("pathlib").Path(__file__).parent))
from yoda_tokenize import tokenize, is_punctuation

# Anything that invites the teacher to rewrite rather than reorder.
BANNED = re.compile(r'["“”«»()\[\]{}<>*_/\\|@#§~`]|\.\.\.|--|\d')


def words(text: str) -> list[str]:
    return [t for t in tokenize(text) if not is_punctuation(t)]


def acceptable(text: str, min_w: int, max_w: int) -> bool:
    text = text.strip()
    if not text.endswith("."):
        return False          # declaratives only; Classic style is defined on them
    if text.count(".") != 1:
        return False          # no abbreviations, no run-ons
    if BANNED.search(text):
        return False
    w = words(text)
    if not (min_w <= len(w) <= max_w):
        return False
    if sum(1 for x in w if x[:1].isupper()) > 2:
        return False          # name-heavy sentences stress the gazetteer, not the syntax
    if any(len(x) > 20 for x in w):
        return False
    return True


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--tsv", action="append", required=True,
                    metavar="LANG=PATH", help="e.g. no=nob.tsv")
    ap.add_argument("--out", required=True)
    ap.add_argument("--per-lang", type=int, default=4000)
    ap.add_argument("--min-words", type=int, default=5)
    ap.add_argument("--max-words", type=int, default=14)
    ap.add_argument("--seed", type=int, default=20260910)
    args = ap.parse_args()

    rng = random.Random(args.seed)
    picked: list[dict] = []

    for spec in args.tsv:
        lang, path = spec.split("=", 1)
        seen: set[str] = set()
        cands: list[dict] = []
        with open(path, encoding="utf-8") as fh:
            for line in fh:
                parts = line.rstrip("\n").split("\t")
                if len(parts) < 3:
                    continue
                sid, text = parts[0], parts[2].strip()
                key = text.lower()
                if key in seen or not acceptable(text, args.min_words, args.max_words):
                    continue
                seen.add(key)
                cands.append({"id": f"tatoeba-{sid}", "lang": lang, "text": text})
        rng.shuffle(cands)
        take = cands[: args.per_lang]
        picked.extend(take)
        print(f"{lang}: {len(cands)} acceptable of pool, took {len(take)}", file=sys.stderr)

    rng.shuffle(picked)
    with open(args.out, "w", encoding="utf-8") as fh:
        for row in picked:
            fh.write(json.dumps(row, ensure_ascii=False) + "\n")
    print(f"wrote {len(picked)} -> {args.out}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
