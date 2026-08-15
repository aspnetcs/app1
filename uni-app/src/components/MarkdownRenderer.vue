<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import MarkdownIt from 'markdown-it'
// #ifndef H5
import MpHtml from 'mp-html/dist/uni-app/components/mp-html/mp-html.vue'
// #endif

type HighlightRuntime = {
  getLanguage: (language: string) => unknown
  highlight: (code: string, options: { language: string; ignoreIllegals?: boolean }) => { value: string }
}

type MermaidRuntime = {
  initialize: (options: { startOnLoad: boolean; securityLevel: 'strict' }) => void
  render: (id: string, code: string) => Promise<{ svg: string }>
}

const props = defineProps<{
  content: string
}>()

const renderedHtml = computed(() => {
  return md.render(props.content || '')
})

const mdWrapper = ref<HTMLElement | null>(null)
let mermaidInitialized = false
let highlightRuntimePromise: Promise<HighlightRuntime> | null = null
let mermaidRuntimePromise: Promise<MermaidRuntime> | null = null

const md: MarkdownIt = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
})

// #ifdef H5
function escapeAttribute(value: string): string {
  return md.utils.escapeHtml(value)
}

function encodeBlockSource(value: string): string {
  return encodeURIComponent(value)
}

async function loadHighlightRuntime(): Promise<HighlightRuntime> {
  if (!highlightRuntimePromise) {
    highlightRuntimePromise = Promise.all([
      import('highlight.js'),
      import('highlight.js/styles/github-dark.css'),
    ]).then(([module]) => {
      return ((module as { default?: HighlightRuntime }).default ?? module) as HighlightRuntime
    })
  }
  return highlightRuntimePromise
}

async function loadMermaidRuntime(): Promise<MermaidRuntime> {
  if (!mermaidRuntimePromise) {
    mermaidRuntimePromise = import('mermaid').then((module) => {
      return ((module as { default?: MermaidRuntime }).default ?? module) as MermaidRuntime
    })
  }
  return mermaidRuntimePromise
}

md.options.highlight = function (str: string, lang: string): string {
  if (lang === 'mermaid') {
    return `<div class="mermaid-block" data-code="${escapeAttribute(encodeBlockSource(str))}"></div>`
  }

  return '<pre class="hljs md-code-block bg-gray-800 text-gray-100 rounded-lg p-4 my-3 text-sm overflow-x-auto !font-mono shadow-sm"><code class="md-code-content" data-lang="' +
    escapeAttribute(lang || '') +
    '">' +
    md.utils.escapeHtml(str) +
    '</code></pre>'
}

type RenderRule = NonNullable<(typeof md.renderer.rules)[string]>

const withClass = (className: string): RenderRule => {
  return (tokens, idx, options, env, self) => {
    tokens[idx].attrJoin('class', className)
    return self.renderToken(tokens, idx, options)
  }
}

md.renderer.rules.paragraph_open = withClass('md-paragraph')
md.renderer.rules.bullet_list_open = withClass('md-list md-list-unordered')
md.renderer.rules.ordered_list_open = withClass('md-list md-list-ordered')
md.renderer.rules.list_item_open = withClass('md-list-item')
md.renderer.rules.blockquote_open = withClass('md-blockquote')
md.renderer.rules.link_open = withClass('md-link')
md.renderer.rules.heading_open = ((tokens, idx, options, env, self) => {
  tokens[idx].attrJoin('class', `md-heading md-heading-${tokens[idx].tag}`)
  return self.renderToken(tokens, idx, options)
}) as RenderRule
md.renderer.rules.code_inline = ((tokens, idx) => {
  return `<code class="md-inline-code">${md.utils.escapeHtml(tokens[idx].content)}</code>`
}) as RenderRule

async function renderHighlightBlocks(): Promise<void> {
  const root = mdWrapper.value
  if (!root) return

  const blocks = Array.from(root.querySelectorAll<HTMLElement>('.md-code-content:not([data-highlighted])'))
  if (!blocks.length) return

  const shouldLoadRuntime = blocks.some((block) => {
    const language = (block.getAttribute('data-lang') || '').trim()
    return Boolean(language)
  })

  if (!shouldLoadRuntime) {
    blocks.forEach((block) => block.setAttribute('data-highlighted', 'true'))
    return
  }

  const highlight = await loadHighlightRuntime()

  blocks.forEach((block) => {
    const language = (block.getAttribute('data-lang') || '').trim()
    const source = block.textContent || ''
    if (language && highlight.getLanguage(language)) {
      block.innerHTML = highlight.highlight(source, {
        language,
        ignoreIllegals: true,
      }).value
    } else {
      block.textContent = source
    }
    block.setAttribute('data-highlighted', 'true')
  })
}

async function renderMermaidBlocks(): Promise<void> {
  const root = mdWrapper.value
  if (!root) return

  const blocks = Array.from(root.querySelectorAll<HTMLElement>('.mermaid-block:not([data-rendered])'))
  if (!blocks.length) return

  const mermaid = await loadMermaidRuntime()
  if (!mermaidInitialized) {
    mermaid.initialize({ startOnLoad: false, securityLevel: 'strict' })
    mermaidInitialized = true
  }

  await Promise.all(
    blocks.map(async (block, index) => {
      const code = decodeURIComponent(block.getAttribute('data-code') || '')
      const id = `mermaid-md-${Date.now()}-${index}`
      try {
        const { svg } = await mermaid.render(id, code)
        block.innerHTML = svg
      } catch {
        block.innerHTML = `<pre class="mermaid-fallback">${md.utils.escapeHtml(code)}</pre>`
      }
      block.setAttribute('data-rendered', 'true')
    }),
  )
}

async function enhanceRenderedBlocks(): Promise<void> {
  await renderHighlightBlocks()
  await renderMermaidBlocks()
}

onMounted(() => {
  nextTick(() => {
    void enhanceRenderedBlocks()
  })
})

watch(renderedHtml, () => {
  nextTick(() => {
    void enhanceRenderedBlocks()
  })
})
// #endif

// #ifndef H5
md.options.typographer = false
md.options.highlight = function (str: string): string {
  return '<pre><code>' + md.utils.escapeHtml(str) + '</code></pre>'
}
// #endif

const mpContainerStyle = 'font-size:14px;line-height:1.6;color:inherit;'
const mpTagStyle: Record<string, string> = {
  p: 'margin:0 0 12px 0;line-height:1.6;',
  a: 'color:#3b82f6;text-decoration:underline;',
  blockquote: 'border-left:4px solid #cbd5e1;padding-left:12px;margin:10px 0;color:#64748b;',
  ul: 'padding-left:18px;margin:0 0 12px 0;',
  ol: 'padding-left:18px;margin:0 0 12px 0;',
  li: 'margin:0 0 4px 0;',
  h1: 'font-size:20px;font-weight:600;margin:16px 0 10px 0;line-height:1.3;',
  h2: 'font-size:18px;font-weight:600;margin:14px 0 8px 0;line-height:1.3;',
  h3: 'font-size:16px;font-weight:600;margin:12px 0 6px 0;line-height:1.3;',
  pre: 'background:#111827;color:#f9fafb;padding:12px;border-radius:8px;overflow:auto;margin:10px 0;font-size:12px;',
  code: 'font-family:monospace;',
}
</script>

<template>
  <!-- #ifdef H5 -->
  <view ref="mdWrapper" class="markdown-wrapper break-words w-full" v-html="renderedHtml"></view>
  <!-- #endif -->

  <!-- #ifndef H5 -->
  <view class="markdown-wrapper break-words w-full">
    <MpHtml
      :content="renderedHtml"
      :container-style="mpContainerStyle"
      :tag-style="mpTagStyle"
      :copy-link="true"
      :selectable="true"
    />
  </view>
  <!-- #endif -->
</template>

<style>
.markdown-wrapper {
  color: inherit;
  font-size: 14px;
  line-height: 1.6;
}

.markdown-wrapper .md-paragraph {
  margin-bottom: 0.75em;
  min-height: 1.6em;
}

.markdown-wrapper .md-list {
  padding-left: 1.5em;
  margin-bottom: 0.75em;
}

.markdown-wrapper .md-list-unordered {
  list-style-type: disc !important;
}

.markdown-wrapper .md-list-ordered {
  list-style-type: decimal !important;
}

.markdown-wrapper .md-list-item {
  margin-bottom: 0.25em;
}

.markdown-wrapper .md-heading {
  font-weight: 600;
  margin-top: 1.25em;
  margin-bottom: 0.5em;
}

.markdown-wrapper .md-heading-h1 { font-size: 1.75em; }
.markdown-wrapper .md-heading-h2 { font-size: 1.5em; }
.markdown-wrapper .md-heading-h3 { font-size: 1.25em; }

.markdown-wrapper .md-inline-code {
  background-color: rgba(100, 116, 139, 0.15);
  border-radius: 4px;
  padding: 0.1em 0.3em;
  font-family: monospace;
}

.markdown-wrapper .md-link {
  color: #3b82f6;
  text-decoration: underline;
}

.markdown-wrapper .md-blockquote {
  border-left: 4px solid #cbd5e1;
  padding-left: 1em;
  margin-left: 0;
  margin-right: 0;
  color: #64748b;
}

.dark .markdown-wrapper .md-inline-code {
  background-color: rgba(148, 163, 184, 0.2);
}

.dark .markdown-wrapper .md-blockquote {
  border-left-color: #475569;
  color: #94a3b8;
}

.markdown-wrapper .md-code-block {
  margin: 12px 0;
}

.markdown-wrapper .mermaid-block {
  width: 100%;
  overflow-x: auto;
  margin: 12px 0;
}

.markdown-wrapper .mermaid-fallback {
  font-family: monospace;
  font-size: 12px;
  padding: 12px;
  background: #1e293b;
  color: #f1f5f9;
  border-radius: 8px;
  overflow-x: auto;
  white-space: pre;
  margin: 0;
}
</style>
