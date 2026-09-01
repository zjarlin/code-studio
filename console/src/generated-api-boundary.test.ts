import { readdirSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

const sourceRoot = path.resolve(import.meta.dirname)

describe('generated API boundary', () => {
  it('keeps handwritten endpoint modules and fixed backend paths out of production source', () => {
    const files = sourceFiles(sourceRoot).filter((file) => !file.endsWith('.test.ts') && !file.endsWith('.test.tsx'))
    const apiModules = files.filter((file) => path.basename(file) === 'api.ts')
    const fixedPaths = files.flatMap((file) => {
      const matches = readFileSync(file, 'utf8').match(/['"`]\/(?:console\/api|studio\/api|agent\/|v1\/)[^'"`]*/g)
      return matches?.map((match) => `${path.relative(sourceRoot, file)}: ${match}`) ?? []
    })

    expect(apiModules).toEqual([])
    expect(fixedPaths).toEqual([])
  })
})

function sourceFiles(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const target = path.join(directory, entry.name)
    if (entry.isDirectory()) return sourceFiles(target)
    return /\.tsx?$/.test(entry.name) ? [target] : []
  })
}
