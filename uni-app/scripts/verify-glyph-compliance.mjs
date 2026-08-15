import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptPath = fileURLToPath(import.meta.url);
const projectRoot = path.resolve(path.dirname(scriptPath), '..');
const srcRoot = path.join(projectRoot, 'src');

const TARGET_EXTENSIONS = new Set(['.vue', '.ts', '.js', '.json', '.css', '.scss']);

const EMOJI_REGEX = /[\u2600-\u27BF]|[\uD83C-\uDBFF][\uDC00-\uDFFF]/gu;
const DECORATIVE_REGEX = /[\u00D7\u2190-\u2193\u21BA\u21BB\u25B6\u25C0\u2605\u2630\u2699\u270E\u2715-\u2718\u2726]/gu;

async function collectFiles(dir) {
  const entries = await fs.readdir(dir, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...(await collectFiles(fullPath)));
      continue;
    }
    if (TARGET_EXTENSIONS.has(path.extname(entry.name))) {
      files.push(fullPath);
    }
  }
  return files;
}

function collectMatches(content, regex, type, filePath) {
  const lines = content.split(/\r?\n/);
  const matches = [];
  lines.forEach((line, index) => {
    regex.lastIndex = 0;
    let found = regex.exec(line);
    while (found) {
      matches.push({
        type,
        filePath,
        line: index + 1,
        column: found.index + 1,
        glyph: found[0],
      });
      found = regex.exec(line);
    }
  });
  return matches;
}

function getCodePoint(glyph) {
  const cp = glyph.codePointAt(0);
  if (cp === undefined) return 'UNKNOWN';
  return `U+${cp.toString(16).toUpperCase().padStart(4, '0')}`;
}

async function main() {
  const files = await collectFiles(srcRoot);
  const findings = [];

  for (const file of files) {
    const content = await fs.readFile(file, 'utf8');
    findings.push(...collectMatches(content, EMOJI_REGEX, 'emoji', file));
    findings.push(...collectMatches(content, DECORATIVE_REGEX, 'decorative', file));
  }

  if (findings.length === 0) {
    console.log('[verify:glyph] PASS no forbidden emoji or decorative glyphs in uni-app/src');
    return;
  }

  console.error(`[verify:glyph] FAIL found ${findings.length} forbidden glyph match(es):`);
  findings.forEach((item) => {
    const relativePath = path.relative(projectRoot, item.filePath).replace(/\\/g, '/');
    console.error(`- ${item.type}: ${relativePath}:${item.line}:${item.column} (${getCodePoint(item.glyph)})`);
  });
  process.exitCode = 1;
}

main().catch((error) => {
  console.error('[verify:glyph] ERROR', error);
  process.exitCode = 1;
});
