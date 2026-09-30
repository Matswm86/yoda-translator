# Corpus pipeline

Builds the training pairs for the on-device model: English and Norwegian
sentences in, `{"lang", "input", "output"}` Yoda pairs out.

## The pipeline that trained the shipped model

```bash
# 1. Source pool from the Tatoeba per-language dumps
#    https://downloads.tatoeba.org/exports/per_language/{nob,eng}/{nob,eng}_sentences.tsv.bz2
python3 build_pool.py --tsv no=nob.tsv --tsv en=eng.tsv --per-lang 5000 --out pool.jsonl

# 2. Teacher: Claude Sonnet through the `claude` CLI, batches of 100, resumable
python3 distill_sonnet.py --pool pool.jsonl --work work/ --out pairs.jsonl \
    --rejects rejects.jsonl --prompt sonnet-teacher-prompt.txt

# 3. Fine-tune (see ../train/train_lora.py)
```

`distill_sonnet.py` keeps a pair only if the teacher's line has the same number
of negations and the same modal verbs as the source, and actually differs from
it. Word forms may change; meaning may not. On the 10,000-sentence pool this
kept 9,968 pairs.

## Tools kept from earlier attempts

- `yoda_tokenize.py`: a port of the app's tokenizer and word-form check.
- `clause_check.py`: rejects output that breaks a subordinate clause apart.
- `build_rule_corpus.py`: pairs from the app's rule engine instead of a model
  (run the shipped engine over a pool with `RuleEngineBatchTool`, see its
  header). A reviewer rejected this corpus as too stiff to teach from.
- `distill.py`, `prompts.py`: the first teacher, a local `gemma3:12b` through
  Ollama. It changed word forms in 10–33% of answers and was dropped.
- `review_sample.py`: draws a human review sample from a pairs file.
