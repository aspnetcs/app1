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

function readJson(filePath) {
  const raw = fs.readFileSync(filePath, "utf8");
  try {
    return JSON.parse(raw);
  } catch (e) {
    throw new Error(`Failed to parse JSON: ${filePath}\n${e?.message || e}`);
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

  // Fallback: accept the directory if it exists but app.json isn't present (we'll error later).
  for (const c of candidates) {
    if (exists(c)) return c;
  }

  return null;
}

function verifyRequiredRootFiles(mpRoot) {
  const required = ["app.json", "app.js", "app.wxss"];
  const missing = [];
  for (const f of required) {
    const abs = path.join(mpRoot, f);
    if (!exists(abs)) missing.push(abs);
  }
  return missing;
}

function collectAppPages(appJson) {
  const pages = [];
  if (Array.isArray(appJson.pages)) pages.push(...appJson.pages);

  const subPackages = appJson.subPackages || appJson.subpackages;
  if (Array.isArray(subPackages)) {
    for (const sp of subPackages) {
      if (!sp) continue;
      const root = typeof sp.root === "string" ? sp.root : "";
      const spPages = Array.isArray(sp.pages) ? sp.pages : [];
      for (const p of spPages) {
        if (typeof p !== "string") continue;
        pages.push(path.posix.join(root, p));
      }
    }
  }

  // Normalize duplicates
  return Array.from(new Set(pages.filter((p) => typeof p === "string" && p.length > 0)));
}

function verifyPageTriples(mpRoot, pagePath) {
  const missing = [];
  const base = path.join(mpRoot, ...pagePath.split("/"));
  // `.wxss` is only required when the source page defines a <style> block.
  // uni-app can legally omit generating per-page wxss for pages that rely purely
  // on global styles / utility classes, and mini program tooling should tolerate it.
  const requiredExts = [".js", ".wxml"];
  for (const ext of requiredExts) {
    const abs = `${base}${ext}`;
    if (!exists(abs)) missing.push(abs);
  }
  return missing;
}

function findSourcePageFile(cwd, pagePath) {
  const rel = path.join("src", ...pagePath.split("/"));
  const candidates = [`${rel}.vue`, `${rel}.nvue`].map((p) => path.join(cwd, p));
  for (const c of candidates) {
    if (exists(c)) return c;
  }
  return null;
}

function sourceHasStyleTag(sourceFileAbs) {
  if (!sourceFileAbs) return null;
  const raw = fs.readFileSync(sourceFileAbs, "utf8");
  return /<style\b/i.test(raw);
}

function extractStaticRequires(jsSource) {
  const out = [];
  // Common patterns in mp-weixin build output are CommonJS require('...')
  const re = /\brequire\s*\(\s*(['"])([^'"]+)\1\s*\)/g;
  let m;
  while ((m = re.exec(jsSource)) !== null) {
    const reqPath = m[2];
    if (typeof reqPath === "string" && reqPath.length > 0) out.push(reqPath);
  }
  return out;
}

function resolveRequirePath(fromFileAbs, req) {
  if (!req.startsWith("./") && !req.startsWith("../")) return null;
  const fromDir = path.dirname(fromFileAbs);
  const raw = path.resolve(fromDir, req);

  // If the require already includes an extension, check it directly.
  if (path.extname(raw)) return raw;

  // Most build output requires JS modules without extensions.
  // Try common resolutions, matching Node-ish behavior for generated code.
  const candidates = [
    `${raw}.js`,
    `${raw}.wxs`,
    path.join(raw, "index.js"),
  ];

  for (const c of candidates) {
    if (exists(c)) return c;
  }
  return candidates[0]; // Return a representative path for error output.
}

function verifyNoMissingRelativeRequires(mpRoot) {
  const jsFiles = listFilesRecursive(mpRoot).filter((f) => f.endsWith(".js"));

  const missing = [];
  for (const f of jsFiles) {
    const src = fs.readFileSync(f, "utf8");
    const reqs = extractStaticRequires(src);
    for (const req of reqs) {
      const resolved = resolveRequirePath(f, req);
      if (!resolved) continue;
      if (!exists(resolved)) {
        missing.push({
          from: f,
          require: req,
          resolved,
        });
      }
    }
  }

  return missing;
}

function scanWxssForbiddenTokens(mpRoot) {
  const wxssFiles = listFilesRecursive(mpRoot).filter((f) => f.endsWith(".wxss"));
  const forbidden = [
    { name: "dvh", re: /dvh/ },
    { name: "min(", re: /\bmin\(/ },
    { name: "max(", re: /\bmax\(/ },
    { name: "::-webkit-scrollbar", re: /::-webkit-scrollbar/ },
  ];

  const hits = [];
  for (const f of wxssFiles) {
    const text = fs.readFileSync(f, "utf8");
    for (const rule of forbidden) {
      const m = rule.re.exec(text);
      if (!m) continue;
      const idx = typeof m.index === "number" ? m.index : 0;
      const before = text.slice(0, idx);
      const line = before.split("\n").length;
      const lineText = text.split("\n")[line - 1] || "";
      hits.push({ file: f, token: rule.name, line, snippet: lineText.trim() });
    }
  }

  return hits;
}

function main() {
  const cwd = process.cwd();
  const mpRoot = resolveBuiltMpRoot(cwd);
  if (!mpRoot) {
    console.error(`[verify:mp-weixin-output] FAIL: mp-weixin dist directory not found under ${cwd}`);
    console.error(`[verify:mp-weixin-output] Hint: run "npm run build:mp-weixin" first.`);
    process.exit(1);
  }

  const missingRoot = verifyRequiredRootFiles(mpRoot);
  if (missingRoot.length > 0) {
    console.error("[verify:mp-weixin-output] FAIL: missing required root files:");
    for (const m of missingRoot) console.error(`- ${m}`);
    process.exit(1);
  }

  const appJsonPath = path.join(mpRoot, "app.json");
  const appJson = readJson(appJsonPath);
  const pages = collectAppPages(appJson);
  if (pages.length === 0) {
    console.error(`[verify:mp-weixin-output] FAIL: no pages found in ${appJsonPath}`);
    process.exit(1);
  }

  const missingPages = [];
  const missingWxss = [];
  for (const p of pages) {
    missingPages.push(...verifyPageTriples(mpRoot, p));

    const srcFile = findSourcePageFile(cwd, p);
    const hasStyle = sourceHasStyleTag(srcFile);
    if (hasStyle === true) {
      const wxssAbs = path.join(mpRoot, ...p.split("/")) + ".wxss";
      if (!exists(wxssAbs)) missingWxss.push(wxssAbs);
    }
  }
  if (missingPages.length > 0) {
    console.error("[verify:mp-weixin-output] FAIL: missing page files (js/wxml):");
    for (const m of missingPages) console.error(`- ${m}`);
    process.exit(1);
  }
  if (missingWxss.length > 0) {
    console.error("[verify:mp-weixin-output] FAIL: missing page wxss for pages that define <style>:");
    for (const m of missingWxss) console.error(`- ${m}`);
    process.exit(1);
  }

  const missingRequires = verifyNoMissingRelativeRequires(mpRoot);
  if (missingRequires.length > 0) {
    console.error("[verify:mp-weixin-output] FAIL: missing required modules referenced by relative require():");
    for (const item of missingRequires.slice(0, 50)) {
      console.error(`- from: ${item.from}`);
      console.error(`  require: ${item.require}`);
      console.error(`  resolved: ${item.resolved}`);
    }
    if (missingRequires.length > 50) {
      console.error(`(showing first 50 of ${missingRequires.length})`);
    }
    process.exit(1);
  }

  const wxssHits = scanWxssForbiddenTokens(mpRoot);
  if (wxssHits.length > 0) {
    console.error("[verify:mp-weixin-output] FAIL: forbidden tokens found in mp-weixin wxss output:");
    for (const hit of wxssHits.slice(0, 50)) {
      console.error(`- ${hit.file}:${hit.line} token=${hit.token}`);
      console.error(`  ${hit.snippet}`);
    }
    if (wxssHits.length > 50) {
      console.error(`(showing first 50 of ${wxssHits.length})`);
    }
    process.exit(1);
  }

  console.log(`[verify:mp-weixin-output] PASS: mpRoot=${mpRoot} pages=${pages.length}`);
}

main();
