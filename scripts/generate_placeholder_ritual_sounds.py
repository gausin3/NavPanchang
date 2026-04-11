#!/usr/bin/env python3
"""
Procedural placeholder generator for NavPanchang's ritual notification sounds.

Writes four short mono 16-bit WAV files into `app/src/main/res/raw/`:

    ritual_temple_bell.wav   — decaying sine with bell harmonics, ~2 s
    ritual_sankh.wav         — low-frequency conch-like drone with vibrato, ~2 s
    ritual_bell_toll.wav     — three bell strikes in succession, ~2 s
    ritual_om_mantra.wav     — sustained low drone with harmonics (Om-ish), ~2 s

**These are placeholders**, not authentic field recordings. Their role is to give
each of the four ritual notification channels a *distinct* audible identity for
testing — so you can verify that the bell / sankh / toll / Om variants land on
the correct channel without needing to squint at the channel ID.

Before the first public Play Store release, replace each file with an
authentic CC0 (or appropriately licensed) field recording from Freesound.org,
Zapsplat, or your own recording. The `NotificationChannels.ensureChannels()`
setSound() calls pick these up by `R.raw.*` id — no code change needed if you
keep the filenames.

Run from the project root:
    python3 scripts/generate_placeholder_ritual_sounds.py

Requires only the Python 3 stdlib (`wave`, `math`, `struct`, `os`).
"""

from __future__ import annotations

import math
import os
import struct
import wave


SAMPLE_RATE = 22_050  # Hz — 22.05 kHz is plenty for notification sounds
DURATION_SEC = 2.0    # Length of each clip. 2 s is the sweet spot for notifications.
PEAK_AMPLITUDE = 20_000  # Out of the 32767 16-bit PCM max — leaves headroom.


def _write_wav(path: str, samples: list[float]) -> None:
    """Serialize a list of float samples in [-1, 1] to a 16-bit PCM mono WAV."""
    with wave.open(path, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)      # 16-bit
        w.setframerate(SAMPLE_RATE)
        frames = b"".join(
            struct.pack("<h", max(-32767, min(32767, int(s * PEAK_AMPLITUDE))))
            for s in samples
        )
        w.writeframes(frames)


def _bell(fundamental_hz: float, decay_rate: float = 3.0) -> list[float]:
    """
    Decaying sine with a couple of inharmonic overtones — approximates a struck
    metal bell. Real bells have a sharp attack and non-integer harmonics; we
    model the two most audible ones at ~2.76x and ~5.4x the fundamental.
    """
    n = int(SAMPLE_RATE * DURATION_SEC)
    out: list[float] = [0.0] * n
    for i in range(n):
        t = i / SAMPLE_RATE
        envelope = math.exp(-decay_rate * t)
        val = (
            math.sin(2 * math.pi * fundamental_hz * t) * 0.60
            + math.sin(2 * math.pi * fundamental_hz * 2.76 * t) * 0.30
            + math.sin(2 * math.pi * fundamental_hz * 5.40 * t) * 0.10
        )
        out[i] = val * envelope
    return out


def _sankh(fundamental_hz: float = 220.0) -> list[float]:
    """
    A low horn-like tone with slow attack, slight vibrato, and a soft release.
    The conch shell (shankh) actually has a rich harmonic spectrum; this is a
    caricature, but its distinguishing feature — a slowly breathing low tone —
    survives.
    """
    n = int(SAMPLE_RATE * DURATION_SEC)
    out: list[float] = [0.0] * n
    attack_end = 0.20  # seconds
    release_start = DURATION_SEC - 0.40
    for i in range(n):
        t = i / SAMPLE_RATE
        if t < attack_end:
            env = t / attack_end
        elif t > release_start:
            env = max(0.0, 1.0 - (t - release_start) / 0.40)
        else:
            env = 1.0
        vibrato_depth = 0.03
        vibrato_hz = 5.0
        inst_freq = fundamental_hz * (1 + vibrato_depth * math.sin(2 * math.pi * vibrato_hz * t))
        val = (
            math.sin(2 * math.pi * inst_freq * t) * 0.55
            + math.sin(2 * math.pi * inst_freq * 2 * t) * 0.25
            + math.sin(2 * math.pi * inst_freq * 3 * t) * 0.10
        )
        out[i] = val * env
    return out


def _bell_toll() -> list[float]:
    """
    Three bell strikes at 0, 0.6, 1.2 seconds, each decaying within ~0.6 s.
    Rhythmic 'bong ... bong ... bong' evoking a temple bell rope.
    """
    n = int(SAMPLE_RATE * DURATION_SEC)
    out: list[float] = [0.0] * n
    strike_offsets_sec = (0.00, 0.60, 1.20)
    strike_freq = 660.0
    strike_decay = 4.0
    for strike_t in strike_offsets_sec:
        strike_start = int(strike_t * SAMPLE_RATE)
        for k in range(int(SAMPLE_RATE * 0.70)):
            idx = strike_start + k
            if idx >= n:
                break
            t = k / SAMPLE_RATE
            envelope = math.exp(-strike_decay * t)
            val = (
                math.sin(2 * math.pi * strike_freq * t) * 0.55
                + math.sin(2 * math.pi * strike_freq * 2.76 * t) * 0.25
            )
            out[idx] = min(1.0, max(-1.0, out[idx] + val * envelope))
    return out


def _om_drone() -> list[float]:
    """
    Sustained low-frequency drone with stacked harmonics. Not an Om chant —
    no vocal formants — but it occupies the right frequency band (130 Hz is
    a typical male chest-voice fundamental) and holds for the full duration
    so it reads as "droning" rather than "ringing".
    """
    n = int(SAMPLE_RATE * DURATION_SEC)
    out: list[float] = [0.0] * n
    base_hz = 130.81  # ~C3
    attack_end = 0.30
    release_start = DURATION_SEC - 0.50
    for i in range(n):
        t = i / SAMPLE_RATE
        if t < attack_end:
            env = t / attack_end
        elif t > release_start:
            env = max(0.0, 1.0 - (t - release_start) / 0.50)
        else:
            env = 1.0
        val = (
            math.sin(2 * math.pi * base_hz * t) * 0.50
            + math.sin(2 * math.pi * base_hz * 2 * t) * 0.28
            + math.sin(2 * math.pi * base_hz * 3 * t) * 0.14
            + math.sin(2 * math.pi * base_hz * 4 * t) * 0.08
        )
        out[i] = val * env
    return out


def main() -> None:
    here = os.path.dirname(os.path.abspath(__file__))
    raw_dir = os.path.normpath(os.path.join(here, "..", "app", "src", "main", "res", "raw"))
    os.makedirs(raw_dir, exist_ok=True)

    generators = {
        "ritual_temple_bell.wav": lambda: _bell(880.0, decay_rate=3.2),
        "ritual_sankh.wav": lambda: _sankh(220.0),
        "ritual_bell_toll.wav": _bell_toll,
        "ritual_om_mantra.wav": _om_drone,
    }

    for name, gen in generators.items():
        out_path = os.path.join(raw_dir, name)
        _write_wav(out_path, gen())
        size_kb = os.path.getsize(out_path) / 1024
        print(f"wrote {out_path}  ({size_kb:.1f} KB)")


if __name__ == "__main__":
    main()
