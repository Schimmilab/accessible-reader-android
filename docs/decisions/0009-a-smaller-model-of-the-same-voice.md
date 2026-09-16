# Decision 0009: The natural voice stays, its model gets smaller

Date: 16 September 2026

Status: accepted for the recommendation; the listener decides by ear

## Context

The listener's verdict on the two obvious paths, in her own words:

> *"Die Google Online-Stimmen sind nicht gerade prickelnd, das sind noch ältere Stimmen, die noch schlechter sind als die, die auf dem Handy installiert sind."*

> *"Die Thorsten-Stimme klingt sehr natürlich, aber funktioniert halt nicht so richtig."*

Both statements are precise and they point in the same direction. **The problem with the voice she likes is not its quality. It is its speed.** Thorsten through VoxSherpa needs about as long to produce audio as the audio lasts, so a book turns into a waiting game and long sections run into the synthesis budget.

The suggestion that followed — train a natural voice with ElevenLabs or something like it — treats this as a quality problem and would cost money, an internet permission and a cloud account. It is worth checking whether the speed problem can be solved instead.

## What was measured

Piper, the model behind the Thorsten voice, publishes every voice in several sizes: `low`, `medium`, `high`. She has `high`. The sherpa-onnx project ships each one as a ready-made Android speech engine with the model inside.

Same emulator, same text, same app, measured through `SynthesisSpeedTest`. The factor is the time the engine needs divided by the length of the audio it produces, so 1.0 means it is exactly as slow as listening and 0.1 means ten times faster:

| Voice | Model | Factor at 600 characters |
| --- | --- | --- |
| Thorsten, VoxSherpa | **high** | **0.52 – 0.62** |
| Thorsten, sherpa-onnx engine | **medium** | **0.07** |
| Thorsten, sherpa-onnx engine | **low** | **0.06** |
| Google, offline | — | 0.03 |

**The same voice, in the medium model, is about eight times faster than the one she has** — and lands in the same class as the stock Google voice, which she already describes as running fluently.

`medium` and `low` differ in sample rate, 22.05 kHz against 16 kHz, and in model size. Between them the speed is the same, so there is no reason to go below `medium`.

## Decision

**Recommend the same voice in the medium model**, as a second speech engine installed beside the ones she has. Nothing about the reader changes: it already offers every installed engine, and `AndroidSpeechProvider` was built for exactly this.

What this keeps that a cloud voice would not: the phone stays offline, no account, no key, no bill, no consent dialog, and her books stay on the device.

## What this does not settle

**Whether she likes how it sounds.** Speed was measured, quality was not, and quality is hers to judge. The medium model is a smaller network than the high one; most Piper users run medium and it is the level the project itself recommends where speed matters. If she hears the difference and dislikes it, the cloud question from decision 0008 comes back — with its price tag of about thirty dollars a book beyond the free million characters.

## The concrete step for her phone

A Samsung Galaxy S25 is `arm64-v8a`:

```
https://huggingface.co/csukuangfj2/sherpa-onnx-apk/resolve/main/tts-engine-new/1.13.8/sherpa-onnx-1.13.8-arm64-v8a-deu-tts-engine-vits-piper-de_DE-thorsten-medium.apk
```

The model is inside the APK, so it works offline from the first sentence and needs no setup. The alternative is *SherpaTTS* from F-Droid, which downloads a model of your choice on first start; it offers more voices but needs sighted setup once.

Afterwards the engine appears in *Stimme und Einstellungen* under the speech engines, and the reader uses it like any other.

## Other German voices in the same family

`eva_k`, `karlsson`, `kerstin`, `pavoque`, `ramona`, `thorsten_emotional` — all Piper, all available as the same kind of engine APK, all offline. If Thorsten is not the voice she wants to spend a hundred hours with, there are six more to try at no cost but the listening.
