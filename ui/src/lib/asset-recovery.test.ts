import { describe, expect, it, vi } from 'vitest'

import { installAssetRecovery } from './asset-recovery'

describe('asset recovery', () => {
  it('reloads the page when a deployed preload asset is no longer available', () => {
    const target = new EventTarget()
    const reload = vi.fn()
    installAssetRecovery(target, reload)

    const event = new Event('vite:preloadError', { cancelable: true })
    target.dispatchEvent(event)

    expect(event.defaultPrevented).toBe(true)
    expect(reload).toHaveBeenCalledOnce()
  })
})
