import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import CodeBlock from './CodeBlock.vue'

const clipboardDescriptor = Object.getOwnPropertyDescriptor(navigator, 'clipboard')
const execCommandDescriptor = Object.getOwnPropertyDescriptor(document, 'execCommand')

afterEach(() => {
  restoreProperty(navigator, 'clipboard', clipboardDescriptor)
  restoreProperty(document, 'execCommand', execCommandDescriptor)
  vi.restoreAllMocks()
})

describe('CodeBlock', () => {
  it('copies code through the Clipboard API when available', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
    const wrapper = mount(CodeBlock, { props: { content: 'const value = 1', language: 'typescript' } })

    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(writeText).toHaveBeenCalledWith('const value = 1')
    expect(wrapper.get('button').attributes('aria-label')).toBe('已复制')
  })

  it('falls back to selection copy on non-secure HTTP pages', async () => {
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: undefined })
    const execCommand = vi.fn().mockReturnValue(true)
    Object.defineProperty(document, 'execCommand', { configurable: true, value: execCommand })
    const wrapper = mount(CodeBlock, { props: { content: 'export const value = 1', language: 'typescript' } })

    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(execCommand).toHaveBeenCalledWith('copy')
    expect(wrapper.get('button').attributes('title')).toBe('已复制')
    expect(document.body.querySelector('textarea')).toBeNull()
  })
})

function restoreProperty(
  target: object,
  property: string,
  descriptor: PropertyDescriptor | undefined,
): void {
  if (descriptor) {
    Object.defineProperty(target, property, descriptor)
  } else {
    Reflect.deleteProperty(target, property)
  }
}
