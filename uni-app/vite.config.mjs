import { defineConfig } from 'vite'
import uniModule from '@dcloudio/vite-plugin-uni'
import { UnifiedViteWeappTailwindcssPlugin as uvtw } from 'weapp-tailwindcss/vite'
const uni = typeof uniModule === 'function' ? uniModule : uniModule.default
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))

const uniPlatform = process.env.UNI_PLATFORM || ''
const isH5 = uniPlatform === 'h5' || process.argv.includes('h5')
const isMiniProgram = uniPlatform.startsWith('mp-') || process.argv.some((arg) => arg.startsWith('mp-'))
const uniH5VueRuntimePath = resolve(__dirname, 'node_modules/@dcloudio/uni-h5-vue/dist/vue.runtime.esm.js')

// Patch uni-h5-vue updateSlots bug:
// The runtime tries Object.assign on frozen slot objects, causing
// "Cannot assign to read only property '_'" TypeError which halts
// all component re-renders. This plugin wraps the assignment in try-catch.
function patchUniH5VueSlots() {
  return {
    name: 'patch-uni-h5-vue-slots',
    enforce: 'pre',
    transform(code, id) {
      if (!id.includes('uni-h5-vue') && !id.includes('vue.runtime.esm')) return
      // The bug: updateSlots calls extend(slots, children) where extend = Object.assign
      // But initSlots defines children._ with def() making it non-writable/non-configurable
      // When extend copies _ from children to slots, it throws:
      // "TypeError: Cannot assign to read only property '_'"
      // Fix: replace extend(slots, children) with a safe version
      const patched = code.replace(
        /extend\(slots,\s*children\)/g,
        '(function(s,c){for(var k in c){if(k==="_")continue;try{s[k]=c[k]}catch(e){}}return s})(slots,children)'
      )
      if (patched !== code) {
        return { code: patched, map: null }
      }
    }
  }
}

export default defineConfig({
  plugins: [
    patchUniH5VueSlots(),
    uni(),
    // weapp-tailwindcss only on mini program builds (rem->rpx); H5 uses Tailwind CLI pre-compile
    ...(isMiniProgram ? [uvtw({ rem2rpx: true })] : [])
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      ...(isH5 ? { vue: uniH5VueRuntimePath } : {}),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
      },
    },
  },
  build: {
    minify: 'esbuild',
    chunkSizeWarningLimit: 2000,
    terserOptions: undefined,
  },
})
