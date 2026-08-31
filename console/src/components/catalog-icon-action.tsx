import type { ButtonHTMLAttributes } from 'react'

import { useCatalog } from '@/catalog/context'

import { Button } from './button'
import { CatalogIcon } from './catalog-icon'

interface CatalogIconActionProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'aria-label' | 'children' | 'title'> {
  elementKey: string
  variant?: 'primary' | 'outline' | 'ghost'
}

export function CatalogIconAction({ elementKey, variant = 'ghost', ...props }: CatalogIconActionProps) {
  const element = useCatalog().elementsByKey.get(elementKey)
  if (!element) return null
  return (
    <Button
      aria-label={element.name}
      className="button-icon"
      title={element.description ?? element.name}
      variant={variant}
      {...props}
    >
      <CatalogIcon name={element.icon} />
    </Button>
  )
}
