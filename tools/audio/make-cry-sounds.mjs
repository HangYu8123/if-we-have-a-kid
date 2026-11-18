// Synthesizes the app's crying sounds (no recorded or licensed audio needed).
//   node tools/audio/make-cry-sounds.mjs
// Writes 16 kHz mono 16-bit WAV files to app/src/main/res/raw/.
// The sounds are stylized: harmonic "waah" units shaped by infant-like formants, with breath gaps.
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const outDir = join(root, 'app', 'src', 'main', 'res', 'raw');
const SR = 16000;

// Deterministic PRNG (mulberry32) so the files are reproducible.
function rng(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// Two-pole resonator (constant peak gain band-pass).
function resonator(freq, bw) {
  const r = Math.exp(-Math.PI * bw / SR);
  const c = 2 * r * Math.cos(2 * Math.PI * freq / SR);
  let y1 = 0, y2 = 0;
  const g = 1 - r;
  return x => {
    const y = g * x + c * y1 - r * r * y2;
    y2 = y1; y1 = y;
    return y;
  };
}

function cryUnit(out, start, dur, opts, rand) {
  const { f0, amp, rough = 0, breath = 0.06, rise = 0.22, formant = 1 } = opts;
  const n = Math.floor(dur * SR);
  const f1 = resonator(1150 * formant, 260), f2 = resonator(2150 * formant, 340), f3 = resonator(3300 * formant, 420);
  const harmonics = Math.floor(4800 / (f0 * 1.3));
  const phases = new Array(harmonics + 1).fill(0);
  let jitter = 0;
  const vibRate = 5.5 + rand() * 2;
  for (let i = 0; i < n; i++) {
    const t = i / n;
    jitter += (rand() - 0.5) * 0.004;
    jitter *= 0.995;
    const contour = 1 + rise * Math.sin(Math.PI * Math.min(1, t * 1.15)) - 0.08 * t;
    const f = f0 * contour * (1 + 0.025 * Math.sin(2 * Math.PI * vibRate * i / SR) + jitter);
    let src = 0;
    for (let k = 1; k <= harmonics; k++) {
      phases[k] += 2 * Math.PI * k * f / SR;
      src += Math.sin(phases[k]) / Math.pow(k, 1.05);
    }
    if (rough > 0) src *= 1 - rough * 0.5 * (1 + Math.sin(phases[1] / 2));
    src += (rand() * 2 - 1) * breath * 3;
    const shaped = 1.0 * f1(src) + 0.7 * f2(src) + 0.35 * f3(src) + 0.08 * src;
    const attack = Math.min(1, i / (0.045 * SR));
    const release = Math.min(1, (n - i) / (0.13 * SR));
    out[start + i] += shaped * amp * attack * release;
  }
}

function inhale(out, start, dur, amp, rand) {
  const n = Math.floor(dur * SR);
  const f = resonator(2600, 900);
  for (let i = 0; i < n; i++) {
    const env = Math.sin(Math.PI * i / n);
    out[start + i] += f((rand() * 2 - 1)) * amp * env;
  }
}

function render(seconds, seed, unit) {
  const rand = rng(seed);
  const out = new Float64Array(Math.floor(seconds * SR));
  let t = 0.12;
  while (true) {
    const u = unit(rand);
    if (t + u.dur + 0.15 > seconds) break;
    cryUnit(out, Math.floor(t * SR), u.dur, u, rand);
    t += u.dur;
    const gap = u.gap;
    if (u.inhale) inhale(out, Math.floor((t + gap * 0.25) * SR), Math.min(0.12, gap * 0.5), u.amp * 0.09, rand);
    t += gap;
  }
  let peak = 0;
  for (const v of out) peak = Math.max(peak, Math.abs(v));
  return { out, peak };
}

function wav(name, { out, peak }, level) {
  const scale = peak > 0 ? (level / peak) : 0;
  const data = Buffer.alloc(out.length * 2);
  for (let i = 0; i < out.length; i++) {
    const v = Math.max(-1, Math.min(1, out[i] * scale));
    data.writeInt16LE(Math.round(v * 32767), i * 2);
  }
  const header = Buffer.alloc(44);
  header.write('RIFF', 0); header.writeUInt32LE(36 + data.length, 4); header.write('WAVE', 8);
  header.write('fmt ', 12); header.writeUInt32LE(16, 16); header.writeUInt16LE(1, 20); header.writeUInt16LE(1, 22);
  header.writeUInt32LE(SR, 24); header.writeUInt32LE(SR * 2, 28); header.writeUInt16LE(2, 32); header.writeUInt16LE(16, 34);
  header.write('data', 36); header.writeUInt32LE(data.length, 40);
  mkdirSync(outDir, { recursive: true });
  writeFileSync(join(outDir, name), Buffer.concat([header, data]));
  console.log(`${name}: ${(out.length / SR).toFixed(1)} s, ${((44 + data.length) / 1024).toFixed(0)} KB`);
}

const between = (rand, a, b) => a + (b - a) * rand();

wav('cry_fuss.wav', render(5.0, 11, r => ({ dur: between(r, 0.18, 0.34), gap: between(r, 0.45, 1.0), f0: between(r, 360, 430), amp: 0.5, rise: 0.12, breath: 0.08, inhale: false })), 0.5);
wav('cry_cry.wav', render(6.0, 23, r => ({ dur: between(r, 0.6, 1.0), gap: between(r, 0.28, 0.45), f0: between(r, 430, 500), amp: 0.8, rise: 0.22, breath: 0.06, inhale: true })), 0.75);
wav('cry_hard.wav', render(6.0, 37, r => ({ dur: between(r, 0.95, 1.5), gap: between(r, 0.2, 0.32), f0: between(r, 520, 620), amp: 1.0, rise: 0.28, rough: 0.6, breath: 0.1, inhale: true, formant: 1.08 })), 0.9);
wav('night_cry.wav', render(2.4, 5, r => ({ dur: between(r, 0.75, 0.9), gap: 0.3, f0: between(r, 450, 500), amp: 0.9, rise: 0.24, breath: 0.06, inhale: true })), 0.8);
