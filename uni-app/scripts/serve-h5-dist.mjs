import fs from 'node:fs'
import http from 'node:http'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const projectRoot = path.resolve(scriptDir, '..')
const distRoot = path.join(projectRoot, 'dist', 'build', 'h5')
const port = Number(process.argv[2] || '41880')

const MIME_TYPES = {
  '.css': 'text/css; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.ico': 'image/x-icon',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.txt': 'text/plain; charset=utf-8',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
}

function resolveTarget(urlPath) {
  const normalized = decodeURIComponent((urlPath || '/').split('?')[0])
  const requested = normalized === '/' ? '/index.html' : normalized
  const target = path.normalize(path.join(distRoot, requested))
  if (!target.startsWith(distRoot)) {
    return null
  }
  if (fs.existsSync(target) && fs.statSync(target).isFile()) {
    return target
  }
  return path.join(distRoot, 'index.html')
}

const server = http.createServer((req, res) => {
  const target = resolveTarget(req.url || '/')
  if (!target || !fs.existsSync(target)) {
    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' })
    res.end('Not Found')
    return
  }

  const ext = path.extname(target).toLowerCase()
  res.writeHead(200, {
    'Cache-Control': 'no-store',
    'Content-Type': MIME_TYPES[ext] || 'application/octet-stream',
  })
  fs.createReadStream(target).pipe(res)
})

server.listen(port, '127.0.0.1', () => {
  console.log(`[serve-h5-dist] http://127.0.0.1:${port}`)
})
