import { useEffect, useState } from 'react'

import { Button } from '@/components/generated/shadcn/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/generated/shadcn/dialog'
import { Field, FieldLabel } from '@/components/generated/shadcn/field'
import { Input } from '@/components/generated/shadcn/input'

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

  return (
    <Dialog onOpenChange={(nextOpen) => !nextOpen && onClose()} open={open}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>临时 Bearer 鉴权</DialogTitle>
          <DialogDescription>仅保存在当前页面内存中，刷新后自动清除。留空时继续使用宿主登录态。</DialogDescription>
        </DialogHeader>
        <form onSubmit={(event) => {
          event.preventDefault()
          onChange(draft.trim())
          onClose()
        }}>
          <Field>
            <FieldLabel htmlFor="api-bearer-token">Bearer Token</FieldLabel>
            <Input
              autoComplete="off"
              autoFocus
              id="api-bearer-token"
              onChange={(event) => setDraft(event.target.value)}
              placeholder="输入 Token，不含 Bearer 前缀"
              type="password"
              value={draft}
            />
          </Field>
          <DialogFooter>
            <Button onClick={() => {
              onChange('')
              onClose()
            }} type="button" variant="ghost">清除</Button>
            <Button onClick={onClose} type="button" variant="outline">取消</Button>
            <Button type="submit">应用</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
