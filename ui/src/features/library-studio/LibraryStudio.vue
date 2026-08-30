<script setup lang="ts">
import { Boxes, Braces, CheckCircle2, ChevronDown, ChevronRight, Clock3, Code, Database, Eye, FileCode2, Folder, FolderOpen, GitBranch, LayoutDashboard, Library as LibraryIcon, ListTree, Plus, RefreshCw, Save, Search, Trash2 } from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'
import type { Component } from 'vue'

import IconButton from '@/components/composed/icon-button/IconButton.vue'
import { Badge } from '@/components/generated/shadcn/badge'
import { Button } from '@/components/generated/shadcn/button'
import { Checkbox } from '@/components/generated/shadcn/checkbox'
import { DropdownMenu, DropdownMenuContent, DropdownMenuGroup, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/generated/shadcn/dropdown-menu'
import { Field, FieldDescription, FieldGroup, FieldLabel, FieldLegend, FieldSet } from '@/components/generated/shadcn/field'
import { Input } from '@/components/generated/shadcn/input'
import { Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectTrigger, SelectValue } from '@/components/generated/shadcn/select'
import { Switch } from '@/components/generated/shadcn/switch'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/generated/shadcn/tabs'
import { LowcodeApi } from '@/lowcode-api'
import type {
  ApplicationIdentityMode,
  ConventionFileKind,
  LibraryDefinitionDraft,
  LibraryKind,
  LowcodeValidationResult,
  LsiApplicationFeature,
  LsiLibraryDefinition,
  LsiLibraryFeature,
} from '@/types'

import ConstantWorkspace from '../constants/ConstantWorkspace.vue'
import ConventionFileWorkspace from '../convention-files/ConventionFileWorkspace.vue'
import LibraryFeatureTable from './LibraryFeatureTable.vue'
import LibraryPreview from './LibraryPreview.vue'
import LibraryQueryTable from './LibraryQueryTable.vue'
import LibraryResourceWorkspace from './LibraryResourceWorkspace.vue'
import { applyLibraryDisplayName, createLibraryDraft, createLibraryFeature, featurePackageName, libraryViewToDraft, normalizeLibraryDraft, selectedLibraryFeatures } from './library-draft'
import type { LibraryDataTab, LibraryIndexedResource } from './library-resource-index'
import { useLibraryResourceCatalog } from './use-library-resource-catalog'

type LibraryTab = 'overview' | 'features' | LibraryDataTab | 'constants' | 'preview'
type CreatableTab = LibraryDataTab | 'constants'

const props = defineProps<{
  editableContributorId: string
}>()

interface ResourceTabDefinition {
  value: CreatableTab
  label: string
  icon: Component
}

interface ResourceCreateDefinition extends ResourceTabDefinition {
  conventionKind?: ConventionFileKind
}

const api = new LowcodeApi()
const catalog = useLibraryResourceCatalog(api)
const libraries = catalog.libraries
const draft = ref<LibraryDefinitionDraft>(createLibraryDraft())
const selectedId = ref<number | string>()
const selectedFeatureId = ref<number | string>()
const expanded = ref(true)
const editing = ref(false)
const search = ref('')
const busy = ref(false)
const dirty = ref(false)
const notice = ref('')
const noticeTone = ref<'error' | 'success'>('success')
const validation = ref<LowcodeValidationResult>()
const activeTab = ref<LibraryTab>('overview')
const conventionFileCreateKind = ref<ConventionFileKind>('SERVICE')
const createRequests = ref<Record<CreatableTab, number>>({
  models: 0,
  queries: 0,
  dtos: 0,
  services: 0,
  constants: 0,
})

const resourceTabs: ResourceTabDefinition[] = [
  { value: 'models', label: '模型', icon: Database },
  { value: 'queries', label: '查询', icon: GitBranch },
  { value: 'dtos', label: 'DTO', icon: Braces },
  { value: 'services', label: '约定文件', icon: FileCode2 },
  { value: 'constants', label: '常量', icon: Code },
]

const resourceCreateItems: ResourceCreateDefinition[] = [
  { value: 'models', label: '模型', icon: Database },
  { value: 'queries', label: '查询', icon: GitBranch },
  { value: 'dtos', label: 'DTO', icon: Braces },
  { value: 'services', label: 'Service', icon: FileCode2, conventionKind: 'SERVICE' },
  { value: 'services', label: '定时任务', icon: Clock3, conventionKind: 'SCHEDULED_JOB' },
  { value: 'constants', label: '常量', icon: Code },
]

const selectedLibrary = computed(() => libraries.value.find(({ id }) => String(id) === String(selectedId.value)))
const readOnly = computed(() => selectedLibrary.value != null
  && selectedLibrary.value.spec.contributorId !== props.editableContributorId)
const features = computed(() => selectedLibrary.value?.features ?? [])
const selectedFeature = computed(() => features.value.find(({ id }) => String(id) === String(selectedFeatureId.value)))
const contributorId = computed(() => draft.value.spec.contributorId)
const constantFeatures = computed<LsiApplicationFeature[]>(() => selectedLibraryFeatures(
  features.value,
  selectedFeatureId.value,
).map((feature) => ({
  featureId: feature.id,
  featureCode: feature.featureCode,
  name: feature.name,
  description: feature.description,
  packageName: featurePackageName(draft.value.spec, feature),
  contributorId: contributorId.value,
  modelCodes: [],
  dtoCodes: [],
  contractCodes: [],
})))
const searchMatches = computed(() => catalog.matches(search.value))
const tabCounts = computed(() => selectedLibrary.value
  ? catalog.countsFor(selectedLibrary.value, selectedFeatureId.value)
  : { features: 0, models: 0, queries: 0, dtos: 0, services: 0, constants: 0 })
const resourceCreateLabel = computed(() => {
  if (selectedId.value === undefined) return '资源（请先选择 Library）'
  if (readOnly.value) return '资源（依赖 Library 只读）'
  if (features.value.length === 0) return '资源（请先创建功能目录）'
  return '资源'
})
const visibleResourceTabs = computed(() => resourceTabs.filter(({ value }) => tabCounts.value[value] > 0))
const visibleLibraries = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  if (!keyword) return libraries.value
  return libraries.value.filter((library) =>
    `${library.displayName} ${library.code} ${library.spec.description ?? ''}`.toLowerCase().includes(keyword)
    || library.features.some((feature) =>
      `${feature.name} ${feature.featureCode} ${featurePackageName(library.spec, feature)}`.toLowerCase().includes(keyword))
    || searchMatches.value.some((match) => String(match.libraryId) === String(library.id)),
  )
})

onMounted(() => run(catalog.loadCatalog))

function matchesFor(library: LsiLibraryDefinition): LibraryIndexedResource[] {
  return searchMatches.value
    .filter((match) => String(match.libraryId) === String(library.id))
    .slice(0, 12)
}

async function selectLibrary(library: LsiLibraryDefinition): Promise<void> {
  if (String(library.id) === String(selectedId.value)) {
    expanded.value = !expanded.value
    selectedFeatureId.value = undefined
    await run(() => catalog.loadLibraryContext(library))
    return
  }
  if (dirty.value && !window.confirm('放弃当前未保存的修改？')) return
  await run(() => activateLibrary(library, 'overview'))
}

async function openSearchMatch(library: LsiLibraryDefinition, match: LibraryIndexedResource): Promise<void> {
  if (dirty.value && String(library.id) !== String(selectedId.value)
    && !window.confirm('放弃当前未保存的修改？')) return
  await run(() => activateLibrary(library, match.tab, match.featureId))
}

async function activateLibrary(
  library: LsiLibraryDefinition,
  tab: LibraryTab,
  featureId?: number | string,
): Promise<void> {
  draft.value = libraryViewToDraft(library)
  selectedId.value = library.id
  selectedFeatureId.value = featureId
  activeTab.value = tab
  expanded.value = true
  editing.value = true
  dirty.value = false
  await catalog.loadLibraryContext(library, featureId)
}

function createLibrary(): void {
  draft.value = createLibraryDraft()
  draft.value.spec.contributorId = props.editableContributorId
  selectedId.value = undefined
  selectedFeatureId.value = undefined
  activeTab.value = 'overview'
  editing.value = true
  dirty.value = false
}

function updateDisplayName(displayName: string): void {
  draft.value = applyLibraryDisplayName(draft.value, displayName)
}

async function refresh(): Promise<void> {
  if (dirty.value && !window.confirm('放弃当前未保存的修改并刷新？')) return
  await run(async () => {
    await catalog.loadCatalog()
    const selected = selectedLibrary.value
    if (selected) {
      draft.value = libraryViewToDraft(selected)
      await catalog.loadLibraryContext(selected, selectedFeatureId.value)
    }
    dirty.value = false
  })
}

async function validateLibrary(): Promise<void> {
  validation.value = await api.validateLibrary(normalizeLibraryDraft(draft.value))
  notice.value = validation.value.valid ? 'Library 定义通过校验' : validation.value.errors.join('；')
  noticeTone.value = validation.value.valid ? 'success' : 'error'
}

async function saveLibrary(): Promise<void> {
  if (readOnly.value) return
  const command = normalizeLibraryDraft(draft.value)
  validation.value = await api.validateLibrary(command)
  if (!validation.value.valid) {
    notice.value = validation.value.errors.join('；')
    noticeTone.value = 'error'
    return
  }
  const result = await api.saveLibrary(command)
  const id = command.id ?? result
  await catalog.loadCatalog()
  if (typeof id === 'number' || typeof id === 'string') {
    selectedId.value = id
    const selected = selectedLibrary.value
    if (selected) {
      draft.value = libraryViewToDraft(selected)
      await catalog.loadLibraryContext(selected)
    }
  }
  dirty.value = false
  notice.value = 'Library 已保存'
  noticeTone.value = 'success'
}

async function deleteLibrary(): Promise<void> {
  if (readOnly.value) return
  const id = selectedId.value
  if (id === undefined || !window.confirm(`删除 Library“${draft.value.displayName}”？`)) return
  await run(async () => {
    await api.deleteLibrary(id)
    await catalog.loadCatalog()
    draft.value = createLibraryDraft()
    selectedId.value = undefined
    editing.value = false
  })
}

async function addFeature(): Promise<void> {
  if (readOnly.value) return
  const id = selectedId.value
  if (id === undefined) throw new Error('请先保存 Library')
  const saved = await api.saveLibraryFeature(createLibraryFeature(id, features.value))
  await catalog.loadCatalog()
  selectedFeatureId.value = saved.id
  activeTab.value = 'features'
  notice.value = `功能“${saved.name}”已创建`
  noticeTone.value = 'success'
}

async function saveFeatureRow(feature: Omit<LsiLibraryFeature, 'id'> & { id?: number | string }): Promise<LsiLibraryFeature> {
  if (readOnly.value) throw new Error('依赖 Library 只读')
  const saved = await api.saveLibraryFeature(feature)
  await catalog.loadCatalog()
  selectedFeatureId.value = saved.id
  notice.value = `功能“${saved.name}”已保存`
  noticeTone.value = 'success'
  return saved
}

async function selectFeature(featureId: number | string): Promise<void> {
  selectedFeatureId.value = featureId
  const library = selectedLibrary.value
  if (library) await run(() => catalog.loadLibraryContext(library, featureId))
}

async function deleteFeature(feature: LsiLibraryFeature): Promise<void> {
  if (readOnly.value) return
  if (!window.confirm(`删除功能目录“${feature.name}”？`)) return
  await run(async () => {
    await api.deleteLibraryFeature(feature.id)
    if (String(selectedFeatureId.value) === String(feature.id)) selectedFeatureId.value = undefined
    await catalog.loadCatalog()
  })
}

function identityModeSelected(mode: ApplicationIdentityMode): boolean {
  return draft.value.spec.supportedIdentityModes.includes(mode)
}

function toggleIdentityMode(mode: ApplicationIdentityMode, selected: boolean | 'indeterminate'): void {
  const modes = draft.value.spec.supportedIdentityModes.filter((value) => value !== mode)
  if (selected === true) modes.push(mode)
  draft.value.spec.supportedIdentityModes = modes
  markDirty()
}

function setKind(value: unknown): void {
  draft.value.spec.kind = String(value) as LibraryKind
  markDirty()
}

function setRuntimeDependencies(value: string | number): void {
  draft.value.spec.runtimeDependencies = String(value).split(',').map((item) => item.trim()).filter(Boolean)
  markDirty()
}

function setContributorId(value: string | number): void {
  draft.value.spec.contributorId = String(value).trim()
  markDirty()
}

function createInTab(tab: CreatableTab, conventionKind?: ConventionFileKind): void {
  if (readOnly.value || !selectedLibrary.value || features.value.length === 0) {
    return
  }
  if (tab === 'services') {
    conventionFileCreateKind.value = conventionKind ?? 'SERVICE'
  }
  activeTab.value = tab
  createRequests.value = {
    ...createRequests.value,
    [tab]: createRequests.value[tab] + 1,
  }
}

function handleConstantCount(count: number): void {
  const library = selectedLibrary.value
  if (library) {
    catalog.setConstantCount(library, selectedFeatureId.value, count)
  }
}

async function refreshResources(): Promise<void> {
  await run(async () => {
    await catalog.loadCatalog()
    const library = selectedLibrary.value
    if (library) {
      await catalog.loadLibraryContext(library, selectedFeatureId.value)
    }
  })
}

function markDirty(): void {
  dirty.value = true
  validation.value = undefined
}

async function run(action: () => Promise<unknown>): Promise<void> {
  busy.value = true
  notice.value = ''
  try {
    await action()
  } catch (cause) {
    notice.value = cause instanceof Error ? cause.message : '操作失败'
    noticeTone.value = 'error'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="studio-main contributor-studio-main library-studio-main">
    <aside class="model-sidebar application-sidebar">
      <div class="sidebar-toolbar">
        <div class="studio-search-field"><Search /><Input v-model="search" type="search" placeholder="搜索库、功能、接口或路径" /></div>
        <DropdownMenu>
          <DropdownMenuTrigger as-child>
            <Button aria-label="新增资源" size="sm" type="button" variant="outline">
              <Plus />
              <span>新增</span>
              <ChevronDown />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start" class="library-create-menu">
            <DropdownMenuLabel>新增</DropdownMenuLabel>
            <DropdownMenuGroup>
              <DropdownMenuItem @select="createLibrary"><LibraryIcon />Library</DropdownMenuItem>
              <DropdownMenuItem :disabled="readOnly || selectedId === undefined" @select="run(addFeature)"><ListTree />功能目录</DropdownMenuItem>
            </DropdownMenuGroup>
            <DropdownMenuSeparator />
            <DropdownMenuLabel>{{ resourceCreateLabel }}</DropdownMenuLabel>
            <DropdownMenuGroup>
              <DropdownMenuItem
                v-for="item in resourceCreateItems"
                :key="`${item.value}:${item.conventionKind ?? 'default'}`"
                :disabled="readOnly || selectedId === undefined || features.length === 0 || (item.value === 'queries' && tabCounts.models === 0)"
                @select="createInTab(item.value, item.conventionKind)"
              >
                <component :is="item.icon" />{{ item.label }}
              </DropdownMenuItem>
            </DropdownMenuGroup>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
      <div class="application-tree" role="tree" aria-label="Library 列表">
        <template v-for="library in visibleLibraries" :key="library.id">
          <button
            class="model-item application-tree-item"
            :class="{ active: String(library.id) === String(selectedId) && !selectedFeature }"
            :aria-expanded="String(library.id) === String(selectedId) && expanded"
            role="treeitem"
            type="button"
            @click="selectLibrary(library)"
          >
            <ChevronDown v-if="String(library.id) === String(selectedId) && expanded" />
            <ChevronRight v-else />
            <FolderOpen v-if="String(library.id) === String(selectedId) && expanded" />
            <Folder v-else />
            <span class="model-copy"><strong>{{ library.displayName }}</strong><span>{{ library.spec.description || library.spec.contributorId }}</span></span>
            <Badge :variant="library.spec.applicationSelectable ? 'secondary' : 'outline'">{{ library.spec.kind === 'BUILT_IN' ? '内置' : '业务' }}</Badge>
          </button>
          <div v-if="search.trim() && matchesFor(library).length" class="library-search-results" role="group">
            <button
              v-for="match in matchesFor(library)"
              :key="`${match.tab}:${match.resourceKey}:${match.detail}`"
              class="library-search-result"
              type="button"
              @click="openSearchMatch(library, match)"
            >
              <Search />
              <span><strong>{{ match.label }}</strong><small>{{ match.detail }}</small></span>
            </button>
          </div>
          <div v-if="String(library.id) === String(selectedId) && expanded" class="application-feature-tree" role="group">
            <div v-for="feature in features" :key="feature.id" class="application-feature-row" :class="{ active: String(selectedFeatureId) === String(feature.id) }" :style="{ '--feature-depth': feature.featureCode.split('.').length - 1 }">
              <button class="application-feature-item" type="button" @click="selectFeature(feature.id)"><Boxes /><span><strong>{{ feature.name }}</strong><small>{{ feature.description || featurePackageName(draft.spec, feature) }}</small></span></button>
              <IconButton v-if="!readOnly" :icon="Trash2" :label="`删除${feature.name}`" variant="danger" @click="deleteFeature(feature)" />
            </div>
            <button v-if="!readOnly" class="application-add-feature" type="button" @click="run(addFeature)"><Plus />新建功能目录</button>
          </div>
        </template>
        <div v-if="!busy && visibleLibraries.length === 0" class="empty-list">暂无 Library</div>
      </div>
    </aside>

    <section v-if="editing" class="model-workspace application-workspace library-workspace">
      <header class="workspace-toolbar">
        <div class="workspace-title"><strong>{{ draft.displayName || '新 Library' }}</strong><span>{{ selectedFeature?.name || contributorId || '未保存' }}</span><Badge v-if="readOnly" variant="outline">只读依赖</Badge></div>
        <div class="application-actions" role="group" aria-label="Library 定义操作">
          <IconButton :disabled="busy" :icon="RefreshCw" label="刷新 Library" @click="refresh" />
          <IconButton :disabled="busy || readOnly || selectedId === undefined" :icon="Trash2" label="删除 Library" variant="danger" @click="deleteLibrary" />
          <Button :disabled="busy" size="sm" type="button" variant="outline" @click="run(validateLibrary)"><CheckCircle2 />校验 Library</Button>
          <Button :disabled="busy || readOnly" size="sm" type="button" @click="run(saveLibrary)"><Save />保存 Library</Button>
        </div>
      </header>
      <div v-if="notice" class="studio-notice" :class="noticeTone">{{ notice }}</div>

      <Tabs v-model="activeTab" class="application-feature-tabs library-level-tabs">
        <TabsList aria-label="Library 元数据">
          <TabsTrigger value="overview"><LayoutDashboard /><span>概览</span></TabsTrigger>
          <TabsTrigger v-if="features.length" value="features"><ListTree /><span>功能</span></TabsTrigger>
          <TabsTrigger v-for="tab in visibleResourceTabs" :key="tab.value" :value="tab.value">
            <component :is="tab.icon" /><span>{{ tab.label }}</span>
          </TabsTrigger>
          <TabsTrigger v-if="selectedId !== undefined" value="preview"><Eye /><span>生成预览</span></TabsTrigger>
        </TabsList>

        <TabsContent v-if="activeTab === 'overview'" value="overview" class="library-tab-content">
          <div class="application-editor" @change="markDirty" @input="markDirty">
            <div class="application-editor-sections studio-definition-sections">
              <FieldSet class="application-section">
                <FieldLegend>Library 身份</FieldLegend>
                <FieldDescription>Library 是功能与代码资源的版本化单一事实源。</FieldDescription>
                <FieldGroup class="application-basic-fields">
                  <Field><FieldLabel>名称</FieldLabel><Input :model-value="draft.displayName" placeholder="示例库" @update:model-value="updateDisplayName(String($event))" /></Field>
                  <Field><FieldLabel>定义版本</FieldLabel><Input v-model.number="draft.version" min="1" type="number" /></Field>
                  <Field><FieldLabel>类型</FieldLabel><Select :model-value="draft.spec.kind" @update:model-value="setKind"><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectGroup><SelectLabel>Library 类型</SelectLabel><SelectItem value="BUSINESS">业务</SelectItem><SelectItem value="BUILT_IN">内置</SelectItem></SelectGroup></SelectContent></Select></Field>
                  <Field orientation="horizontal"><FieldLabel>启用</FieldLabel><Switch :model-value="draft.status === 1" @update:model-value="draft.status = $event ? 1 : 0" /></Field>
                  <Field orientation="horizontal"><FieldLabel>允许应用选择</FieldLabel><Switch v-model="draft.spec.applicationSelectable" /></Field>
                  <Field class="application-feature-description"><FieldLabel>说明</FieldLabel><textarea v-model="draft.spec.description" rows="2" /></Field>
                </FieldGroup>
              </FieldSet>
              <FieldSet class="application-section">
                <FieldLegend>编译位置</FieldLegend>
                <FieldGroup class="application-basic-fields">
                  <Field v-if="!selectedFeature"><FieldLabel>Contributor ID</FieldLabel><Input :model-value="draft.spec.contributorId" placeholder="example-library" @update:model-value="setContributorId" /></Field>
                  <Field><FieldLabel>扫描包</FieldLabel><Input v-model="draft.spec.scanPackage" placeholder="com.example.application" /></Field>
                  <Field><FieldLabel>包名前缀</FieldLabel><Input v-model="draft.spec.packagePrefix" placeholder="com.example.application" /></Field>
                  <Field class="application-feature-description"><FieldLabel>运行依赖</FieldLabel><Input :model-value="draft.spec.runtimeDependencies.join(', ')" @update:model-value="setRuntimeDependencies" /></Field>
                </FieldGroup>
              </FieldSet>
              <FieldSet class="application-section">
                <FieldLegend>宿主约束</FieldLegend>
                <div class="library-option-grid">
                  <label><Checkbox :model-value="identityModeSelected('EXTERNAL_JWT')" @update:model-value="toggleIdentityMode('EXTERNAL_JWT', $event)" /><span>外部 JWT</span></label>
                  <label><Checkbox :model-value="identityModeSelected('LOCAL')" @update:model-value="toggleIdentityMode('LOCAL', $event)" /><span>本地身份</span></label>
                  <label><Checkbox v-model="draft.spec.dataScope.tenantScoped" /><span>租户范围</span></label>
                  <label><Checkbox v-model="draft.spec.dataScope.userScoped" /><span>用户范围</span></label>
                  <label><Checkbox v-model="draft.spec.dataScope.departmentScoped" /><span>部门范围</span></label>
                </div>
              </FieldSet>
            </div>
          </div>
        </TabsContent>
        <TabsContent v-if="activeTab === 'features'" value="features" class="library-tab-content">
          <LibraryFeatureTable :create-feature="() => createLibraryFeature(selectedId ?? 0, features)" :features="features" :package-prefix="draft.spec.packagePrefix" :save-row="saveFeatureRow" @delete="deleteFeature" @select="selectFeature" />
        </TabsContent>
        <TabsContent v-if="activeTab === 'models'" value="models" class="library-tab-content"><LibraryResourceWorkspace resource="models" :create-request="createRequests.models" :features="features" :library-spec="draft.spec" :selected-feature-id="selectedFeatureId" @changed="refreshResources" /></TabsContent>
        <TabsContent v-if="activeTab === 'queries'" value="queries" class="library-tab-content"><LibraryQueryTable :create-request="createRequests.queries" :features="features" :selected-feature-id="selectedFeatureId" @changed="refreshResources" /></TabsContent>
        <TabsContent v-if="activeTab === 'dtos'" value="dtos" class="library-tab-content"><LibraryResourceWorkspace resource="dtos" :create-request="createRequests.dtos" :features="features" :library-spec="draft.spec" :selected-feature-id="selectedFeatureId" @changed="refreshResources" /></TabsContent>
        <TabsContent v-if="activeTab === 'services'" value="services" class="library-tab-content"><ConventionFileWorkspace :create-kind="conventionFileCreateKind" :create-request="createRequests.services" :features="features" :library-spec="draft.spec" :read-only="readOnly" :selected-feature-id="selectedFeatureId" @changed="refreshResources" /></TabsContent>
        <TabsContent v-if="activeTab === 'constants'" value="constants" class="library-tab-content">
          <ConstantWorkspace v-if="constantFeatures.length" :create-request="createRequests.constants" :features="constantFeatures" :selected-feature-code="selectedFeature?.featureCode" @count-change="handleConstantCount" />
          <div v-else class="feature-workbench-empty"><Code /><strong>请先创建功能目录</strong></div>
        </TabsContent>
        <TabsContent v-if="activeTab === 'preview'" value="preview" class="library-tab-content"><LibraryPreview :feature-id="selectedFeatureId" :library-id="selectedId" /></TabsContent>
      </Tabs>
    </section>
    <div v-else class="studio-empty-workspace"><strong>选择或新建 Library</strong></div>
  </main>
</template>
