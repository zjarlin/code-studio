import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { analyzeDtoReuse, listDtoValidationRules } from '@generated/openapi/client'
import type { DtoCommand } from '@generated/openapi/models'

import { CatalogIconAction } from '@/components/composed/catalog-action/catalog-icon-action'
import { requireApiData } from '@/lib/http'

export function DtoAssistancePanel({ draft }: Readonly<{ draft: DtoCommand }>) {
  const rules = useQuery({
    queryKey: ['dto-validation-rules'],
    queryFn: async () => requireApiData(await listDtoValidationRules(), 'DTO 校验规则响应缺少 data'),
  })
  const [showRules, setShowRules] = useState(false)
  const reuse = useMutation({
    mutationFn: async () => requireApiData(await analyzeDtoReuse(draft), 'DTO 复用分析响应缺少 data'),
  })

  return (
    <section className="dto-assistance-panel">
      <header className="resource-section-heading"><strong>校验与复用</strong><div className="inline-actions"><CatalogIconAction elementKey="studio.library.dto.rules" onClick={() => setShowRules((current) => !current)} /><CatalogIconAction disabled={reuse.isPending} elementKey="studio.library.dto.reuse" onClick={() => reuse.mutate()} /></div></header>
      {showRules && <div className="assistance-list">{rules.data?.map((rule) => <details key={rule.code}><summary>{rule.name} · {rule.code}</summary><p>{rule.description || rule.defaultMessage}</p><small>支持值类型：{rule.supportedValueKinds.join('、')}</small></details>)}{rules.error && <p className="form-error">{rules.error.message}</p>}</div>}
      {reuse.error && <p className="form-error">{reuse.error.message}</p>}
      {reuse.data !== undefined && <details className="assistance-result" open><summary>复用分析结果</summary><pre>{JSON.stringify(reuse.data, null, 2)}</pre></details>}
    </section>
  )
}
