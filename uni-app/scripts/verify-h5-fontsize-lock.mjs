import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptPath = fileURLToPath(import.meta.url)
const projectRoot = path.resolve(path.dirname(scriptPath), '..')
const appVuePath = path.join(projectRoot, 'src', 'App.vue')

function normalizeWhitespace(text) {
  return String(text || '').replace(/\r\n/g, '\n')
}

function collectFontSizeDecls(cssText, selector) {
  const decls = []
  const re = new RegExp(`${selector}\\s*\\{[^}]*?font-size\\s*:\\s*([^;]+);`, 'gi')
  let m
  while ((m = re.exec(cssText))) {
    decls.push(String(m[1] || '').trim())
  }
  return decls
}

function isPxValue(value) {
  const v = String(value || '').trim().toLowerCase()
  // allow "16px" and "16px !important"
  return /^\d+(\.\d+)?px(\s*!important)?$/.test(v)
}

async function main() {
  const raw = await fs.readFile(appVuePath, 'utf8')
  const text = normalizeWhitespace(raw)

  const htmlDecls = collectFontSizeDecls(text, 'html')
  const pageDecls = collectFontSizeDecls(text, 'page')

  if (htmlDecls.length === 0) {
    console.error('[verify:h5-fontsize-lock] FAIL missing html { font-size: ... } in src/App.vue')
    process.exitCode = 1
    return
  }
  if (pageDecls.length === 0) {
    console.error('[verify:h5-fontsize-lock] FAIL missing page { font-size: ... } in src/App.vue')
    process.exitCode = 1
    return
  }

  const forbiddenHtml = htmlDecls.filter((v) => !isPxValue(v))
  const forbiddenPage = pageDecls.filter((v) => !isPxValue(v))
  if (forbiddenHtml.length || forbiddenPage.length) {
    console.error('[verify:h5-fontsize-lock] FAIL html/page font-size must be fixed px values (no rpx/rem/vw)')
    forbiddenHtml.forEach((v) => console.error(`- html font-size: ${v}`))
    forbiddenPage.forEach((v) => console.error(`- page font-size: ${v}`))
    process.exitCode = 1
    return
  }

  const hasExpectedHtml = htmlDecls.some((v) => String(v).replace(/\s+/g, ' ').trim().toLowerCase() === '16px !important')
  if (!hasExpectedHtml) {
    console.error('[verify:h5-fontsize-lock] FAIL expected html font-size to be exactly: 16px !important')
    htmlDecls.forEach((v) => console.error(`- html font-size: ${v}`))
    process.exitCode = 1
    return
  }

  const hasExpectedPage = pageDecls.some((v) => String(v).trim().toLowerCase() === '14px')
  if (!hasExpectedPage) {
    console.error('[verify:h5-fontsize-lock] FAIL expected page font-size to be exactly: 14px')
    pageDecls.forEach((v) => console.error(`- page font-size: ${v}`))
    process.exitCode = 1
    return
  }

  console.log('[verify:h5-fontsize-lock] PASS html/page font-size are locked for H5 rem stability')
}

main().catch((error) => {
  console.error('[verify:h5-fontsize-lock] ERROR', error)
  process.exitCode = 1
})
