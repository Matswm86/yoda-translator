#!/usr/bin/env python3
"""LoRA fine-tune of Gemma 3 270M on the Sonnet-distilled Yoda pairs (plan step 6).

Loss is taken on the answer tokens only. A held-out slice is decoded greedily
after training and written next to the model, so the result is judged on
sentences the model never saw. The LoRA is merged into the base weights at the
end, which is the form step 7 converts for the phone.

  pip install torch transformers peft          # CUDA build of torch
  python train_lora.py --pairs pairs.jsonl --out models/yoda-1b \
      --base unsloth/gemma-3-1b-it --batch 2 --accum 8

Convert the merged model for the phone (litert-torch-nightly):

  litert-torch export_hf models/yoda-1b/merged models/yoda-1b/litertlm \
      --quantization_recipe=dynamic_wi8_emb4_afp32 --externalize_embedder=True \
      --bundle_litert_lm=True --cache_length=512
"""

import argparse
import json
import math
import random
import time
from pathlib import Path

import torch
from peft import LoraConfig, get_peft_model
from transformers import AutoModelForCausalLM, AutoTokenizer

DEFAULT_BASE = "unsloth/gemma-3-270m-it"  # ungated mirror of google/gemma-3-270m-it

# The app sends exactly this instruction; keep the two in step.
INSTRUCTION = "Rewrite as Yoda speaks. Keep the language and the meaning."


def prompt_text(tok, sentence: str) -> str:
    msgs = [{"role": "user", "content": f"{INSTRUCTION}\n{sentence}"}]
    return tok.apply_chat_template(msgs, tokenize=False, add_generation_prompt=True)


def encode(tok, pair: dict, max_len: int) -> dict:
    p = tok(prompt_text(tok, pair["input"]), add_special_tokens=False)["input_ids"]
    a = tok(pair["output"] + "<end_of_turn>\n", add_special_tokens=False)["input_ids"]
    ids = (p + a)[:max_len]
    labels = ([-100] * len(p) + a)[:max_len]
    return {"input_ids": ids, "labels": labels}


def collate(batch: list[dict], pad_id: int) -> dict:
    n = max(len(b["input_ids"]) for b in batch)
    ids = torch.full((len(batch), n), pad_id)
    labels = torch.full((len(batch), n), -100)
    mask = torch.zeros((len(batch), n), dtype=torch.long)
    for i, b in enumerate(batch):
        k = len(b["input_ids"])
        ids[i, :k] = torch.tensor(b["input_ids"])
        labels[i, :k] = torch.tensor(b["labels"])
        mask[i, :k] = 1
    return {"input_ids": ids, "labels": labels, "attention_mask": mask}


@torch.no_grad()
def evaluate(model, tok, pairs: list[dict], device) -> tuple[float, list[dict]]:
    model.eval()
    rows, exact = [], 0
    for p in pairs:
        enc = tok(prompt_text(tok, p["input"]), return_tensors="pt", add_special_tokens=False).to(device)
        out = model.generate(**enc, max_new_tokens=64, do_sample=False)
        got = tok.decode(out[0, enc["input_ids"].shape[1]:], skip_special_tokens=True).strip()
        exact += got == p["output"]
        rows.append({"lang": p["lang"], "input": p["input"], "teacher": p["output"], "student": got})
    model.train()
    return exact / max(len(pairs), 1), rows


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--pairs", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--epochs", type=int, default=3)
    ap.add_argument("--batch", type=int, default=4, help="micro-batch; the 262k-word vocabulary makes logits large")
    ap.add_argument("--accum", type=int, default=4, help="micro-batches per optimiser step")
    ap.add_argument("--lr", type=float, default=2e-4)
    ap.add_argument("--rank", type=int, default=16)
    ap.add_argument("--max-len", type=int, default=160)
    ap.add_argument("--holdout", type=int, default=200)
    ap.add_argument("--seed", type=int, default=20260930)
    ap.add_argument("--base", default=DEFAULT_BASE, help="e.g. unsloth/gemma-3-1b-it")
    args = ap.parse_args()

    random.seed(args.seed)
    torch.manual_seed(args.seed)
    device = torch.device("cuda")
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    pairs = [json.loads(l) for l in open(args.pairs, encoding="utf-8") if l.strip()]
    random.shuffle(pairs)
    held, train = pairs[: args.holdout], pairs[args.holdout :]
    (out / "holdout.jsonl").write_text(
        "".join(json.dumps(p, ensure_ascii=False) + "\n" for p in held), encoding="utf-8"
    )

    tok = AutoTokenizer.from_pretrained(args.base)
    model = AutoModelForCausalLM.from_pretrained(args.base, dtype=torch.bfloat16, attn_implementation="eager")
    model = get_peft_model(model, LoraConfig(
        r=args.rank, lora_alpha=2 * args.rank, lora_dropout=0.05, task_type="CAUSAL_LM",
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
    ))
    model.to(device)
    model.print_trainable_parameters()

    data = [encode(tok, p, args.max_len) for p in train]
    steps_per_epoch = math.ceil(len(data) / (args.batch * args.accum))
    total = steps_per_epoch * args.epochs
    opt = torch.optim.AdamW([p for p in model.parameters() if p.requires_grad], lr=args.lr, weight_decay=0.0)
    sched = torch.optim.lr_scheduler.LambdaLR(
        opt, lambda s: min(1.0, (s + 1) / 50) * max(0.0, 1 - s / total)
    )

    base_acc, _ = evaluate(model, tok, held[:50], device)
    print(f"before training: exact match on 50 held-out = {base_acc:.1%}", flush=True)

    step, micro, t0 = 0, 0, time.time()
    model.train()
    for epoch in range(args.epochs):
        random.shuffle(data)
        for i in range(0, len(data), args.batch):
            b = collate(data[i : i + args.batch], tok.pad_token_id)
            b = {k: v.to(device) for k, v in b.items()}
            loss = model(**b).loss
            (loss / args.accum).backward()
            micro += 1
            if micro % args.accum:
                continue
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            opt.step()
            sched.step()
            opt.zero_grad()
            step += 1
            if step % 50 == 0:
                print(f"epoch {epoch + 1} step {step}/{total} loss {loss.item():.3f} "
                      f"{time.time() - t0:.0f}s", flush=True)

    acc, rows = evaluate(model, tok, held, device)
    print(f"after training: exact match on {len(held)} held-out = {acc:.1%}", flush=True)
    (out / "holdout-predictions.jsonl").write_text(
        "".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows), encoding="utf-8"
    )

    merged = model.merge_and_unload()
    merged.save_pretrained(out / "merged")
    tok.save_pretrained(out / "merged")
    (out / "train-args.json").write_text(json.dumps(vars(args) | {"base": args.base, "train_pairs": len(train)}, indent=2))
    print(f"saved merged model -> {out / 'merged'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
