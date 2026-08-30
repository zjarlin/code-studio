<script setup lang="ts">
import { Plus, Trash2 } from '@lucide/vue'

import { Button } from '@/components/generated/shadcn/button'
import { Input } from '@/components/generated/shadcn/input'
import type { LsiDtoTypeDraft } from '@/types'

import { createKotlinType } from './dto-draft'

const props = withDefaults(defineProps<{
  modelValue: LsiDtoTypeDraft
  depth?: number
}>(), { depth: 0 })

const emit = defineEmits<{
  'update:modelValue': [value: LsiDtoTypeDraft]
}>()

function patch(patchValue: Partial<LsiDtoTypeDraft>): void {
  emit('update:modelValue', { ...props.modelValue, ...patchValue })
}

function patchArgument(index: number, value: LsiDtoTypeDraft): void {
  patch({ arguments: props.modelValue.arguments.map((argument, argumentIndex) => argumentIndex === index ? value : argument) })
}

function addArgument(): void {
  patch({ arguments: [...props.modelValue.arguments, createKotlinType()] })
}

function removeArgument(index: number): void {
  patch({ arguments: props.modelValue.arguments.filter((_, argumentIndex) => argumentIndex !== index) })
}
</script>

<template>
  <div class="kotlin-type-editor" :data-depth="depth">
    <div class="kotlin-type-row">
      <Input
        :aria-label="depth === 0 ? 'Kotlin 全限定类型' : '泛型参数全限定类型'"
        :model-value="modelValue.qualifiedName"
        placeholder="kotlin.collections.List"
        @update:model-value="patch({ qualifiedName: String($event) })"
      />
      <label v-if="depth > 0" class="kotlin-nullable-toggle">
        <input :checked="modelValue.nullable" type="checkbox" @change="patch({ nullable: ($event.target as HTMLInputElement).checked })">
        <span>可空</span>
      </label>
      <Button aria-label="添加泛型参数" size="icon-sm" type="button" variant="outline" @click="addArgument">
        <Plus />
      </Button>
    </div>
    <div v-if="modelValue.arguments.length" class="kotlin-type-arguments">
      <div v-for="(argument, index) in modelValue.arguments" :key="index" class="kotlin-type-argument">
        <KotlinTypeEditor :depth="depth + 1" :model-value="argument" @update:model-value="patchArgument(index, $event)" />
        <Button aria-label="删除泛型参数" size="icon-sm" type="button" variant="ghost" @click="removeArgument(index)">
          <Trash2 />
        </Button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.kotlin-type-editor {
  display: grid;
  gap: 6px;
  min-width: 280px;
}

.kotlin-type-row,
.kotlin-type-argument {
  display: flex;
  align-items: center;
  gap: 6px;
}

.kotlin-type-row > :first-child,
.kotlin-type-argument > :first-child {
  min-width: 0;
  flex: 1;
}

.kotlin-type-arguments {
  display: grid;
  gap: 6px;
  padding-left: 14px;
  border-left: 1px solid var(--border);
}

.kotlin-nullable-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--muted-foreground);
  font-size: 11px;
  white-space: nowrap;
}
</style>
