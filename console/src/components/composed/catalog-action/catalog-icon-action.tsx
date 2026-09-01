import type { ButtonHTMLAttributes } from 'react'

import { useCatalog } from '@/catalog/context'
import { Button } from '@/components/generated/shadcn/button'
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/generated/shadcn/tooltip'

import { CatalogIcon } from '../catalog-icon/catalog-icon'

interface CatalogIconActionProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'aria-label' | 'children' | 'title'> {
  elementKey: string
  variant?: 'primary' | 'outline' | 'ghost' | 'destructive'
}

export function CatalogIconAction({ elementKey, type = 'button', variant = 'ghost', ...props }: CatalogIconActionProps) {
  const element = useCatalog().elementsByKey.get(elementKey)
  if (!element) return null
  const description = element.description ?? element.name
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            aria-label={element.name}
            size="icon-sm"
            type={type}
            variant={variant === 'primary' ? 'default' : variant}
            {...props}
          >
            <CatalogIcon name={element.icon} />
          </Button>
        </TooltipTrigger>
        <TooltipContent>{description}</TooltipContent>
      </Tooltip>
    </TooltipProvider>
  )
}
