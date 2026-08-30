<script setup lang="ts">
import { Check, Copy, TriangleAlert } from '@lucide/vue'
import { computed, ref } from 'vue'

import { Button } from '@/components/generated/shadcn/button'

const props = withDefaults(defineProps<{
  content: string
  language?: string
  lineNumbers?: boolean
}>(), {
  language: 'text',
  lineNumbers: false,
})

const copyState = ref<'idle' | 'copied' | 'failed'>('idle')
const lines = computed(() => props.content.split('\n'))
const copyLabel = computed(() => {
  if (copyState.value === 'copied') {
    return '已复制'
  }
  if (copyState.value === 'failed') {
    return '复制失败'
  }
  return '复制代码'
})

async function copyContent(): Promise<void> {
  try {
    await writeClipboardText(props.content)
    copyState.value = 'copied'
  } catch {
    copyState.value = 'failed'
  }

  window.setTimeout(() => {
    copyState.value = 'idle'
  }, 1600)
}

async function writeClipboardText(content: string): Promise<void> {
  let clipboardError: unknown
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(content)
      return
    } catch (cause) {
      clipboardError = cause
    }
  }

  if (copyWithSelection(content)) {
    return
  }
  if (clipboardError instanceof Error) {
    throw clipboardError
  }
  throw new Error('当前浏览器不允许复制代码')
}

function copyWithSelection(content: string): boolean {
  const activeElement = document.activeElement instanceof HTMLElement ? document.activeElement : undefined
  const textarea = document.createElement('textarea')
  textarea.value = content
  textarea.readOnly = true
  textarea.style.position = 'fixed'
  textarea.style.inset = '0 auto auto -9999px'
  document.body.append(textarea)
  try {
    textarea.focus()
    textarea.select()
    return document.execCommand?.('copy') === true
  } finally {
    textarea.remove()
    activeElement?.focus()
  }
}
</script>

<template>
  <section class="studio-code-block">
    <header class="studio-code-toolbar">
      <span>{{ language }}</span>
      <Button :aria-label="copyLabel" :title="copyLabel" size="icon-xs" type="button" variant="ghost" @click="copyContent">
        <Check v-if="copyState === 'copied'" />
        <TriangleAlert v-else-if="copyState === 'failed'" />
        <Copy v-else />
      </Button>
    </header>
    <ol v-if="lineNumbers" class="studio-code-lines">
      <li v-for="(line, index) in lines" :key="index"><code>{{ line || ' ' }}</code></li>
    </ol>
    <pre v-else><code>{{ content }}</code></pre>
  </section>
</template>
