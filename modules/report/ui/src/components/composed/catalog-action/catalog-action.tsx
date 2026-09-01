import { forwardRef, type ButtonHTMLAttributes } from 'react'

import { useCatalog } from '@/catalog/context'
import { Button } from '@platform/ui/components/generated/shadcn/button'

import { CatalogIcon } from '../catalog-icon/catalog-icon'

type CatalogActionVariant = 'primary' | 'outline' | 'ghost' | 'destructive'

interface CatalogActionProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> {
  elementKey: string
  variant?: CatalogActionVariant
}

export const CatalogAction = forwardRef<HTMLButtonElement, CatalogActionProps>(function CatalogAction(
  { elementKey, type = 'button', variant = 'outline', ...props },
  ref,
) {
  const element = useCatalog().elementsByKey.get(elementKey)
  if (!element) return null

  return (
    <Button
      ref={ref}
      title={element.description ?? element.name}
      type={type}
      variant={variant === 'primary' ? 'default' : variant}
      {...props}
    >
      <CatalogIcon data-icon="inline-start" name={element.icon} />
      <span>{element.name}</span>
    </Button>
  )
})
