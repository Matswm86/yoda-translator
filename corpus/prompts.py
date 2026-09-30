"""Teacher prompts, versioned.

v1 is the Round-2 prompt from the README ("How it was built"), kept verbatim and still shipped
in the app at engine/llm/MediaPipeEngine.kt. It was validated by a single run on
a CPU server. It constrains MORPHOLOGY tightly but says nothing about WHICH of the
three candidate word orders to produce, so it underdetermines the style that was
later decided (Classic). It also permits reordering inside a subordinate clause.

v2 keeps v1's morphology wording untouched and adds the two things v1 left open:
the Classic shape, pinned by the approved example itself, and a ban on reordering
inside a subordinate clause.
"""

V1_NO = (
    "Du er en norsk grammatiker. Skriv om setningen slik Yoda snakker: "
    "FLYTT ledd (objekt/verbal foran), men IKKE endre ordformer, tempus "
    "eller modalverb. Behold alle ord fra originalen. Svar kun med setningen.\n"
    "Original: {s}"
)
V1_EN = (
    "You are an English grammarian. Rewrite the sentence the way Yoda speaks: "
    "MOVE constituents (object or verb phrase to the front), but do NOT change "
    "word forms, tense or modal verbs. Keep every word from the original. "
    "Reply with the sentence only.\n"
    "Original: {s}"
)

V2_NO = (
    "Du er en norsk grammatiker. Skriv om setningen slik Yoda snakker.\n"
    "REGLER:\n"
    "1. FLYTT ledd. IKKE endre ordformer, tempus eller modalverb. "
    "Behold alle ord fra originalen, ingen nye ord.\n"
    "2. Rekkefølge: sett det infinitte verbalet med objektet FØRST, "
    "deretter subjektet, deretter det finitte verbet.\n"
    "3. En leddsetning (hvis, når, fordi, at, som, om) flyttes som EN HEL blokk "
    "til slutt. Ordene INNE i leddsetningen står i original rekkefølge.\n"
    "Svar kun med setningen.\n\n"
    "Original: Du vil lære tålmodighet hvis du trener hardt hver dag.\n"
    "Yoda: Lære tålmodighet du vil, hvis du trener hardt hver dag.\n\n"
    "Original: Han har lært mye av deg.\n"
    "Yoda: Lært mye av deg, han har.\n\n"
    "Original: {s}\n"
    "Yoda:"
)
V2_EN = (
    "You are an English grammarian. Rewrite the sentence the way Yoda speaks.\n"
    "RULES:\n"
    "1. MOVE constituents. Do NOT change word forms, tense or modal verbs. "
    "Keep every word from the original, add no new words.\n"
    "2. Order: put the non-finite verb and its object FIRST, then the subject, "
    "then the finite verb.\n"
    "3. A subordinate clause (if, when, because, that, which) moves as ONE WHOLE "
    "block to the end. The words INSIDE it stay in their original order.\n"
    "Reply with the sentence only.\n\n"
    "Original: You will learn patience if you train hard every day.\n"
    "Yoda: Learn patience you will, if you train hard every day.\n\n"
    "Original: I have learned much from you.\n"
    "Yoda: Learned much from you, I have.\n\n"
    "Original: {s}\n"
    "Yoda:"
)

VERSIONS = {
    "v1": {"no": V1_NO, "en": V1_EN},
    "v2": {"no": V2_NO, "en": V2_EN},
}


def prompt_for(sentence: str, lang: str, version: str = "v2") -> str:
    return VERSIONS[version]["en" if lang == "en" else "no"].format(s=sentence)
