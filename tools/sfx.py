"""Synthesizes every sound effect of the game (no external samples, so no licensing questions).

Usage: python3 tools/sfx.py <out_dir>
Writes 16-bit mono 48 kHz WAVs: hum (loop), stretch, launch, star, checkpoint, delivered, crash,
place. Background music is not synthesized: a sustained pure tone turned out to be the only part
small speakers reproduce, which made the old ambient bed sound shrill.
"""
import math, os, random, struct, sys, wave

RATE = 48000
OUT = sys.argv[1] if len(sys.argv) > 1 else "."
TAU = 2 * math.pi


def write(name, samples, peak=0.85):
    m = max(1e-9, max(abs(s) for s in samples))
    k = peak / m
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s * k)) * 32767)) for s in samples))
    print(name, f"{len(samples) / RATE:.2f}s")


def env(i, n, attack=0.01, release=0.3):
    t = i / RATE
    dur = n / RATE
    a = min(1.0, t / attack) if attack > 0 else 1.0
    r = min(1.0, (dur - t) / release) if release > 0 else 1.0
    return max(0.0, a * r)


def lowpass(xs, alpha):
    # alpha was tuned at 22.05 kHz; rescale so the cutoff stays the same at RATE.
    alpha = 1 - (1 - alpha) ** (22050 / RATE)
    y, out = 0.0, []
    for x in xs:
        y += alpha * (x - y)
        out.append(y)
    return out


def bell(f, t, decay):
    # Inharmonic partials give a glassy chime.
    return math.exp(-t * decay) * (math.sin(TAU * f * t) + 0.45 * math.sin(TAU * f * 2.76 * t) * math.exp(-t * decay * 1.5)
                                   + 0.2 * math.sin(TAU * f * 5.4 * t) * math.exp(-t * decay * 3))


rng = random.Random(42)

# Planet hum: 4 s loop, low and breathy.
L = 4.0
n = int(RATE * L)
hum = [0.6 * math.sin(TAU * 60 * i / RATE) * (0.7 + 0.3 * math.sin(TAU * i / RATE / L))
       + 0.3 * math.sin(TAU * 90 * i / RATE) for i in range(n)]
write("hum", hum, peak=0.6)

# Stretch: rubbery rising creak while pulling.
n = int(RATE * 0.45)
ph = 0.0
st = []
for i in range(n):
    t = i / RATE
    f = 140 + 260 * (t / 0.45) ** 1.5
    ph += TAU * f / RATE
    s = (math.sin(ph) + 0.4 * math.sin(2 * ph + math.sin(TAU * 30 * t))) * env(i, n, 0.02, 0.12)
    st.append(s)
write("stretch", st, peak=0.5)

# Launch: noise whoosh with a falling pitch tail.
n = int(RATE * 0.9)
noise = lowpass([rng.uniform(-1, 1) for _ in range(n)], 0.18)
ph = 0.0
la = []
for i in range(n):
    t = i / RATE
    f = 420 * math.exp(-t * 3) + 70
    ph += TAU * f / RATE
    e = math.exp(-t * 4.5) * min(1, t / 0.01)
    la.append(e * (noise[i] * 1.6 + 0.5 * math.sin(ph)))
write("launch", la)

# Star: bright two-note chime (G6 -> D7).
n = int(RATE * 0.9)
sr = []
for i in range(n):
    t = i / RATE
    s = bell(1568, t, 6)
    if t > 0.08:
        s += bell(2349, t - 0.08, 5)
    sr.append(s * min(1, t / 0.003))
write("star", sr, peak=0.6)

# Checkpoint: soft rising ping (C6 -> G6).
n = int(RATE * 0.8)
cp = []
for i in range(n):
    t = i / RATE
    s = bell(1047, t, 4) * 0.8
    if t > 0.12:
        s += bell(1568, t - 0.12, 4)
    cp.append(s * min(1, t / 0.003))
write("checkpoint", cp, peak=0.6)

# Delivered: major arpeggio fanfare with a pad underneath.
n = int(RATE * 2.2)
notes = [523.25, 659.25, 783.99, 1046.5]
de = []
for i in range(n):
    t = i / RATE
    s = 0.0
    for k, f in enumerate(notes):
        t0 = k * 0.11
        if t > t0:
            s += bell(f, t - t0, 2.2) * 0.8
    pad = sum(math.sin(TAU * f / 2 * t) for f in notes[:3]) * 0.12 * env(i, n, 0.25, 0.9)
    de.append(s + pad)
write("delivered", de, peak=0.75)

# Crash: low thump plus crunchy noise.
n = int(RATE * 0.8)
noise = lowpass([rng.uniform(-1, 1) for _ in range(n)], 0.35)
cr = []
ph = 0.0
for i in range(n):
    t = i / RATE
    f = 90 * math.exp(-t * 6) + 40
    ph += TAU * f / RATE
    cr.append(math.exp(-t * 7) * (1.2 * math.sin(ph) + noise[i] * math.exp(-t * 4)))
write("crash", cr)

# Place: short soft click-thunk when a gravity ball is set down.
n = int(RATE * 0.25)
pl = [math.exp(-i / RATE * 25) * (math.sin(TAU * 220 * i / RATE) + 0.5 * math.sin(TAU * 440 * i / RATE)) for i in range(n)]
write("place", pl, peak=0.5)
