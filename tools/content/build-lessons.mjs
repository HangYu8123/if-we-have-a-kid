// Builds the app's lesson content from the curriculum spec (docs/09-education-curriculum.md).
// The docs stay the single source: re-run after editing doc 09.
//   node tools/content/build-lessons.mjs
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const doc = readFileSync(join(root, 'docs', '09-education-curriculum.md'), 'utf8');
const out = join(root, 'app', 'src', 'main', 'assets', 'content', 'lessons.json');

const statusLine = doc.match(/^> Status: (.+)$/m)?.[1] ?? 'Draft';

// Curriculum map: | 0 | Before Baby | Day 0 | L-PREP-01…04 |
const modules = [];
const mapSection = doc.split('## 2. Curriculum map')[1].split('## 3.')[0];
for (const line of mapSection.split('\n')) {
  const m = line.match(/^\| (\d+) \| ([^|]+) \| ([^|]+) \| (L-[A-Z]+)-\d+/);
  if (m) modules.push({ number: +m[1], title: m[2].trim(), unlocks: m[3].trim(), prefix: m[4] });
}

// Lessons: - **L-FEED-01 Hunger and fullness cues.** Body text [Source](url), [Source 2](url)
const lessons = [];
const lessonRe = /^- \*\*(L-[A-Z]+-\d+) ([^*]+?)\.?\*\* (.+)$/gm;
for (const m of doc.matchAll(lessonRe)) {
  const [, id, title, rest] = m;
  const sources = [...rest.matchAll(/\[([^\]]+)\]\((https?:[^)]+)\)/g)].map(s => ({ title: s[1], url: s[2] }));
  const body = rest.replace(/,?\s*\[[^\]]+\]\(https?:[^)]+\)/g, '').trim();
  const prefix = id.replace(/-\d+$/, '');
  const module = modules.find(mod => mod.prefix === prefix);
  lessons.push({ id, title: title.trim(), body, sources, module: module?.number ?? -1 });
}

// First-trial sequence table: | Before the trial | L-PREP-01 plan ... | Pick which ... |
const firstTrial = [];
const seq = doc.split('### First-trial lesson sequence')[1].split('## 2.')[0];
for (const line of seq.split('\n')) {
  const m = line.match(/^\| ([^|]+) \| ([^|]+) \| ([^|]+) \|$/);
  if (!m || m[1].includes('Moment') || m[1].startsWith('---')) continue;
  const ids = [...m[2].matchAll(/L-[A-Z]+-\d+/g)].map(x => x[0]);
  firstTrial.push({ moment: m[1].trim(), skill: m[2].trim(), feedback: m[3].trim(), lessons: ids });
}

// "Talk about it" prompts.
const talk = (doc.split('## 6. "Talk about it" prompts')[1] ?? '')
  .split('\n').filter(l => l.startsWith('- ')).map(l => l.slice(2).trim());

if (lessons.length < 90) throw new Error(`Only parsed ${lessons.length} lessons; check the doc 09 format.`);
mkdirSync(dirname(out), { recursive: true });
writeFileSync(out, JSON.stringify({
  source: 'docs/09-education-curriculum.md',
  status: statusLine,
  review: 'Draft content. Not yet reviewed by a pediatric professional (docs/09 §5). Not medical advice.',
  modules: modules.map(({ prefix, ...m }) => m),
  lessons,
  firstTrial,
  talkAboutIt: talk,
}, null, 2) + '\n');
console.log(`Wrote ${lessons.length} lessons, ${modules.length} modules, ${firstTrial.length} first-trial moments, ${talk.length} prompts to ${out}`);
