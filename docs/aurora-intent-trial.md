# Typed text trial (M0)

For [aurora-miller-pattern.md](aurora-miller-pattern.md), 2026-11-04. Outside game code: nothing here touches `build.gradle` or `src/`.

**Recommendation: `potion-base-8M` at int8, with one mark missed.** No option cleared every mark. This one adds 6.9 MB, runs in pure Java in under 0.1 ms, and misses the out-of-scope mark by one line in 33. MiniLM gives more confident answers but adds 57.9 MB of native libraries, about twice the limit, and has no Intel Mac build. Word matching cannot be trusted to answer outright.

## Pass marks

Fixed before anything ran, on the held-out half:

| Mark | Limit |
| --- | --- |
| Top-1 | At least 80 percent |
| Right intent in the top two | At least 90 percent |
| Strong and wrong: she answers the wrong topic | At most 5 percent |
| Out-of-scope reaching Strong | At most 5 percent |
| p99 per line, one thread | Under 25 ms, half a tick |
| Added jar | At most 30 MB (the last built jar was 45.5 MB) |

## Results

Held-out half: 132 in-scope lines and 33 out-of-scope. Bold misses a mark.

| Option | Top-1 | Top two | Strong wrong | Out-of-scope Strong | p99 | Added jar |
| --- | --- | --- | --- | --- | --- | --- |
| `potion-base-8M` float32 | 86.4% | 92.4% | 0.8% | **6.1%** | 0.06 ms | 28.3 MB |
| `potion-base-8M` float16 | 86.4% | 92.4% | 0.8% | **6.1%** | 0.05 ms | 14.2 MB |
| `potion-base-8M` int8 | 86.4% | 92.4% | 0.8% | **6.1%** | 0.04 ms | 6.9 MB |
| MiniLM float32, ONNX | 87.9% | 93.2% | 4.5% | 0.0% | 2.8 ms | **123.3 MB** |
| MiniLM int8 (arm64 file), ONNX | 87.9% | 93.2% | 2.3% | 0.0% | 2.7 ms | **57.9 MB** |
| Word and synonym | 82.6% | **89.4%** | 0.0% | 0.0% | 0.3 ms | Under 0.1 MB |

What a player gets, in-scope:

| Option | Strong, right | Close, answer in the slots | Close, not in the slots | None |
| --- | --- | --- | --- | --- |
| `potion-base-8M` int8 | 62.9% | 24.2% | 5.3% | 6.8% |
| MiniLM int8 | 73.5% | 18.9% | 4.5% | 0.8% |
| Word and synonym | 0.0% | 66.7% | 3.0% | 30.3% |

Top-1 by style, of 22 (terse 44):

| Option | Casual | Terse | Typo | Long | Indirect |
| --- | --- | --- | --- | --- | --- |
| `potion-base-8M` int8 | 22 | 42 | 18 | 17 | 15 |
| MiniLM int8 | 21 | 41 | 20 | 19 | 15 |
| Word and synonym | 19 | 43 | 19 | 16 | 12 |

Thresholds, from the dev half only: potion Strong 0.56 with a 0.10 lead over the second intent, Close 0.425. MiniLM int8 Strong 0.48, lead 0.13, Close 0.32.

Load, excluding the read from disk: potion 40 ms or less. MiniLM 0.2 to 1.2 s. The potion heap of about 31 MB is the table held as floats; kept as bytes it should be about 8 MB, unmeasured.

## What this means

- **The three potion widths score alike.** Cosines move in the third decimal and no line changes band. Width is free, so int8.
- **The miss is two lines:** "how are you" went to "How do you know all this?" at 0.64, and "ok" to Enoch at 0.59, by way of "okoye". Both are short small talk. The dev half had none, and the 95 percent interval on 2 of 33 runs from about 2 to 20 percent, so the mark is not settled either way. I did not retune against this half. A small-talk intent in M4 is the obvious fix, and it needs a fresh test set to prove.
- **Near neighbours mostly fall to Close,** as the spec wants: "wat hapened to kevin" and "the technician" put the right topic second, so it becomes a button.
- **Indirect phrasing is the weak spot for every option**, 15 of 22 at best. The spec's fallback covers it: every topic stays on a button, and typing only finds small surprises.
- **Locked names held.** No Sophie, TITHONUS, Anderson-Kind, Maeve or "continuity" line reached Strong on any embedding option, though one in eight reached Close on potion and three or four on MiniLM. M4's check before matching still has to catch them, because Close shows two guesses in the slots: it must never offer a locked topic.
- **Word matching has no safe Strong line.** One-word lines score a perfect 1.0, right or wrong, so tuning found no threshold. It would check everything with "You mean...?". It stays the fallback the spec calls it.
- **The spec's "moves to ONNX" does not apply cleanly.** ONNX fixed the out-of-scope mark and failed the jar mark at twice the limit, and it adds 10.6 points of Strong answers. That is the real trade, and it is yours if you value it over the size.

## Licences

Checked at each official source on 2026-11-04. The mod is `LGPL-3.0-or-later`; MIT and Apache-2.0 both sit inside it with their notices kept.

| Item | Licence | Source |
| --- | --- | --- |
| `potion-base-8M` weights | MIT | `minishlab/potion-base-8M` card |
| Its teacher, `bge-base-en-v1.5` | MIT | `BAAI/bge-base-en-v1.5` card |
| Its training set, tokenlearn on C4 | No licence on the card; C4 itself is ODC-BY | `minishlab/tokenlearn-c4-en-bge-base-en-v1.5`, `allenai/c4` |
| model2vec | MIT, needed only if we ship its code; the trial does not | `MinishLab/model2vec` `LICENSE` |
| `all-MiniLM-L6-v2` | Apache-2.0 | `sentence-transformers` card |
| ONNX Runtime | MIT, with 344 KB of third-party notices in the jar | `microsoft/onnxruntime` `LICENSE`, `onnxruntime-1.31.1.jar` |

One thing for you: the weights are MIT, but the card for the data they were trained on states no licence. The spec's listing came from a mirror; the official card agrees on MIT. I am not a lawyer, and this is the one line to look at before shipping.

ONNX Runtime 1.31.1's Maven jar carries Linux x64 and arm64, macOS arm64 and Windows x64. It has no Intel Mac library.

## Limits

- One author wrote the examples and the test set, which flatters every option. A blind set from the owner would be the honest column; none was available.
- Word matching's synonym table was written with the test set in view, so it is the most flattered.
- 33 out-of-scope lines is few. One line moves that figure by 3 points.
- All 22 topics were open. Locks are M4's conditions and are not tested here.
- One machine: an arm64 Mac, Java 21. The MiniLM int8 file is tuned for arm64; other platforms need another file.
- ONNX Runtime was not loaded under NeoForge's classloader, since `build.gradle` stays untouched. Native loading there is unproven.

## Method

`tools/intent_trial/IntentTrial.java` runs everything in Java, on one thread. `intents.json` holds 22 topics at 10 examples each, 220 in all. `phrasings.json` holds 264 in-scope lines, 12 per topic in five styles, and 66 that must miss. None is copied from an example. The score is the best cosine to any of an intent's examples.

Halves alternate inside each topic rather than by hash, so both hold every topic and style. Besides the plan's two thresholds, Strong also needs a lead over the second intent, because the near neighbours are the risk; tuning took the most Strong answers with wrong and out-of-scope Strong each within 3 percent, then a Close line letting at most a quarter of out-of-scope through. Jar sizes are the real bytes compressed at the highest level; ONNX counts its jar without debug symbols, 40.3 MB against 50.9 MB as shipped.

To repeat it, fetch the models into `build/intent-trial/{potion,minilm,ort}` and run from the repository root:

```bash
java -cp <gson.jar>:build/intent-trial/ort/onnxruntime-1.31.1.jar tools/intent_trial/IntentTrial.java build/intent-trial
```
