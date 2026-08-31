import {
  Blocks,
  BookOpen,
  Bot,
  Circle,
  Library,
  MessageSquarePlus,
  MessagesSquare,
  Plus,
  RefreshCw,
  Save,
  Settings,
  type LucideIcon,
} from 'lucide-react'

const icons: Readonly<Record<string, LucideIcon>> = {
  blocks: Blocks,
  'book-open': BookOpen,
  bot: Bot,
  library: Library,
  'message-square-plus': MessageSquarePlus,
  'messages-square': MessagesSquare,
  plus: Plus,
  'refresh-cw': RefreshCw,
  save: Save,
  settings: Settings,
}

export function CatalogIcon({ name, ...props }: Readonly<{ name?: string | null; className?: string }>) {
  const Icon = (name && icons[name]) || Circle
  return <Icon aria-hidden="true" {...props} />
}
