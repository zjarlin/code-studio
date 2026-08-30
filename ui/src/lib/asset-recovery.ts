interface AssetRecoveryTarget {
  addEventListener(type: string, listener: EventListener): void
}

export function installAssetRecovery(target: AssetRecoveryTarget, reload: () => void): void {
  target.addEventListener('vite:preloadError', (event) => {
    event.preventDefault()
    reload()
  })
}
