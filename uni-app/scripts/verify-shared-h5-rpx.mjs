import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptPath = fileURLToPath(import.meta.url)
const projectRoot = path.resolve(path.dirname(scriptPath), '..')
const srcRoot = path.join(projectRoot, 'src')

const TARGET_EXTENSIONS = new Set(['.vue', '.css', '.scss'])
const RPX_REGEX = /\b\d+(?:\.\d+)?rpx\b/g

function opensMpOnlyBlock(line) {
  return /#ifdef\s+MP(?:-[A-Z0-9_]+)?/.test(line) || /#ifndef\s+H5/.test(line)
}

function closesConditionalBlock(line) {
  return /#endif/.test(line)
}

async function collectFiles(dir) {
  const entries = await fs.readdir(dir, { withFileTypes: true })
  const files = []
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      files.push(...(await collectFiles(fullPath)))
      continue
    }
    if (TARGET_EXTENSIONS.has(path.extname(entry.name))) {
      files.push(fullPath)
    }
  }
  return files
}

async function main() {
  const files = await collectFiles(srcRoot)
  const findings = []

  for (const file of files) {
    const content = await fs.readFile(file, 'utf8')
    const lines = content.split(/\r?\n/)
    let mpOnlyDepth = 0

    lines.forEach((line, index) => {
      if (opensMpOnlyBlock(line)) {
        mpOnlyDepth += 1
      }

      if (mpOnlyDepth === 0) {
        RPX_REGEX.lastIndex = 0
        let match = RPX_REGEX.exec(line)
        while (match) {
          findings.push({
            file: path.relative(projectRoot, file).replace(/\\/g, '/'),
            line: index + 1,
            column: match.index + 1,
            value: match[0],
          })
          match = RPX_REGEX.exec(line)
        }
      }

      if (closesConditionalBlock(line) && mpOnlyDepth > 0) {
        mpOnlyDepth -= 1
      }
    })
  }

  if (findings.length === 0) {
    console.log('[verify:h5-rpx] PASS no shared H5-active rpx declarations in uni-app/src')
    return
  }

  console.error(`[verify:h5-rpx] FAIL found ${findings.length} H5-active rpx declaration(s):`)
  findings.forEach((item) => {
    console.error(`- ${item.file}:${item.line}:${item.column} ${item.value}`)
  })
  process.exitCode = 1
}

main().catch((error) => {
  console.error('[verify:h5-rpx] ERROR', error)
  process.exitCode = 1
})
