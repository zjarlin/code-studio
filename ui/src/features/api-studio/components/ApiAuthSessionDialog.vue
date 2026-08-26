<script setup lang="ts">
import {
  KeyRound,
  Plus,
  Save,
  ShieldCheck,
  Trash2,
} from '@lucide/vue'
import { ref, watch } from 'vue'

import IconButton from '@/components/composed/icon-button/IconButton.vue'
import { Button } from '@/components/generated/shadcn/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/generated/shadcn/dialog'
import { Input } from '@/components/generated/shadcn/input'
import type { StudioConfig } from '@/studio-config'

import {
  createApiAuthSession,
  type ApiAuthSession,
  type ApiAuthSessionDraft,
} from '../api-auth-sessions'

const props = defineProps<{
  open: boolean
  config?: StudioConfig
  sessions: ApiAuthSession[]
  activeSessionId?: string
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  remove: [sessionId: string]
  save: [session: ApiAuthSession]
  select: [sessionId?: string]
}>()

const selectedSessionId = ref('')
const draft = ref<ApiAuthSessionDraft>(emptyDraft(''))
const error = ref('')
const notice = ref('')

watch(
  () => [props.open, props.config?.contributorId, props.activeSessionId, props.sessions] as const,
  () => {
    if (!props.open) return
    const selected = props.sessions.find((session) => session.id === props.activeSessionId)
      ?? props.sessions.find((session) => session.id === selectedSessionId.value)
      ?? props.sessions[0]
    if (selected) {
      if (selected.id === selectedSessionId.value && draft.value.id === selected.id) return
      editSession(selected)
    } else {
      createSession()
    }
  },
  { immediate: true },
)

function editSession(session: ApiAuthSession): void {
  selectedSessionId.value = session.id
  draft.value = { ...session }
  error.value = ''
  notice.value = ''
}

function createSession(): void {
  selectedSessionId.value = ''
  draft.value = emptyDraft(props.config?.contributorId ?? '')
  error.value = ''
  notice.value = ''
}

function saveSession(): void {
  error.value = ''
  notice.value = ''
  try {
    const session = createApiAuthSession({
      ...draft.value,
      contributorId: props.config?.contributorId ?? '',
    })
    emit('save', session)
    selectedSessionId.value = session.id
    draft.value = { ...session }
    notice.value = '令牌已在当前页面启用'
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '登录态保存失败'
  }
}

function selectSession(session: ApiAuthSession): void {
  editSession(session)
  emit('select', session.id)
}

function removeSession(): void {
  const session = props.sessions.find((candidate) => candidate.id === selectedSessionId.value)
  if (!session || !window.confirm(`删除登录态“${session.name}”？`)) return
  emit('remove', session.id)
  createSession()
}

function emptyDraft(contributorId: string): ApiAuthSessionDraft {
  return {
    contributorId,
    name: '',
    accessToken: '',
  }
}
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent class="api-auth-dialog">
      <DialogHeader>
        <DialogTitle>登录态</DialogTitle>
        <DialogDescription class="sr-only">管理当前宿主的接口请求身份</DialogDescription>
      </DialogHeader>

      <div v-if="config" class="api-auth-layout">
        <aside class="api-auth-session-panel">
          <div class="api-auth-session-heading">
            <strong>{{ config.displayName }}</strong>
            <IconButton :icon="Plus" label="新增登录态" @click="createSession" />
          </div>
          <div class="api-auth-session-list">
            <button
              v-for="session in sessions"
              :key="session.id"
              class="api-auth-session-item"
              :class="{ active: session.id === selectedSessionId }"
              type="button"
              @click="selectSession(session)">
              <KeyRound :size="15" />
              <span>
                <strong>{{ session.name }}</strong>
                <small>Bearer Token</small>
              </span>
              <ShieldCheck v-if="session.id === activeSessionId" :size="14" aria-label="当前登录态" />
            </button>
            <button
              class="api-auth-session-item"
              :class="{ active: !selectedSessionId }"
              type="button"
              @click="createSession">
              <Plus :size="15" />
              <span><strong>新增登录态</strong></span>
            </button>
          </div>
        </aside>

        <form class="api-auth-form" @submit.prevent="saveSession">
          <label class="api-auth-field">
            <span>登录态名称</span>
            <Input v-model="draft.name" autocomplete="off" placeholder="例如：管理员、只读用户" />
          </label>
          <label class="api-auth-field">
            <span>Bearer Token</span>
            <Input v-model="draft.accessToken" autocomplete="off" type="password" />
          </label>

          <div v-if="error" class="api-auth-feedback error" role="alert">{{ error }}</div>
          <div v-else-if="notice" class="api-auth-feedback" role="status">{{ notice }}</div>

          <DialogFooter class="api-auth-actions">
            <IconButton
              v-if="selectedSessionId"
              :icon="Trash2"
              label="删除登录态"
              variant="danger"
              @click="removeSession"
            />
            <span />
            <Button type="button" variant="ghost" @click="emit('update:open', false)">取消</Button>
            <Button type="submit">
              <Save />
              保存并启用
            </Button>
          </DialogFooter>
        </form>
      </div>
      <div v-else class="api-auth-empty">宿主配置尚未加载</div>
    </DialogContent>
  </Dialog>
</template>
