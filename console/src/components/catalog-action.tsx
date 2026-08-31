import { forwardRef, type ButtonHTMLAttributes } from 'react'

import { useCatalog } from '@/catalog/context'

import { Button } from './button'
import { CatalogIcon } from './catalog-icon'

interface CatalogActionProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> {
  elementKey: string
  variant?: 'primary' | 'outline' | 'ghost'
}

export const CatalogAction = forwardRef<HTMLButtonElement, CatalogActionProps>(function CatalogAction(
  { elementKey, variant, ...props },
  ref,
) {
  const element = useCatalog().elementsByKey.get(elementKey)
  if (!element) return null

  return (
    <Button ref={ref} title={element.description ?? element.name} variant={variant} {...props}>
      <CatalogIcon name={element.icon} />
      <span>{element.name}</span>
    </Button>
  )
})
