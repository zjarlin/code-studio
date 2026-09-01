import ts from 'typescript'
import { describe, expect, it } from 'vitest'

import { parseCatalogEntries } from '@/catalog/catalog'

const sourceModules = import.meta.glob<string>([
  '../report-designer/**/*.tsx',
  '../report-library/**/*.tsx',
  '../spreadsheet-template-designer/**/*.tsx',
], { eager: true, import: 'default', query: '?raw' })
const catalogModules = import.meta.glob<unknown>([
  '../report-designer/catalog.convention.json',
  '../report-library/catalog.convention.json',
  '../spreadsheet-template-designer/catalog.convention.json',
], { eager: true, import: 'default' })

describe('report element catalog', () => {
  it('matches every catalog action literal with convention metadata', () => {
    const usedKeys = new Set(Object.entries(sourceModules).flatMap(([path, source]) => scanElementKeys(path, source)))
    const catalogKeys = Object.values(catalogModules)
      .flatMap(parseCatalogEntries)
      .filter((entry) => entry.kind === 'ELEMENT')
      .map((entry) => entry.elementKey!)
      .sort()

    expect([...usedKeys].sort()).toEqual(catalogKeys)
  })
})

function scanElementKeys(path: string, source: string): string[] {
  const file = ts.createSourceFile(path, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
  const keys: string[] = []
  const visit = (node: ts.Node) => {
    if (isElementKeyOwner(node)) collectStringLiterals(node, keys)
    ts.forEachChild(node, visit)
  }
  visit(file)
  return keys
}

function isElementKeyOwner(node: ts.Node): boolean {
  if (ts.isJsxAttribute(node)) return node.name.getText() === 'elementKey'
  return ts.isPropertyAssignment(node) && node.name.getText() === 'elementKey'
}

function collectStringLiterals(node: ts.Node, keys: string[]): void {
  if (ts.isStringLiteralLike(node) && /^(studio\.report-designer|studio\.spreadsheet-templates|reports\.library)\./.test(node.text)) {
    keys.push(node.text)
  }
  ts.forEachChild(node, (child) => collectStringLiterals(child, keys))
}
