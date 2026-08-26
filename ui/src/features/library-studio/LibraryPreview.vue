<script setup lang="ts">
import { FileCode2, RefreshCw } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import { Button } from '@/components/generated/shadcn/button'
import { LowcodeApi } from '@/lowcode-api'
import type { LowcodePreviewFile } from '@/types'

const props = defineProps<{
  libraryId?: number | string
  featureId?: number | string
}>()

const api = new LowcodeApi()
const files = ref<LowcodePreviewFile[]>([])
const selectedPath = ref('')
const loading = ref(false)
const error = ref('')
const selectedFile = computed(() => files.value.find((file) => file.filePath === selectedPath.value))

watch(() => [props.libraryId, props.featureId], () => {
  files.value = []
  selectedPath.value = ''
}, { immediate: true })

async function load(): Promise<void> {
  if (props.libraryId === undefined) return
  loading.value = true
  error.value = ''
  try {
    const preview = await api.previewLibrary(props.libraryId, props.featureId)
    files.value = preview.files
    selectedPath.value = preview.files[0]?.filePath ?? ''
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '读取生成预览失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="library-preview-workspace">
    <header class="library-table-toolbar">
      <div><strong>生成预览</strong><span>{{ featureId != null ? `功能分类 ${featureId}` : '当前 Library 全部生成文件' }}</span></div>
      <Button :disabled="libraryId === undefined || loading" size="sm" @click="load"><RefreshCw />{{ loading ? '生成中' : '生成预览' }}</Button>
    </header>
    <p v-if="libraryId === undefined" class="studio-notice">请先保存 Library，再生成预览。</p>
    <p v-if="error" class="studio-notice error">{{ error }}</p>
    <div v-if="files.length" class="library-preview-grid">
      <nav aria-label="生成文件">
        <button v-for="file in files" :key="file.filePath" :class="{ active: file.filePath === selectedPath }" type="button" @click="selectedPath = file.filePath">
          <FileCode2 /><span>{{ file.filePath }}</span>
        </button>
      </nav>
      <pre><code>{{ selectedFile?.content }}</code></pre>
    </div>
    <div v-else-if="libraryId !== undefined && !loading && !error" class="feature-workbench-empty"><FileCode2 /><strong>点击生成预览</strong></div>
  </section>
</template>
