<script setup lang="ts">
import type { Component } from 'vue'

import { Button } from '@/components/generated/shadcn/button'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/generated/shadcn/tooltip'

defineOptions({ inheritAttrs: false })

withDefaults(defineProps<{
  label: string
  icon: Component
  disabled?: boolean
  tooltip?: boolean
  variant?: 'ghost' | 'danger'
}>(), {
  disabled: false,
  tooltip: true,
  variant: 'ghost',
})
</script>

<template>
  <Tooltip>
    <TooltipTrigger as-child>
      <Button
        v-bind="$attrs"
        :aria-label="label"
        :class="{ 'icon-button-danger': variant === 'danger' }"
        :disabled="disabled"
        size="icon-sm"
        type="button"
        :variant="variant === 'danger' ? 'ghost' : variant">
        <component :is="icon" />
      </Button>
    </TooltipTrigger>
    <TooltipContent>{{ label }}</TooltipContent>
  </Tooltip>
</template>
