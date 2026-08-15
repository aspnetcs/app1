import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptPath = fileURLToPath(import.meta.url)
const projectRoot = path.resolve(path.dirname(scriptPath), '..')
const srcRoot = path.join(projectRoot, 'src')

const TARGET_EXTENSIONS = new Set(['.ts', '.tsx', '.js', '.jsx', '.vue'])
const CONSOLE_REGEX = /\bconsole\.(log|warn|error)\s*\(/g
const ALLOWLIST = new Set([
  'src/components/audio/AudioRecorder.vue',
  'src/pages/index/index.vue',
  'src/utils/logger.ts',
])

async function collectFiles(dir) {
  const entries = await fs.readdir(dir, { withFileTypes: true })
  const files = []
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      files.push(...(await collectFiles(fullPath)))
      continue
    }
    if (TARGET_EXTENSIONS.has(path.extname(entry.name)) && !entry.name.includes('.test.')) {
      files.push(fullPath)
    }
  }
  return files
}

async function main() {
  const files = await collectFiles(srcRoot)
  const findings = []

  for (const file of files) {
    const relativePath = path.relative(projectRoot, file).replace(/\\/g, '/')
    if (ALLOWLIST.has(relativePath)) {
      continue
    }
    const content = await fs.readFile(file, 'utf8')
    const lines = content.split(/\r?\n/)
    lines.forEach((line, index) => {
      CONSOLE_REGEX.lastIndex = 0
      let match = CONSOLE_REGEX.exec(line)
      while (match) {
        findings.push({
          relativePath,
          line: index + 1,
          column: match.index + 1,
          call: match[0],
        })
        match = CONSOLE_REGEX.exec(line)
      }
    })
  }

  if (findings.length === 0) {
    console.log('[verify:console] PASS no direct console usage in uni-app/src production files')
    return
  }

  console.error(`[verify:console] FAIL found ${findings.length} direct console call(s):`)
  findings.forEach((item) => {
    console.error(`- ${item.relativePath}:${item.line}:${item.column} ${item.call}`)
  })
  process.exitCode = 1
}

main().catch((error) => {
  console.error('[verify:console] ERROR', error)
  process.exitCode = 1
})
