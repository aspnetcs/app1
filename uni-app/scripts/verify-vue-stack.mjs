import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

const rootDir = process.cwd()
const pkgPath = path.join(rootDir, 'package.json')
const lockPath = path.join(rootDir, 'package-lock.json')
const viteConfigPath = path.join(rootDir, 'vite.config.mjs')
const uniH5VueRuntimePath = path.join(
  rootDir,
  'node_modules',
  '@dcloudio',
  'uni-h5-vue',
  'dist',
  'vue.runtime.esm.js'
)

const REQUIRED_OVERRIDES = [
  'vue',
  '@vue/compiler-core',
  '@vue/compiler-dom',
  '@vue/compiler-sfc',
  '@vue/compiler-ssr',
  '@vue/server-renderer',
  '@vue/shared',
]

function fail(message) {
  console.error(`[verify:vue-stack] ${message}`)
  process.exit(1)
}

function readJson(filePath) {
  try {
    return JSON.parse(fs.readFileSync(filePath, 'utf8'))
  } catch (error) {
    fail(`failed to read ${path.basename(filePath)}: ${error.message}`)
  }
}

function resolveVueTargetVersion(pkg) {
  const overrideVueVersion = pkg?.overrides?.vue
  if (typeof overrideVueVersion !== 'string' || !overrideVueVersion.trim()) {
    fail('package.json overrides.vue must define the single Vue target version source')
  }
  return overrideVueVersion.trim()
}

const pkg = readJson(pkgPath)
const lock = readJson(lockPath)
const overrides = pkg.overrides ?? {}
const viteConfig = fs.readFileSync(viteConfigPath, 'utf8')
const vueTargetVersion = resolveVueTargetVersion(pkg)

for (const name of REQUIRED_OVERRIDES) {
  if (overrides[name] !== vueTargetVersion) {
    fail(`override mismatch for ${name}, expected ${vueTargetVersion}, got ${overrides[name] ?? 'missing'}`)
  }
}

const lockPackages = lock.packages ?? {}
const lockMustBePinned = [
  ...REQUIRED_OVERRIDES,
  '@vue/runtime-core',
  '@vue/runtime-dom',
  '@vue/reactivity',
]

for (const pkgName of lockMustBePinned) {
  const key = `node_modules/${pkgName}`
  const metadata = lockPackages[key]
  if (!metadata?.version) {
    fail(`missing ${pkgName} in package-lock.json`)
  }
  if (metadata.version !== vueTargetVersion) {
    fail(`${pkgName} is ${metadata.version} in package-lock.json, expected ${vueTargetVersion}`)
  }
}

for (const [pkgName, meta] of Object.entries(lockPackages)) {
  if (!pkgName.startsWith('node_modules/@dcloudio/')) continue
  const deps = meta?.dependencies ?? {}
  for (const vuePkg of REQUIRED_OVERRIDES.filter((name) => name !== 'vue')) {
    const depVersion = deps[vuePkg]
    if (depVersion && depVersion !== vueTargetVersion) {
      // DCloud packages may declare older hard pins; this check keeps the drift visible.
      // We do not fail here because npm overrides is expected to rewrite resolution.
      console.warn(`[verify:vue-stack] ${pkgName} declares ${vuePkg}@${depVersion}, override is enforcing ${vueTargetVersion}`)
    }
  }
}

if (!fs.existsSync(uniH5VueRuntimePath)) {
  fail(`missing H5 Vue runtime alias target: ${path.relative(rootDir, uniH5VueRuntimePath)}`)
}

if (!viteConfig.includes('@dcloudio/uni-h5-vue/dist/vue.runtime.esm.js')) {
  fail('vite.config.mjs no longer aliases H5 builds to @dcloudio/uni-h5-vue runtime')
}

console.log('[verify:vue-stack] Vue dependency stack and H5 runtime alias are pinned and auditable.')
