/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}"
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        gray: {
          50:  'var(--color-gray-50)',
          100: 'var(--color-gray-100)',
          200: 'var(--color-gray-200)',
          300: 'var(--color-gray-300)',
          400: 'var(--color-gray-400)',
          500: 'var(--color-gray-500)',
          600: 'var(--color-gray-600)',
          700: 'var(--color-gray-700)',
          800: 'var(--color-gray-800)',
          850: 'var(--color-gray-850)',
          900: 'var(--color-gray-900)',
          950: 'var(--color-gray-950)',
        }
      }
    },
  },
  // Weapp tailwindcss might require disabling corePlugins preflight in WeChat
  corePlugins: {
    preflight: false
  },
  plugins: [],
}
