import { useEffect, useState } from 'react'

import { Button } from '@/components/button'

interface AuthDialogProps {
  onChange: (value: string) => void
  onClose: () => void
  open: boolean
  token: string
}

export function AuthDialog({ onChange, onClose, open, token }: AuthDialogProps) {
  const [draft, setDraft] = useState(token)

  useEffect(() => {
    if (open) setDraft(token)
  }, [open, token])

  if (!open) return null
  return (
    <div className="dialog-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section aria-labelledby="api-auth-title" aria-modal="true" className="dialog" role="dialog">
        <header>
          <h2 id="api-auth-title">临时 Bearer 鉴权</h2>
          <p>仅保存在当前页面内存中，刷新后自动清除。留空时继续使用宿主登录态。</p>
        </header>
        <form onSubmit={(event) => {
          event.preventDefault()
          onChange(draft.trim())
          onClose()
        }}>
          <label>
            Bearer Token
            <input
              autoComplete="off"
              autoFocus
              onChange={(event) => setDraft(event.target.value)}
              placeholder="输入 Token，不含 Bearer 前缀"
              type="password"
              value={draft}
            />
          </label>
          <footer>
            <Button onClick={() => {
              onChange('')
              onClose()
            }} variant="ghost">清除</Button>
            <Button onClick={onClose}>取消</Button>
            <Button type="submit" variant="primary">应用</Button>
          </footer>
        </form>
      </section>
    </div>
  )
}
