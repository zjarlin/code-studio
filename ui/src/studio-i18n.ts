export const workspaceKeys = ['library', 'model', 'contract', 'agent', 'api'] as const

export type Workspace = typeof workspaceKeys[number]

interface WorkspaceMessage {
  label: string
  description: string
}

interface ConstantMessages {
  tabLabel: string
  searchGroups: string
  createGroup: string
  groupSaved: string
  deleteGroupConfirmation: string
  groupDeleted: string
  groupCodeCamelCase: string
  objectNameInvalid: string
  objectCommentRequired: string
  groupItemRequired: string
  constantNameInvalid: string
  unnamedConstant: string
  constantCommentRequired: string
  operationFailed: string
  unauthorized: string
  networkFailed: string
  loading: string
  noGroups: string
  groupAriaSuffix: string
  newGroup: string
  deleteGroup: string
  save: string
  closeMessage: string
  groupCode: string
  groupCodePlaceholder: string
  kotlinObjectName: string
  objectNamePlaceholder: string
  objectComment: string
  objectCommentPlaceholder: string
  items: string
  addConstant: string
  name: string
  type: string
  value: string
  comment: string
  constantName: string
  constantNamePlaceholder: string
  constantType: string
  constantValue: string
  constantComment: string
  constantCommentPlaceholder: string
  constantCompleted: string
  constantCompletionInputRequired: string
  deleteConstant: string
}

interface StudioMessages {
  brand: string
  workspaceNavigation: string
  accessToken: string
  accessTokenMissing: string
  workspaces: Record<Workspace, WorkspaceMessage>
  constants: ConstantMessages
}

export const zhCN: StudioMessages = {
  brand: 'code studio',
  workspaceNavigation: '工作台导航',
  accessToken: '访问令牌',
  accessTokenMissing: '令牌未配置',
  workspaces: {
    library: { label: '库', description: '低代码资源' },
    agent: { label: '智能体', description: '智能体设计' },
    model: { label: '模型', description: '模型设计' },
    contract: { label: 'Service', description: 'Service 设计' },
    api: { label: '接口', description: '接口调试' },
  },
  constants: {
    tabLabel: '常量',
    searchGroups: '搜索常量组',
    createGroup: '新建常量组',
    groupSaved: '常量组已保存',
    deleteGroupConfirmation: '删除常量组“{name}”？',
    groupDeleted: '常量组已删除',
    groupCodeCamelCase: '常量对象名未能生成合法内部标识',
    objectNameInvalid: '对象名必须是合法 Kotlin 标识符',
    objectCommentRequired: '常量对象注释不能为空',
    groupItemRequired: '常量组至少需要一条常量',
    constantNameInvalid: '常量名必须使用大写下划线命名',
    unnamedConstant: '未命名常量',
    constantCommentRequired: '常量 {name} 的注释不能为空',
    operationFailed: '常量操作失败',
    unauthorized: '当前会话无权修改常量元数据',
    networkFailed: '常量服务连接失败，请检查低代码服务是否可用',
    loading: '正在读取',
    noGroups: '暂无常量组',
    groupAriaSuffix: '常量',
    newGroup: '新建常量组',
    deleteGroup: '删除常量组',
    save: '保存',
    closeMessage: '关闭消息',
    groupCode: '常量组内部标识',
    groupCodePlaceholder: 'statusValues',
    kotlinObjectName: 'Kotlin 对象名',
    objectNamePlaceholder: 'StatusConstants',
    objectComment: '对象注释',
    objectCommentPlaceholder: '描述该常量对象的用途。',
    items: '常量项',
    addConstant: '新增常量',
    name: '名称',
    type: '类型',
    value: '值',
    comment: '注释',
    constantName: '常量名',
    constantNamePlaceholder: 'ENABLED',
    constantType: '常量类型',
    constantValue: '常量值',
    constantComment: '常量注释',
    constantCommentPlaceholder: '说明该常量的业务含义。',
    constantCompleted: '常量项已由 AI 补全',
    constantCompletionInputRequired: '请至少填写常量值、名称或注释中的一项',
    deleteConstant: '删除常量',
  },
}
