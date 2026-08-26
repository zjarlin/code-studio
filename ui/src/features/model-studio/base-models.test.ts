import { describe, expect, it } from 'vitest'

import {
  normalizeBaseModels,
  resolvedBaseModelProperties,
  toggleBaseModelSelection,
} from './base-models'

describe('base models', () => {
  it('keeps BaseEntity as the default and removes its duplicate components', () => {
    expect(normalizeBaseModels([], 'DEFAULT')).toEqual(['BASE_ENTITY'])
    expect(normalizeBaseModels(['BASE_ENTITY', 'CREATE_TIME', 'AUDIT'], 'INHERITED'))
      .toEqual(['BASE_ENTITY'])
  })

  it('keeps BaseNode as the single owner of its component properties', () => {
    expect(normalizeBaseModels(['NODE', 'BASE_ENTITY', 'SNOWFLAKE_ID', 'NAMESPACE', 'NAMED'], 'INHERITED'))
      .toEqual(['NODE'])

    const namedNode = toggleBaseModelSelection(['NODE'], 'NAMED', true)

    expect(namedNode).toEqual(['NAMED'])
  })

  it('supports an append-only entity with an id and create time', () => {
    const withoutDefault = toggleBaseModelSelection(['BASE_ENTITY'], 'SNOWFLAKE_ID', true)
    const appendOnly = toggleBaseModelSelection(withoutDefault, 'CREATE_TIME', true)
    const properties = resolvedBaseModelProperties({
      baseMode: 'INHERITED',
      baseModels: appendOnly,
      inheritedProperties: [],
    })

    expect(appendOnly).toEqual(['SNOWFLAKE_ID', 'CREATE_TIME'])
    expect(properties.map((property) => property.name)).toEqual(['id', 'createTime'])
  })
})
