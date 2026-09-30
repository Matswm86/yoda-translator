#!/usr/bin/env python3
"""Draw the step-5 review sample for a human reader.

Random sampling wastes the reviewer on easy pairs. Every pair in the corpus has
already passed the mechanical morphology check, so what is left to catch is
*word order that is wrong but uses the right words* - and the strongest
available signal for that is disagreement between the two independent methods:
the gemma3:12b teacher and the app's rule engine.

The sample is therefore stratified:
  - DISAGREE pairs, where teacher and rule engine produced different strings.
    These are where a judgement is actually needed.
  - AGREE pairs, sampled as a control. If these are wrong too, the problem is
    the style definition, not the teacher.
Both languages are represented in proportion to the corpus.
"""

import argparse
import json
import random
from collections import Counter


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--pairs", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("-n", type=int, default=100)
    ap.add_argument("--disagree-share", type=float, default=0.7)
    ap.add_argument("--seed", type=int, default=20260910)
    args = ap.parse_args()

    rows = [json.loads(l) for l in open(args.pairs, encoding="utf-8") if l.strip()]
    agree = [r for r in rows if r["agrees_with_rule_engine"]]
    disagree = [r for r in rows if not r["agrees_with_rule_engine"]]

    rng = random.Random(args.seed)
    rng.shuffle(agree)
    rng.shuffle(disagree)

    want_dis = min(len(disagree), int(args.n * args.disagree_share))
    want_agr = min(len(agree), args.n - want_dis)
    want_dis = min(len(disagree), args.n - want_agr)   # backfill if agree is short
    sample = disagree[:want_dis] + agree[:want_agr]
    rng.shuffle(sample)

    langs = Counter(r["lang"] for r in sample)
    with open(args.out, "w", encoding="utf-8") as fh:
        fh.write("# Step 5 review sample\n\n")
        fh.write(f"{len(sample)} pairs of {len(rows)} in the corpus "
                 f"({want_dis} where the teacher and the rule engine disagree, "
                 f"{want_agr} where they agree). Languages: "
                 f"{', '.join(f'{k}={v}' for k, v in sorted(langs.items()))}.\n\n")
        fh.write("Every pair below already passed the word-form check, so the words "
                 "are guaranteed unchanged. What needs judging is the ORDER.\n\n")
        fh.write("Mark each one PASS or FAIL. A FAIL is: order that is not grammatical "
                 "Yoda, or a dangling subordinator like the `hvis` case in the README "
                 "(\"How it was built\").\n\n---\n\n")
        for i, r in enumerate(sample, 1):
            tag = "AGREE" if r["agrees_with_rule_engine"] else "DISAGREE"
            fh.write(f"## {i}. [{r['lang']}] {tag}\n\n")
            fh.write(f"- in         : {r['input']}\n")
            fh.write(f"- teacher 12B: {r['output']}\n")
            if not r["agrees_with_rule_engine"]:
                fh.write(f"- rule engine: {r['rule_engine']}\n")
            fh.write("- verdict    : \n\n")
    print(f"wrote {len(sample)} pairs -> {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
