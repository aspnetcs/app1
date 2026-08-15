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

function listFilesRecursive(rootDir) {
  const out = [];
  const queue = [rootDir];
  while (queue.length > 0) {
    const dir = queue.pop();
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const ent of entries) {
      const abs = path.join(dir, ent.name);
      if (ent.isDirectory()) queue.push(abs);
      else out.push(abs);
    }
  }
  return out;
}

function resolveBuiltMpRoot(cwd) {
  const candidates = [
    path.join(cwd, "dist", "build", "mp-weixin"),
    path.join(cwd, "dist", "dev", "mp-weixin"),
    path.join(cwd, "unpackage", "dist", "build", "mp-weixin"),
    path.join(cwd, "unpackage", "dist", "dev", "mp-weixin"),
  ];

  for (const c of candidates) {
    if (exists(path.join(c, "app.json"))) return c;
  }

  for (const c of candidates) {
    if (exists(c)) return c;
  }

  return null;
}

function sanitizeWxssFile(absPath) {
  const before = fs.readFileSync(absPath, "utf8");

  // Mini program WXSS does not benefit from WebKit-only scrollbar pseudo-elements.
  // Strip them to keep mp-weixin output clean and reduce compatibility risk.
  const after = before.replace(/[^{}]*::-webkit-scrollbar[^{}]*\{[^}]*\}/g, "");

  if (after === before) return false;
  fs.writeFileSync(absPath, after, "utf8");
  return true;
}

function main() {
  const cwd = process.cwd();
  const mpRoot = resolveBuiltMpRoot(cwd);
  if (!mpRoot) {
    console.error(`[sanitize:mp-weixin-wxss] FAIL: mp-weixin dist directory not found under ${cwd}`);
    console.error(`[sanitize:mp-weixin-wxss] Hint: run "npm run build:mp-weixin" first.`);
    process.exit(1);
  }

  const wxssFiles = listFilesRecursive(mpRoot).filter((f) => f.endsWith(".wxss"));
  let changed = 0;
  for (const f of wxssFiles) {
    if (sanitizeWxssFile(f)) changed += 1;
  }

  console.log(`[sanitize:mp-weixin-wxss] DONE: mpRoot=${mpRoot} files=${wxssFiles.length} changed=${changed}`);
}

main();

