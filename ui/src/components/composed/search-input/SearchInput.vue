<script setup lang="ts">
import { Search, X } from '@lucide/vue'

import { Button } from '@/components/generated/shadcn/button'
import { Input } from '@/components/generated/shadcn/input'

const model = defineModel<string>({ required: true })

defineProps<{
  label: string
  placeholder?: string
}>()
</script>

<template>
  <div class="relative min-w-0">
    <Search aria-hidden="true" class="pointer-events-none absolute inset-s-2.5 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground" />
    <Input v-model="model" :aria-label="label" class="h-8 px-8 text-xs" :placeholder="placeholder" type="search" />
    <Button
      v-if="model"
      :aria-label="`清除${label}`"
      class="absolute inset-e-1 top-1/2 -translate-y-1/2"
      size="icon-xs"
      type="button"
      variant="ghost"
      @click="model = ''">
      <X />
    </Button>
  </div>
</template>
