import type { SpreadsheetTemplateDocument } from './models'

export interface SpreadsheetTemplateHistory {
  past: SpreadsheetTemplateDocument[]
  present: SpreadsheetTemplateDocument
  future: SpreadsheetTemplateDocument[]
}

export type SpreadsheetTemplateHistoryAction =
  | { type: 'commit'; document: SpreadsheetTemplateDocument }
  | { type: 'reset'; document: SpreadsheetTemplateDocument }
  | { type: 'undo' }
  | { type: 'redo' }

export function createSpreadsheetTemplateHistory(document: SpreadsheetTemplateDocument): SpreadsheetTemplateHistory {
  return { past: [], present: document, future: [] }
}

export function spreadsheetTemplateHistoryReducer(
  state: SpreadsheetTemplateHistory,
  action: SpreadsheetTemplateHistoryAction,
): SpreadsheetTemplateHistory {
  if (action.type === 'reset') return createSpreadsheetTemplateHistory(action.document)
  if (action.type === 'commit') {
    if (action.document === state.present) return state
    return { past: [...state.past, state.present].slice(-50), present: action.document, future: [] }
  }
  if (action.type === 'undo') {
    const previous = state.past.at(-1)
    return previous ? { past: state.past.slice(0, -1), present: previous, future: [state.present, ...state.future] } : state
  }
  const next = state.future[0]
  return next ? { past: [...state.past, state.present].slice(-50), present: next, future: state.future.slice(1) } : state
}

