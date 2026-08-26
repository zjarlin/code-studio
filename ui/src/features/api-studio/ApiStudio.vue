<script setup lang="ts">
import {
  RefreshCw,
  Clock,
  Globe,
  KeyRound,
  Search,
} from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'

import IconButton from '@/components/composed/icon-button/IconButton.vue'
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from '@/components/generated/shadcn/select'

import ApiOperationTree from './components/ApiOperationTree.vue'
import ApiAuthSessionDialog from './components/ApiAuthSessionDialog.vue'
import ApiRequestEditor from './components/ApiRequestEditor.vue'
import ApiResponsePanel from './components/ApiResponsePanel.vue'
import { type ApiAuthSession, useApiAuthSessions } from './api-auth-sessions'
import { useApiStudio } from './useApiStudio'

const NO_AUTH_SESSION = '__no_auth__'
const authDialogOpen = ref(false)
const authSessions = useApiAuthSessions()
const studio = useApiStudio((contributorId) =>
  authSessions.activeSession(contributorId ?? '')?.accessToken ?? '',
)
const {
  baseUrl,
  bodyText,
  config,
  document,
  error,
  fileReferenceUploads,
  filteredOperations,
  groups,
  headerValues,
  history,
  incompleteCount,
  loading,
  operations,
  metadataFilter,
  multipartValues,
  pathValues,
  query,
  response,
  selectedOperation,
  queryValues,
} = studio
const contributorAuthSessions = computed(() => authSessions.sessionsFor(config.value?.contributorId ?? ''))
const activeAuthSession = computed(() => authSessions.activeSession(config.value?.contributorId ?? ''))

function selectHistory(id: string, path: string): void {
  const operation = studio.operations.value.find((item) =>
    item.id === id || item.addresses.some((address) => address.path === path),
  )
  if (operation) {
    studio.selectOperation(operation)
    studio.selectOperationPath(path)
  }
}

function selectAuthSession(value: unknown): void {
  const contributorId = config.value?.contributorId
  if (!contributorId) return
  const sessionId = String(value ?? '')
  authSessions.select(contributorId, sessionId === NO_AUTH_SESSION ? undefined : sessionId)
}

function saveAuthSession(session: ApiAuthSession): void {
  authSessions.upsert(session)
}

function removeAuthSession(sessionId: string): void {
  const contributorId = config.value?.contributorId
  if (contributorId) authSessions.remove(contributorId, sessionId)
}

onMounted(() => {
  void studio.load()
})
</script>

<template>
  <main class="api-studio-shell">
    <aside class="api-sidebar">
      <div class="api-sidebar-heading">
        <div>
          <span class="api-panel-kicker">Workspace</span>
          <strong>API Studio</strong>
        </div>
        <button class="api-icon-button" type="button" title="刷新 OpenAPI" @click="studio.load">
          <RefreshCw :size="16" />
        </button>
      </div>
      <label class="api-search-box">
        <Search :size="15" />
        <input v-model="query" placeholder="搜索接口、路径或标签" />
      </label>
      <div class="api-sidebar-summary">
        <button :class="{ active: metadataFilter === 'all' }" type="button" @click="metadataFilter = 'all'">
          全部 {{ operations.length }}
        </button>
        <button :class="{ active: metadataFilter === 'incomplete' }" type="button" @click="metadataFilter = 'incomplete'">
          待补 {{ incompleteCount }}
        </button>
        <span>{{ history.length }} 条历史</span>
      </div>
      <ApiOperationTree
        :groups="groups"
        :selected-id="selectedOperation?.id"
        @select="studio.selectOperation"
      />
      <div v-if="history.length" class="api-history">
        <div class="api-history-heading">
          <span><Clock :size="14" />最近请求</span>
        </div>
        <button
          v-for="entry in history.slice(0, 4)"
          :key="`${entry.id}:${entry.createdAt}`"
          class="api-history-item"
          type="button"
          @click="selectHistory(entry.id, entry.path)">
          <span class="api-method" :class="`method-${entry.method}`">{{ entry.method }}</span>
          <span>{{ entry.path }}</span>
          <strong v-if="entry.status" :class="{ success: entry.status < 400 }">{{ entry.status }}</strong>
        </button>
      </div>
    </aside>

    <section class="api-main">
      <div class="api-environment-bar">
        <div class="api-environment-label">
          <Globe :size="15" />
          <span>请求环境</span>
        </div>
        <div class="api-environment-select" aria-label="当前宿主">
          <span v-if="config" class="api-environment-selected">
            <strong>{{ config.displayName }}</strong>
            <code>{{ baseUrl }}</code>
          </span>
          <span v-else>正在读取宿主配置</span>
        </div>
        <div class="api-auth-controls">
          <Select
            :model-value="activeAuthSession?.id ?? NO_AUTH_SESSION"
            :disabled="!config"
            @update:model-value="selectAuthSession">
            <SelectTrigger class="api-auth-select" aria-label="选择登录态" size="sm">
              <KeyRound :size="14" />
              <SelectValue>
                <span class="api-auth-selected">{{ activeAuthSession?.name ?? '开发免认证' }}</span>
              </SelectValue>
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                <SelectLabel>登录态</SelectLabel>
                <SelectItem :value="NO_AUTH_SESSION">开发免认证</SelectItem>
                <SelectItem v-for="session in contributorAuthSessions" :key="session.id" :value="session.id">
                  <span class="api-auth-option">
                    <strong>{{ session.name }}</strong>
                    <small>Bearer Token</small>
                  </span>
                </SelectItem>
              </SelectGroup>
            </SelectContent>
          </Select>
          <IconButton
            :disabled="!config"
            :icon="KeyRound"
            label="管理登录态"
            @click="authDialogOpen = true"
          />
        </div>
      </div>

      <div v-if="error" class="api-global-error">{{ error }}</div>

      <div v-if="selectedOperation" class="api-operation-header">
        <div class="api-operation-title">
          <span class="api-method api-method-large" :class="`method-${selectedOperation.method}`">
            {{ selectedOperation.method }}
          </span>
          <div>
            <div class="api-operation-addresses" role="radiogroup" aria-label="请求地址">
              <label
                v-for="address in selectedOperation.addresses"
                :key="address.path"
                :class="{ active: address.path === selectedOperation.path }">
                <input
                  class="api-operation-address-input"
                  type="radio"
                  name="api-operation-address"
                  :value="address.path"
                  :checked="address.path === selectedOperation.path"
                  @change="studio.selectOperationPath(address.path)"
                />
                <code>{{ address.path }}</code>
              </label>
            </div>
            <h1>{{ selectedOperation.summary }}</h1>
            <small v-if="selectedOperation.metadataIssues.length" class="api-metadata-issues">
              {{ selectedOperation.metadataIssues.join(' · ') }}
            </small>
          </div>
        </div>
        <p v-if="selectedOperation.description">{{ selectedOperation.description }}</p>
      </div>

      <div v-if="loading && !selectedOperation" class="api-loading-state">正在读取 OpenAPI...</div>
      <div v-else class="api-request-response-grid">
        <ApiRequestEditor
          :operation="selectedOperation"
          :document="document ?? {}"
          :path-values="pathValues"
          :query-values="queryValues"
          :header-values="headerValues"
          :body-text="bodyText"
          :multipart-values="multipartValues"
          :file-reference-uploads="fileReferenceUploads"
          :loading="loading"
          @update-field="studio.updateField"
          @update-body="bodyText = $event"
          @update-multipart-field="studio.updateMultipartField"
          @upload-file-reference="studio.uploadFileReference"
          @send="studio.send"
        />
        <ApiResponsePanel
          :document="document ?? {}"
          :operation="selectedOperation"
          :response="response"
          :error="selectedOperation ? error : ''"
          :loading="loading"
        />
      </div>
    </section>

    <ApiAuthSessionDialog
      v-model:open="authDialogOpen"
      :active-session-id="activeAuthSession?.id"
      :config="config"
      :sessions="contributorAuthSessions"
      @remove="removeAuthSession"
      @save="saveAuthSession"
      @select="selectAuthSession"
    />
  </main>
</template>
