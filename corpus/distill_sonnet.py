#!/usr/bin/env python3
"""Distil Yoda pairs from Claude Sonnet, the teacher chosen on 2026-09-30.

Sends the pool in batches through `claude -p` (subscription, no API key),
asks for a JSON array back, and applies a meaning gate before keeping a pair:
negation count and modal verbs must match the source, because the review ruled that
word forms may change but meaning may not.

Resumable: each batch is written to --work/batch-NNNN.json and skipped on rerun.

  python3 distill_sonnet.py --pool pool.jsonl --work work/ --out pairs.jsonl \
      --rejects rejects.jsonl --prompt sonnet-teacher-prompt.txt
"""

import argparse
import json
import re
import subprocess
import sys
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from yoda_tokenize import tokenize, is_punctuation

NEG = {
    "no": {"ikke", "aldri", "ingen", "ingenting", "intet"},
    "en": {"not", "never", "no", "nobody", "nothing", "none", "nowhere", "cannot"},
}

# Surface form -> modal lemma. English contractions map to the verb they carry.
MODALS = {
    "no": {
        "vil": "vil", "ville": "vil", "skal": "skal", "skulle": "skal",
        "kan": "kan", "kunne": "kan", "må": "må", "måtte": "må",
        "bør": "bør", "burde": "bør",
    },
    "en": {
        "will": "will", "'ll": "will", "won't": "will", "would": "would", "'d": "would",
        "wouldn't": "would", "can": "can", "can't": "can", "cannot": "can",
        "could": "could", "couldn't": "could", "must": "must", "mustn't": "must",
        "should": "should", "shouldn't": "should", "may": "may", "might": "might",
        "shall": "shall", "shan't": "shall",
    },
}


def words(text: str) -> list[str]:
    return [t.lower().replace("’", "'") for t in tokenize(text) if not is_punctuation(t)]


def negations(ws: list[str], lang: str) -> int:
    n = sum(w in NEG[lang] for w in ws)
    if lang == "en":
        n += sum(w.endswith("n't") for w in ws)
    return n


def modals(ws: list[str], lang: str) -> Counter:
    out = Counter()
    for w in ws:
        if w in MODALS[lang]:
            out[MODALS[lang][w]] += 1
        elif lang == "en":
            for suffix in ("'ll", "'d"):
                if w.endswith(suffix) and w != suffix:
                    out[MODALS["en"][suffix]] += 1
    return out


def meaning_gate(src: str, out: str, lang: str) -> str | None:
    """Returns a reject reason, or None if the pair passes."""
    if not out.strip():
        return "empty"
    s, o = words(src), words(out)
    if not 0.6 <= len(o) / max(len(s), 1) <= 1.6:
        return f"length {len(s)}->{len(o)}"
    if negations(s, lang) != negations(o, lang):
        return "negation changed"
    if modals(s, lang) != modals(o, lang):
        return f"modal changed {dict(modals(s, lang))}->{dict(modals(o, lang))}"
    if out.strip() == src.strip():
        return "unchanged"
    return None


def run_batch(idx: int, items: list[dict], header: str, work: Path, model: str) -> Path:
    path = work / f"batch-{idx:04d}.json"
    if path.exists():
        return path
    listing = "\n".join(f"{i + 1}. [{it['lang']}] {it['text']}" for i, it in enumerate(items))
    prompt = (
        header.rstrip()
        + "\n\nReturn ONLY a JSON array, one object per sentence, in order: "
        + '[{"i": 1, "y": "<yoda line>"}, ...]. No prose, no code fence.\n\nSentences:\n'
        + listing
    )
    proc = subprocess.run(
        ["claude", "-p", "--model", model, prompt],
        capture_output=True, text=True, stdin=subprocess.DEVNULL, timeout=600,
    )
    text = proc.stdout.strip()
    m = re.search(r"\[.*\]", text, re.S)
    if proc.returncode != 0 or not m:
        raise RuntimeError(f"batch {idx}: rc={proc.returncode} out={text[:200]!r} err={proc.stderr[:200]!r}")
    answers = {a["i"]: a["y"] for a in json.loads(m.group(0))}
    result = [{**it, "yoda": answers.get(i + 1, "")} for i, it in enumerate(items)]
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(result, ensure_ascii=False), encoding="utf-8")
    tmp.rename(path)
    return path


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--pool", required=True)
    ap.add_argument("--work", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--rejects", required=True)
    ap.add_argument("--prompt", required=True, help="teacher prompt header, without the sentence list")
    ap.add_argument("--batch", type=int, default=100)
    ap.add_argument("--workers", type=int, default=3)
    ap.add_argument("--limit", type=int, default=0, help="only the first N pool lines (0 = all)")
    ap.add_argument("--model", default="sonnet")
    args = ap.parse_args()

    header = Path(args.prompt).read_text(encoding="utf-8")
    header = header.split("Answer with one line per sentence")[0]
    pool = [json.loads(l) for l in open(args.pool, encoding="utf-8") if l.strip()]
    if args.limit:
        pool = pool[: args.limit]
    work = Path(args.work)
    work.mkdir(parents=True, exist_ok=True)
    batches = [pool[i : i + args.batch] for i in range(0, len(pool), args.batch)]

    failed = 0
    with ThreadPoolExecutor(args.workers) as ex:
        futures = [ex.submit(run_batch, i, b, header, work, args.model) for i, b in enumerate(batches)]
        for n, f in enumerate(futures):
            try:
                f.result()
            except Exception as e:  # one bad batch must not sink the run; it reruns next time
                failed += 1
                print(f"FAILED {e}", file=sys.stderr)
            if (n + 1) % 10 == 0:
                print(f"{n + 1}/{len(batches)} batches", flush=True)

    kept, why = 0, Counter()
    with open(args.out, "w", encoding="utf-8") as fo, open(args.rejects, "w", encoding="utf-8") as fr:
        for p in sorted(work.glob("batch-*.json")):
            for it in json.loads(p.read_text(encoding="utf-8")):
                reason = meaning_gate(it["text"], it["yoda"], it["lang"])
                if reason:
                    why[f"{it['lang']} {reason.split(' {')[0].split(' ')[0]}"] += 1
                    fr.write(json.dumps({"lang": it["lang"], "input": it["text"], "teacher": it["yoda"],
                                         "reason": reason}, ensure_ascii=False) + "\n")
                else:
                    kept += 1
                    fo.write(json.dumps({"lang": it["lang"], "input": it["text"], "output": it["yoda"]},
                                        ensure_ascii=False) + "\n")
    print(f"kept {kept}; rejected {sum(why.values())}: {dict(why)}; failed batches {failed}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
