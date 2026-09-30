"""Port of the app's Tokenizer + RuleEngine.verifyMorphology.

Kept deliberately faithful to
`app/src/main/java/no/mwm/yoda/engine/rule/Tokenizer.kt` so that a pair this
module accepts is a pair the shipped engine would also accept. The Kotlin
regex is

    [\\p{L}][\\p{L}\\p{M}]*(?:['’\\-][\\p{L}\\p{M}]+)*|\\d+(?:[.,:]\\d+)*|\\S

which stdlib `re` cannot express (no \\p{L}), so it is hand-scanned instead.
"""

import unicodedata

SENTENCE_END = {".", "!", "?", "…"}
_JOINERS = {"'", "’", "-"}


def _is_mark(ch: str) -> bool:
    return unicodedata.category(ch).startswith("M")


def _is_letter(ch: str) -> bool:
    return ch.isalpha()


def tokenize(sentence: str) -> list[str]:
    """Surface tokens, matching Tokenizer.tokenize."""
    out: list[str] = []
    i, n = 0, len(sentence)
    while i < n:
        ch = sentence[i]
        if _is_letter(ch):
            start = i
            i += 1
            while i < n and (_is_letter(sentence[i]) or _is_mark(sentence[i])):
                i += 1
            # optional ['-] joined continuations, each needing a letter after it
            while (
                i + 1 < n
                and sentence[i] in _JOINERS
                and (_is_letter(sentence[i + 1]) or _is_mark(sentence[i + 1]))
            ):
                i += 1
                while i < n and (_is_letter(sentence[i]) or _is_mark(sentence[i])):
                    i += 1
            out.append(sentence[start:i])
        elif ch.isdigit():
            start = i
            i += 1
            while i < n and sentence[i].isdigit():
                i += 1
            while i + 1 < n and sentence[i] in ".,:" and sentence[i + 1].isdigit():
                i += 1
                while i < n and sentence[i].isdigit():
                    i += 1
            out.append(sentence[start:i])
        elif ch.isspace():
            i += 1
        else:
            out.append(ch)
            i += 1
    return out


def is_punctuation(tok: str) -> bool:
    return len(tok) > 0 and not any(c.isalnum() for c in tok)


def word_bag(sentence: str) -> list[str]:
    """The multiset the morphology rule compares, as a sorted list."""
    return sorted(
        t.lower() for t in tokenize(sentence) if not is_punctuation(t)
    )


def morphology_preserved(original: str, produced: str) -> bool:
    """True iff `produced` is a pure reordering of `original`'s word forms.

    Case may differ (moving a word changes which one starts the sentence) and
    punctuation is ignored (the transformer adds and removes commas by design).
    """
    return word_bag(original) == word_bag(produced)


def bag_diff(original: str, produced: str) -> tuple[list[str], list[str]]:
    """(words dropped, words invented) - for explaining a rejection."""
    from collections import Counter

    a, b = Counter(word_bag(original)), Counter(word_bag(produced))
    return sorted((a - b).elements()), sorted((b - a).elements())
