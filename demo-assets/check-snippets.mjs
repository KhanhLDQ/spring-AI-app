// Checks that every code pop-up in architecture.html still matches the repo, line for line.
// Run from anywhere: node demo-assets/check-snippets.mjs   (exit 1 when a snippet drifted)
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, "..");
const html = readFileSync(join(here, "architecture.html"), "utf8");
const block = html.match(/<script type="application\/json" id="snippets">([\s\S]*?)<\/script>/);
if (!block) {
  console.error("No snippets block in architecture.html");
  process.exit(1);
}

const snippets = JSON.parse(block[1]);
let failed = 0;
let condensed = 0;
for (const [id, s] of Object.entries(snippets)) {
  // Condensed snippets (only "ref") are written for the talk, not copied, so there is nothing to compare.
  if (!s.file) {
    console.log(`- ${id}: ${s.ref} (condensed, not checked)`);
    condensed++;
    continue;
  }
  let source;
  try {
    source = readFileSync(join(root, s.file), "utf8").split(/\r?\n/);
  } catch {
    console.error(`✗ ${id}: ${s.file} not found`);
    failed++;
    continue;
  }
  if (s.lines.length !== s.to - s.from + 1) {
    console.error(`✗ ${id}: ${s.file}:${s.from}-${s.to} spans ${s.to - s.from + 1} lines but ${s.lines.length} are stored`);
    failed++;
    continue;
  }
  const bad = s.lines.findIndex((line, k) => source[s.from - 1 + k] !== line);
  if (bad >= 0) {
    const n = s.from + bad;
    console.error(`✗ ${id}: ${s.file}:${n}\n    shown: ${s.lines[bad]}\n    repo:  ${source[n - 1] ?? "(past end of file)"}`);
    failed++;
  } else {
    console.log(`✓ ${id}: ${s.file}:${s.from}-${s.to}`);
  }
}
if (failed) {
  console.error(`\n${failed} snippet(s) out of date`);
  process.exit(1);
}
console.log(`\nAll ${Object.keys(snippets).length - condensed} copied snippets match the code`);
