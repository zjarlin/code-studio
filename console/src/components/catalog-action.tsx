import type { ButtonHTMLAttributes } from 'react'

import { useCatalog } from '@/catalog/context'

import { Button } from './button'
import { CatalogIcon } from './catalog-icon'

interface CatalogActionProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> {
  elementKey: string
  variant?: 'primary' | 'outline' | 'ghost'
}

export function CatalogAction({ elementKey, variant, ...props }: CatalogActionProps) {
  const element = useCatalog().elementsByKey.get(elementKey)
  if (!element) return null

  return (
    <Button title={element.description ?? element.name} variant={variant} {...props}>
      <CatalogIcon name={element.icon} />
      <span>{element.name}</span>
    </Button>
  )
}
