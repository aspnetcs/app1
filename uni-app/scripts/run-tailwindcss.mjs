import { spawn } from 'node:child_process'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const cli = require.resolve('tailwindcss/lib/cli.js')
const args = [cli, ...process.argv.slice(2)]

const child = spawn(process.execPath, args, {
  stdio: 'inherit',
  env: {
    ...process.env,
    BROWSERSLIST_IGNORE_OLD_DATA: '1',
  },
})

child.on('error', (error) => {
  console.error('[run-tailwindcss] failed to start tailwindcss', error)
  process.exit(1)
})

child.on('exit', (code, signal) => {
  if (signal) {
    process.kill(process.pid, signal)
    return
  }
  process.exit(code ?? 1)
})
