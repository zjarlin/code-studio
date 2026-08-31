import type { ButtonHTMLAttributes, ReactNode } from 'react'

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'outline' | 'ghost'
  children: ReactNode
}

export function Button({ className = '', variant = 'outline', ...props }: ButtonProps) {
  return <button className={`button button-${variant} ${className}`.trim()} type="button" {...props} />
}
