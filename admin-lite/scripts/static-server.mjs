import http from "node:http";
import { readFile, stat } from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

const distRoot = path.resolve(fileURLToPath(new URL("../dist/", import.meta.url)));
const indexHtml = path.join(distRoot, "index.html");
const port = Number(process.env.PORT || process.argv[2] || 4175);
const host = process.env.HOST || "0.0.0.0";

const contentTypes = new Map([
  [".html", "text/html; charset=utf-8"],
  [".js", "text/javascript; charset=utf-8"],
  [".css", "text/css; charset=utf-8"],
  [".json", "application/json; charset=utf-8"],
  [".svg", "image/svg+xml"],
  [".png", "image/png"],
  [".jpg", "image/jpeg"],
  [".jpeg", "image/jpeg"],
  [".ico", "image/x-icon"],
  [".map", "application/json; charset=utf-8"]
]);

function send(res, status, body, type = "text/plain; charset=utf-8") {
  res.writeHead(status, { "Content-Type": type });
  res.end(body);
}

function safeResolve(urlPath) {
  const normalized = path.normalize(urlPath).replace(/^(\.\.(?:[\\/]|$))+/, "");
  const candidate = path.resolve(distRoot, `.${normalized}`);
  if (!candidate.startsWith(distRoot + path.sep) && candidate !== distRoot) {
    return indexHtml;
  }
  return candidate;
}

async function serveFile(res, filePath) {
  const fileStat = await stat(filePath);
  const resolvedPath = fileStat.isDirectory() ? path.join(filePath, "index.html") : filePath;
  const body = await readFile(resolvedPath);
  const type = contentTypes.get(path.extname(resolvedPath).toLowerCase()) || "application/octet-stream";
  send(res, 200, body, type);
}

const server = http.createServer(async (req, res) => {
  try {
    const requestUrl = new URL(req.url || "/", "http://localhost");
    const pathname = decodeURIComponent(requestUrl.pathname || "/");
    const filePath = pathname === "/" ? indexHtml : safeResolve(pathname);

    try {
      await serveFile(res, filePath);
    } catch {
      await serveFile(res, indexHtml);
    }
  } catch (error) {
    send(res, 500, `Internal Server Error: ${error instanceof Error ? error.message : String(error)}`);
  }
});

server.listen(port, host, () => {
  console.log(`Admin Lite static server ready on http://${host}:${port}/`);
});

server.on("error", (error) => {
  console.error(error);
  process.exitCode = 1;
});
