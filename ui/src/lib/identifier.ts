import { pinyin } from 'pinyin-pro'

/** Convert a display name into the Studio's deterministic default identifier. */
export function toPinyinSnakeIdentifier(value: string): string {
  const words = pinyin(value.trim(), {
    type: 'all',
    toneType: 'none',
    nonZh: 'consecutive',
  }).flatMap((part) => part.result.split(/[^A-Za-z0-9]+/))
    .filter(Boolean)

  return words.join('_')
    .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
    .replace(/_+/g, '_')
    .replace(/^_|_$/g, '')
    .toLowerCase()
}

export function toResourceCodeFromClassName(className: string, suffix = ''): string {
  const normalized = className.trim()
  const baseName = suffix && normalized.endsWith(suffix)
    ? normalized.slice(0, -suffix.length)
    : normalized
  return baseName
    ? `${baseName.charAt(0).toLowerCase()}${baseName.slice(1)}`
    : ''
}

/** 将数据库物理标识确定性转换为 Kotlin 大驼峰名称。 */
export function databaseIdentifierToPascalCase(value: string): string {
  return value
    .trim()
    .split(/[^A-Za-z0-9]+/)
    .filter(Boolean)
    .flatMap((part) => part.match(/[A-Z]+(?=[A-Z][a-z]|\d|$)|[A-Z]?[a-z]+|\d+/g) ?? [])
    .map((word) => `${word.charAt(0).toUpperCase()}${word.slice(1).toLowerCase()}`)
    .join('')
}
