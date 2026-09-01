import type { ModelCommand, RelationCommand, RelationCommandDissociateAction, RelationCommandRelationType } from '@generated/openapi/models'
import { RelationCommandDissociateAction as DissociateActions, RelationCommandRelationType as RelationTypes } from '@generated/openapi/models'

import { CatalogIconAction } from '@/components/composed/catalog-action/catalog-icon-action'
import { Input } from '@platform/ui/components/generated/shadcn/input'

export function RelationEditor({ editable, models, relations, onChange }: Readonly<{
  editable: boolean
  models: ModelCommand[]
  relations: RelationCommand[]
  onChange: (relations: RelationCommand[]) => void
}>) {
  function update(index: number, patch: Partial<RelationCommand>) {
    onChange(normalize(relations.map((relation, relationIndex) => relationIndex === index ? { ...relation, ...patch } : relation)))
  }
  return (
    <section className="relation-editor">
      <header className="resource-section-heading"><strong>关联</strong>{editable && <CatalogIconAction elementKey="studio.library.model.relation.create" onClick={() => onChange(normalize([...relations, createRelation(relations.length, models[0])]))} />}</header>
      {relations.map((relation, index) => <div className="relation-definition" key={relation.id ?? `${relation.relationCode}:${index}`}>
        <div className="resource-table" role="table" aria-label="模型关联">
          <div className="resource-row resource-row-head relation-row" role="row"><span>代码</span><span>名称</span><span>类型</span><span>目标模型</span><span>操作</span></div>
          <div className="resource-row relation-row" role="row">
            <Input aria-label="关联代码" disabled={!editable} onChange={(event) => update(index, { relationCode: event.target.value })} required value={relation.relationCode} />
            <Input aria-label="关联名称" disabled={!editable} onChange={(event) => update(index, { label: event.target.value })} required value={relation.label} />
            <select aria-label="关联类型" disabled={!editable} onChange={(event) => update(index, { relationType: event.target.value as RelationCommandRelationType })} value={relation.relationType}>{Object.values(RelationTypes).map((type) => <option key={type} value={type}>{type}</option>)}</select>
            <select aria-label="关联目标模型" disabled={!editable} onChange={(event) => { const target = models.find((model) => String(model.id) === event.target.value); update(index, { targetModelId: target?.id ?? null, targetModelCode: target?.modelCode ?? null }) }} value={relation.targetModelId == null ? '' : String(relation.targetModelId)}><option value="">使用目标模型代码</option>{models.filter((model) => model.id != null).map((model) => <option key={model.id} value={String(model.id)}>{model.name} ({model.modelCode})</option>)}</select>
            {editable && <CatalogIconAction elementKey="studio.library.model.relation.delete" onClick={() => onChange(normalize(relations.filter((_, relationIndex) => relationIndex !== index)))} variant="destructive" />}
          </div>
        </div>
        <div className="relation-detail-grid">
          <label>目标模型代码<Input aria-label="目标模型代码" disabled={!editable} onChange={(event) => update(index, { targetModelCode: event.target.value || null, targetModelId: null })} value={relation.targetModelCode ?? ''} /></label>
          <label>Join 列<Input aria-label="关联 Join 列" disabled={!editable} onChange={(event) => update(index, { joinColumn: event.target.value || null })} value={relation.joinColumn ?? ''} /></label>
          <label>Mapped By<Input aria-label="关联 Mapped By" disabled={!editable} onChange={(event) => update(index, { mappedBy: event.target.value || null })} value={relation.mappedBy ?? ''} /></label>
          <label>级联策略<select aria-label="关联级联策略" disabled={!editable} onChange={(event) => update(index, { dissociateAction: event.target.value as RelationCommandDissociateAction })} value={relation.dissociateAction ?? DissociateActions.NONE}>{Object.values(DissociateActions).map((action) => <option key={action} value={action}>{action}</option>)}</select></label>
        </div>
      </div>)}
      {!relations.length && <div className="resource-table-empty">尚未配置关联</div>}
    </section>
  )
}

function createRelation(index: number, target?: ModelCommand): RelationCommand {
  return { orderNo: index + 1, relationCode: `relation${index + 1}`, label: `关联 ${index + 1}`, relationType: RelationTypes.MANY_TO_ONE, targetModelId: target?.id ?? null, targetModelCode: target?.modelCode ?? null, joinColumn: null, mappedBy: null, joinTable: null, joinTableJoinColumn: null, joinTableInverseColumn: null, joinTableFilterColumn: null, joinTableFilterValues: [], dissociateAction: DissociateActions.NONE, required: false, createWritable: true, updateWritable: true, listVisible: true, formVisible: true }
}

function normalize(relations: RelationCommand[]): RelationCommand[] {
  return relations.map((relation, index) => ({ ...relation, orderNo: index + 1 }))
}
