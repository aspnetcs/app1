const CACHE_NAME = 'ai-assistant-static-v1'
const CORE_ASSETS = ['/', '/manifest.webmanifest']

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(CORE_ASSETS)).then(() => self.skipWaiting())
  )
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key)))
    ).then(() => self.clients.claim())
  )
})

self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET') return

  const url = new URL(event.request.url)

  // Never cache API calls, SSE streams, or non-same-origin requests
  if (url.pathname.startsWith('/api/')) return
  if (event.request.headers.get('accept') === 'text/event-stream') return
  if (url.origin !== self.location.origin) return

  // Only cache static assets (with file extensions) and the root page
  const isStaticAsset = /\.\w{2,5}$/.test(url.pathname) || url.pathname === '/'
  if (!isStaticAsset) return

  event.respondWith(
    caches.match(event.request).then((cached) => {
      // Network-first for navigation (HTML), cache-first for assets
      if (event.request.mode === 'navigate') {
        return fetch(event.request)
          .then((response) => {
            if (response && response.status === 200) {
              const clone = response.clone()
              caches.open(CACHE_NAME).then((cache) => cache.put(event.request, clone))
            }
            return response
          })
          .catch(() => cached || new Response('Offline', { status: 503 }))
      }

      // Cache-first for static assets
      if (cached) return cached
      return fetch(event.request).then((response) => {
        if (!response || response.status !== 200 || response.type !== 'basic') return response
        const clone = response.clone()
        caches.open(CACHE_NAME).then((cache) => cache.put(event.request, clone))
        return response
      })
    })
  )
})
