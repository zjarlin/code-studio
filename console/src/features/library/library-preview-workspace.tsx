import { useQuery } from '@tanstack/react-query'
import { FileCode2 } from 'lucide-react'
import { useState } from 'react'

import { CatalogIconAction } from '@/components/composed/catalog-action/catalog-icon-action'
import { QueryState } from '@/components/composed/query-state/query-state'

import { fetchLibraryPreview } from './commands'

export function LibraryPreviewWorkspace({ libraryId, featureId, featureName }: Readonly<{
  libraryId: number
  featureId?: number
  featureName?: string
}>) {
  const preview = useQuery({
    queryKey: ['library-preview', libraryId, featureId],
    enabled: false,
    queryFn: () => fetchLibraryPreview(libraryId, featureId),
  })
  const [selectedPath, setSelectedPath] = useState('')
  const files = preview.data?.files ?? []
  const selected = files.find((file) => file.filePath === selectedPath) ?? files[0]

  return (
    <section aria-label="生成预览" className="library-preview-workspace">
      <header className="resource-section-heading">
        <div><strong>生成预览</strong><span>{featureName ? `${featureName} 的生成文件` : '当前 Library 全部生成文件'}</span></div>
        <CatalogIconAction
          disabled={preview.isFetching}
          elementKey="studio.library.preview.refresh"
          onClick={() => { void preview.refetch() }}
        />
      </header>
      <QueryState error={preview.error} pending={preview.isFetching}>
        {files.length ? (
          <div className="library-preview-grid">
            <nav aria-label="生成文件">
              {files.map((file) => (
                <button className={file.filePath === selected?.filePath ? 'active' : undefined} key={file.filePath} onClick={() => setSelectedPath(file.filePath)} type="button">
                  <FileCode2 /><span>{file.filePath}</span>
                </button>
              ))}
            </nav>
            <pre><code>{selected?.content}</code></pre>
          </div>
        ) : <div className="feature-workbench-empty"><FileCode2 /><strong>点击生成预览</strong></div>}
      </QueryState>
    </section>
  )
}
