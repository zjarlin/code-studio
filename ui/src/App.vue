<script setup lang="ts">
import { Bot, KeyRound, Library, LoaderCircle, Network } from '@lucide/vue'
import { computed, defineAsyncComponent, onMounted, ref } from 'vue'

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
import { TooltipProvider } from '@/components/generated/shadcn/tooltip'

import { configureLowcodeApiAccessToken } from './lowcode-api'
import {
  availableStudioWorkspaces,
  loadStudioConfig,
  type StudioConfig,
  type StudioWorkspace,
} from './studio-config'
import { resolveStudioEntry } from './studio-entry'
import { zhCN } from './studio-i18n'
import type { Workspace } from './studio-i18n'

const ApiStudio = defineAsyncComponent(() => import('./features/api-studio/ApiStudio.vue'))
const AgentStudio = defineAsyncComponent(() => import('./features/agent-studio/AgentStudio.vue'))
const LibraryStudio = defineAsyncComponent(() => import('./features/library-studio/LibraryStudio.vue'))

const studioEntry = resolveStudioEntry(window.location.search)
const accessToken = ref('')
configureLowcodeApiAccessToken(() => accessToken.value)
const tokenDraft = ref(accessToken.value)
const workspace = ref<Workspace>(studioEntry.workspace)
const tokenModalOpen = ref(false)
const messages = zhCN
const availableWorkspaces = ref<StudioWorkspace[]>([])
const studioConfig = ref<StudioConfig>()
const configLoading = ref(true)
const configError = ref('')
const hasAgent = computed(() => availableWorkspaces.value.includes('agent'))

onMounted(async () => {
  try {
    const config = await loadStudioConfig()
    studioConfig.value = config
    const supported = availableStudioWorkspaces(config)
    availableWorkspaces.value = studioEntry.apiDocumentationOnly
      ? supported.filter((candidate) => candidate === 'api')
      : supported
    const requested = workspace.value as StudioWorkspace
    if (!availableWorkspaces.value.includes(requested)) {
      workspace.value = availableWorkspaces.value[0] ?? 'library'
    }
    if (!availableWorkspaces.value.length) {
      configError.value = '宿主未启用请求的 Studio 能力'
    }
  } catch (cause) {
    configError.value = cause instanceof Error ? cause.message : '读取 Studio 配置失败'
  } finally {
    configLoading.value = false
  }
})

function openTokenModal(): void {
  tokenDraft.value = accessToken.value
  tokenModalOpen.value = true
}

function saveToken(): void {
  accessToken.value = tokenDraft.value.trim()
  tokenModalOpen.value = false
}
</script>

<template>
  <TooltipProvider>
    <div class="studio-shell">
      <header class="studio-header">
        <div class="studio-header-start">
          <div class="brand-mark">
            <Library v-if="workspace === 'library'" />
            <Bot v-else-if="workspace === 'agent'" />
            <Network v-else-if="workspace === 'api'" />
          </div>
          <div class="brand-copy">
            <strong>{{ messages.brand }}</strong>
            <span>{{ messages.workspaces[workspace].description }}</span>
          </div>
          <nav v-if="!studioEntry.apiDocumentationOnly && !configLoading && !configError" class="studio-mode-nav" :aria-label="messages.workspaceNavigation">
            <Button
              v-if="availableWorkspaces.includes('library')"
              class="studio-mode-button"
              :class="{ active: workspace === 'library' }"
              size="sm"
              type="button"
              variant="ghost"
              @click="workspace = 'library'">
              <Library />
              {{ messages.workspaces.library.label }}
            </Button>
            <Button
              v-if="hasAgent"
              class="studio-mode-button"
              :class="{ active: workspace === 'agent' }"
              size="sm"
              type="button"
              variant="ghost"
              @click="workspace = 'agent'">
              <Bot />
              {{ messages.workspaces.agent.label }}
            </Button>
            <Button
              v-if="availableWorkspaces.includes('api')"
              class="studio-mode-button"
              :class="{ active: workspace === 'api' }"
              size="sm"
              type="button"
              variant="ghost"
              @click="workspace = 'api'">
              <Network />
              {{ messages.workspaces.api.label }}
            </Button>
          </nav>
        </div>
        <Button v-if="!configLoading && !configError && workspace !== 'api'" class="studio-token-button" size="sm" variant="outline" @click="openTokenModal">
          <KeyRound />
          {{ accessToken ? messages.accessToken : messages.accessTokenMissing }}
        </Button>
      </header>

      <main v-if="configLoading" class="studio-status" aria-busy="true" aria-label="正在读取工作台配置">
        <LoaderCircle class="agent-spin" />
      </main>
      <main v-else-if="configError" class="studio-status" role="alert">
        {{ configError }}
      </main>
      <LibraryStudio
        v-else-if="workspace === 'library'"
        :editable-contributor-id="studioConfig?.editableContributorId ?? ''"
      />
      <AgentStudio
        v-else-if="workspace === 'agent'"
      />
      <ApiStudio
        v-else-if="workspace === 'api'"
      />

      <Dialog v-model:open="tokenModalOpen">
        <DialogContent>
          <form class="token-form" @submit.prevent="saveToken">
            <DialogHeader>
              <DialogTitle>访问令牌</DialogTitle>
              <DialogDescription class="sr-only">配置 Studio 管理接口使用的 Bearer Token</DialogDescription>
            </DialogHeader>
            <label for="access-token">Bearer Token</label>
            <Input id="access-token" v-model="tokenDraft" autocomplete="off" type="password" />
            <DialogFooter>
              <Button type="button" variant="ghost" @click="tokenModalOpen = false">取消</Button>
              <Button type="submit"><KeyRound />应用</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  </TooltipProvider>
</template>
