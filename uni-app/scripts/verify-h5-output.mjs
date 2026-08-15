import fs from "node:fs";
import path from "node:path";

function exists(p) {
  try {
    fs.accessSync(p, fs.constants.F_OK);
    return true;
  } catch {
    return false;
  }
}

function resolveBuiltH5Root(cwd) {
  const candidates = [
    path.join(cwd, "dist", "build", "h5"),
    path.join(cwd, "dist", "dev", "h5"),
    path.join(cwd, "unpackage", "dist", "build", "h5"),
    path.join(cwd, "unpackage", "dist", "dev", "h5"),
  ];

  for (const c of candidates) {
    if (exists(path.join(c, "index.html"))) return c;
  }
  for (const c of candidates) {
    if (exists(c)) return c;
  }
  return null;
}

function main() {
  const cwd = process.cwd();
  const h5Root = resolveBuiltH5Root(cwd);
  if (!h5Root) {
    console.error(`[verify:h5-output] FAIL: h5 dist directory not found under ${cwd}`);
    console.error(`[verify:h5-output] Hint: run "npm run build:h5" first.`);
    process.exit(1);
  }

  const indexPath = path.join(h5Root, "index.html");
  if (!exists(indexPath)) {
    console.error(`[verify:h5-output] FAIL: missing ${indexPath}`);
    process.exit(1);
  }

  const indexHtml = fs.readFileSync(indexPath, "utf8");
  const assetRe = /\b(?:src|href)\s*=\s*(['"])(\/assets\/[^'"]+)\1/g;
  const assets = new Set();
  let m;
  while ((m = assetRe.exec(indexHtml)) !== null) {
    assets.add(m[2]);
  }

  const missing = [];
  for (const a of assets) {
    const rel = a.replace(/^\//, "");
    const abs = path.join(h5Root, ...rel.split("/"));
    if (!exists(abs)) missing.push(abs);
  }

  if (missing.length > 0) {
    console.error("[verify:h5-output] FAIL: missing assets referenced by index.html:");
    for (const p of missing) console.error(`- ${p}`);
    process.exit(1);
  }

  console.log(`[verify:h5-output] PASS: h5Root=${h5Root} assets=${assets.size}`);
}

main();

