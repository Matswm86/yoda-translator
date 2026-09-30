"""Second mechanical gate: subordinate-clause block integrity.

The morphology check proves no word form changed. It says nothing about order,
and the measured v1 teacher failures are almost all order failures inside a
subordinate clause:

    hvis du trener hardt hver dag   ->   hvis hardt du trener hver dag
    at han ringer deg senere        ->   at han deg senere ringer

The decided Classic style moves a subordinate clause as ONE WHOLE BLOCK and
leaves its internal order alone. That is checkable without a parser: take the
span from the subordinator to the end of the source sentence, and require the
same token sequence to appear contiguously somewhere in the output.

This also catches the dangling-subordinator failure from the README ("How it was built")
(`... du vil lære tålmodighet hvis.`), because a trailing lone `hvis` is not
followed by the clause it introduced.
"""

import sys
import pathlib

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from yoda_tokenize import tokenize, is_punctuation

SUBORD = {
    "no": {"hvis", "dersom", "når", "fordi", "at", "som", "da", "mens", "om",
           "ettersom", "før", "etter", "siden", "enn"},
    "en": {"if", "when", "because", "that", "which", "while", "since",
           "before", "after", "unless", "although", "though", "whether"},
}


def _words(text: str) -> list[str]:
    return [t.lower() for t in tokenize(text) if not is_punctuation(t)]


def _contiguous(haystack: list[str], needle: list[str]) -> bool:
    if not needle:
        return True
    return any(
        haystack[i:i + len(needle)] == needle
        for i in range(len(haystack) - len(needle) + 1)
    )


def clause_spans(source: str, lang: str) -> list[list[str]]:
    """Each subordinate clause in the source, as a token list.

    A clause runs from its subordinator to the next comma or to the end of the
    sentence - a deliberately shallow rule, but one that matches the shape of
    the sentences the pool actually contains (5-14 words, one finite clause plus
    at most one subordinate clause).
    """
    toks = _words(source)
    marks = SUBORD["en" if lang == "en" else "no"]
    # comma positions, in word-index space
    raw = [t for t in tokenize(source)]
    word_idx, commas = -1, set()
    for t in raw:
        if is_punctuation(t):
            if t == ",":
                commas.add(word_idx + 1)   # clause break before the next word
        else:
            word_idx += 1

    spans = []
    for i, t in enumerate(toks):
        if t in marks and i > 0:           # a sentence-initial subordinator is the main frame
            end = len(toks)
            for c in sorted(commas):
                if c > i:
                    end = c
                    break
            if end - i >= 2:               # a bare subordinator is not a clause
                spans.append(toks[i:end])
    return spans


def clause_integrity(source: str, produced: str, lang: str) -> tuple[bool, list[str]]:
    """(ok, broken clauses). ok=True when the source has no subordinate clause."""
    out = _words(produced)
    broken = [s for s in clause_spans(source, lang) if not _contiguous(out, s)]
    return (not broken), [" ".join(s) for s in broken]


if __name__ == "__main__":
    cases = [
        ("Du vil lære tålmodighet hvis du trener hardt hver dag.",
         "Lære tålmodighet du vil, hvis du trener hardt hver dag.", "no", True),
        ("Du vil lære tålmodighet hvis du trener hardt hver dag.",
         "Tålmodighet vil du lære, hvis hardt du trener hver dag.", "no", False),
        ("Du vil lære tålmodighet hvis du trener hardt hver dag.",
         "Trener hardt hver dag, du vil lære tålmodighet hvis.", "no", False),
        ("Jeg sier til Tom at han ringer deg senere.",
         "Til Tom jeg sier at han deg senere ringer.", "no", False),
        ("Jeg sier til Tom at han ringer deg senere.",
         "Til Tom jeg sier, at han ringer deg senere.", "no", True),
        ("You will learn patience if you train hard every day.",
         "Learn patience you will, if you train hard every day.", "en", True),
    ]
    bad = 0
    for src, out, lang, want in cases:
        ok, broken = clause_integrity(src, out, lang)
        flag = "ok " if ok == want else "BAD"
        bad += ok != want
        print(f"{flag} expect={want} got={ok} broken={broken}\n     {out}")
    print("\nself-test:", "PASS" if bad == 0 else f"{bad} FAILED")
