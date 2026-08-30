export interface MetadataPromptAction {
  label: string
  description: string
  prompt: string
  mode?: 'display-text-translation'
}

export const MODEL_METADATA_PROMPT_ACTIONS: MetadataPromptAction[] = [
  {
    label: '中文化未翻译项',
    description: '名称、注释与说明',
    prompt: '检查当前模型全部允许翻译的展示文本，将仍为英文或未完成中文化的名称、注释和说明翻译为简洁准确的中文，并仅在语义明确时补全空说明或备注。IoT、API、ID、URL、MQTT、Kotlin 等通用技术缩写可保留。已经准确的中文不要改写，不能可靠翻译或补全的项不生成 Patch 并写入 questions。只允许返回展示文本的局部 Patch，不得修改 ID、编码、字段、查询、关联、路由、类型、映射、顺序或任何结构。',
    mode: 'display-text-translation',
  },
  {
    label: '补全字段说明',
    description: '补齐 API 语义',
    prompt: '仅补全当前模型现有 fields 的 remark。结合模型用途、fieldCode、label、kotlinType 和 dbColumn，为每个字段生成准确简洁的中文业务说明，供 API 和查询元数据使用。保留其他全部字段属性，不要新增、删除或重排字段，也不要修改 DTO、查询、关联、路由或模型身份。',
  },
  {
    label: '推导表单语义',
    description: '控件与可见性',
    prompt: '仅根据当前模型已有字段的名称、类型和约束，整列检查并修正 fields 的 formControl、listVisible 和 formVisible。密码、长文本、日期时间、布尔值、枚举和普通文本应使用匹配的控件与可见性。保留字段身份、名称、类型、数据库列和其他元数据，不要新增、删除或重排字段。',
  },
  {
    label: '生成常用查询',
    description: 'LowQuery 条件',
    prompt: '根据当前模型已有字段补充最小必要的常用 LowQuery 查询元数据，包括明确可用的精确匹配、关键词模糊查询和时间范围查询。只修改 queries，复用现有属性名，不要修改字段、DTO、关联、路由或模型身份，不要生成没有业务依据的查询。',
  },
]

export const DTO_METADATA_PROMPT_ACTIONS: MetadataPromptAction[] = [
  {
    label: '中文化未翻译项',
    description: '字段与 Schema 说明',
    prompt: '检查当前 DTO 全部允许翻译的展示文本，将仍为英文或未完成中文化的 DTO 注释、DTO 说明、字段说明和嵌套 Schema 说明翻译为简洁准确的中文，并仅在语义明确时补全空说明。保留通用技术缩写，已经准确的中文不要改写，不能可靠补全时写入 questions。只允许返回展示文本的局部 Patch，不得修改 DTO ID、dtoCode、字段 name、sourcePath、类型、可空性、校验、顺序或任何结构。',
    mode: 'display-text-translation',
  },
  {
    label: '生成 DTO 结构',
    description: '类型、字段与约束',
    prompt: '根据当前 DTO 的人工注释和用途描述，补全 className、dtoCode、kind、visibility、description 与 fields。独立 DTO 为每个字段生成 name、sourcePath、nullability 和 API schema；STRUCTURE 为每个字段生成结构化 kotlinType；实体投影优先复用 availableModels 中真实存在的 sourceModelCode 和属性路径。不要修改 name、featureId、packageName 或 contributorId，不要虚构不可确认的业务字段。',
  },
  {
    label: '补全字段',
    description: '新增缺失字段',
    prompt: '结合当前 DTO 注释、用途、已有字段和可用实体，仅补充明确缺失的 fields，并修正字段类型、来源路径与可空策略。保留已有字段及其顺序；同一 sourcePath 或 name 的字段必须合并，不要重复生成。',
  },
  {
    label: '检查字段约束',
    description: '可空性与校验',
    prompt: '检查当前 DTO 全部字段的 nullability、API schema 或 kotlinType，并补充能从语义可靠判断的校验规则。不要改 DTO 注释、归属、包名、Contributor ID或字段身份；不能确定的约束保持原值。',
  },
]

export const CONTRACT_METADATA_PROMPT_ACTIONS: MetadataPromptAction[] = [
  {
    label: '中文化未翻译项',
    description: '接口名称与文档',
    prompt: '检查当前契约全部允许翻译的展示文本，将仍为英文或未完成中文化的契约名称、操作名称与接口文档说明翻译为简洁准确的中文，并仅在语义明确时补全空说明。保留通用技术缩写，已经准确的中文不要改写，不能可靠补全时写入 questions。只允许返回展示文本的局部 Patch，不得修改契约 ID、contractCode、operationCode、path、method、transport、参数名、类型、必填性、顺序或任何结构。',
    mode: 'display-text-translation',
  },
  {
    label: '生成接口文档',
    description: '摘要、说明与响应',
    prompt: '仅补全当前契约现有 operations 的 name、description、parameters.description、requestBody.description、responseBody.description 以及已有请求和响应字段的 description。说明应简洁、准确并明确可空语义。保留契约身份、操作数量与顺序、operationCode、method、path、transport、implementation、authenticated、permission、callContext、responseEnvelope 和所有类型结构；不要新增、删除或重排操作。',
  },
  {
    label: '补全标准接口',
    description: '列表、详情、保存',
    prompt: '根据当前契约补充最小必要的列表、详情和保存操作。复用当前模型或已有 DTO 作为方法入参与出参，不要重复展开已有类型，不要修改契约身份和已有操作。',
  },
  {
    label: '补全类型绑定',
    description: '实体与 DTO',
    prompt: '检查当前契约每个方法的 requestBody 和 responseBody，只从已有实体或匹配用途的 DTO 中选择类型，并修正单个或列表基数。不要新增无关操作，不要内联重复的类型结构。',
  },
  {
    label: '补充进度接口',
    description: 'SSE 结构化响应',
    prompt: '仅在当前契约确有长任务语义时补充 SSE 进度接口，使用结构化响应类型并保留现有 HTTP 操作；没有长任务依据时不要新增接口。',
  },
]

export function isMetadataDisplayTextTranslationRequest(value: string): boolean {
  const normalized = value.trim().toLowerCase()
  const translationRequested = ['翻译', '中文化', '改成中文', 'translate', 'localize']
    .some((term) => normalized.includes(term))
  if (!translationRequested) return false
  return ['元数据', '展示', '名称', '注释', '说明', '文档', 'metadata']
    .some((term) => normalized.includes(term))
}
