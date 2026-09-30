#!/usr/bin/env python3
"""Distil gemma3:12b into Yoda pairs, with the morphology rule enforced.

Every teacher output is checked against the same rule the app enforces: the
multiset of word forms out must equal the multiset in. A teacher line that
swaps `vil` for `må`, drops `hvis` or changes tense is rejected mechanically
before a human ever reads it - that is the exact failure mode of the gemma3:4b
outputs in the README ("How it was built").

The rule engine's own Classic output is carried alongside each pair so that
review can be spent on the sentences where teacher and rule engine DISAGREE.
Where they agree, two independent methods produced the same string.

Prompts are taken verbatim from the app's MediaPipeEngine.promptFor().
"""

import argparse
import csv
import json
import pathlib
import sys
import time

import requests

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from yoda_tokenize import morphology_preserved, bag_diff
from clause_check import clause_integrity

from prompts import prompt_for  # noqa: E402  (after sys.path tweak)


def clean(reply: str) -> str:
    """Strip the chatter models wrap around a one-line answer."""
    text = reply.strip()
    for prefix in ("Original:", "Yoda:", "Svar:", "Answer:", "Setning:"):
        if text.startswith(prefix):
            text = text[len(prefix):].strip()
    # keep the first non-empty line only
    for line in text.splitlines():
        line = line.strip().strip("`")
        if line:
            text = line
            break
    if len(text) >= 2 and text[0] in "\"'“«" and text[-1] in "\"'”»":
        text = text[1:-1].strip()
    return text


def generate(host: str, model: str, prompt: str, timeout: int) -> str:
    r = requests.post(
        f"{host}/api/generate",
        json={
            "model": model,
            "prompt": prompt,
            "stream": False,
            # Deterministic: the corpus must be reproducible from the pool.
            "options": {"temperature": 0.0, "top_k": 1, "seed": 20260910,
                        "num_predict": 128},
        },
        timeout=timeout,
    )
    r.raise_for_status()
    return r.json().get("response", "")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--screened", required=True, help="TSV from RuleEngineBatchTool")
    ap.add_argument("--out", required=True, help="JSONL of accepted pairs")
    ap.add_argument("--rejects", required=True, help="JSONL of rejected teacher lines")
    ap.add_argument("--model", default="gemma3:12b")
    ap.add_argument("--prompt-version", default="v2", choices=["v1", "v2"])
    ap.add_argument("--host", default="http://127.0.0.1:11434")
    ap.add_argument("--limit", type=int, default=0, help="0 = all")
    ap.add_argument("--min-confidence", type=float, default=0.75)
    ap.add_argument("--timeout", type=int, default=180)
    ap.add_argument("--progress-every", type=int, default=10)
    args = ap.parse_args()

    rows = [
        r for r in csv.DictReader(open(args.screened, encoding="utf-8"), delimiter="\t")
        if float(r["confidence"]) >= args.min_confidence
        and r["classic"].strip() != r["source"].strip()
    ]
    if args.limit:
        rows = rows[: args.limit]

    kept = rejected = 0
    agree = 0
    t0 = time.time()
    fout = open(args.out, "w", encoding="utf-8")
    frej = open(args.rejects, "w", encoding="utf-8")

    for i, row in enumerate(rows, 1):
        src, lang = row["source"], row["lang"]
        try:
            out = clean(generate(args.host, args.model, prompt_for(src, lang, args.prompt_version), args.timeout))
        except Exception as exc:                      # noqa: BLE001
            frej.write(json.dumps({"lang": lang, "input": src, "error": str(exc)},
                                  ensure_ascii=False) + "\n")
            rejected += 1
            continue

        reject = None
        if not out or not morphology_preserved(src, out):
            dropped, invented = bag_diff(src, out)
            reject = {"reason": "morphology", "dropped": dropped, "invented": invented}
        else:
            clause_ok, broken = clause_integrity(src, out, lang)
            if not clause_ok:
                reject = {"reason": "clause", "broken": broken}

        if reject is not None:
            frej.write(json.dumps(
                {"lang": lang, "input": src, "teacher": out, **reject},
                ensure_ascii=False) + "\n")
            rejected += 1
        else:
            same = out.strip() == row["classic"].strip()
            agree += same
            fout.write(json.dumps({
                "lang": lang, "input": src, "output": out,
                "rule_engine": row["classic"], "agrees_with_rule_engine": same,
                "confidence": float(row["confidence"]),
                "prompt_version": args.prompt_version,
            }, ensure_ascii=False) + "\n")
            kept += 1

        if i % args.progress_every == 0 or i == len(rows):
            fout.flush(); frej.flush()
            el = time.time() - t0
            rate = el / i
            print(f"{i}/{len(rows)}  kept={kept} rejected={rejected} "
                  f"agree={agree}  {rate:.1f}s/sentence  "
                  f"eta={(len(rows) - i) * rate / 3600:.1f}h", file=sys.stderr, flush=True)

    fout.close(); frej.close()
    print(f"\ndone: kept={kept} rejected={rejected} "
          f"({rejected / max(1, kept + rejected) * 100:.1f}% rejected), "
          f"teacher==rule-engine on {agree}/{kept}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
