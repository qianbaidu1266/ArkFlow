import type { NodeTypeInfo, NodeConfigDef } from '@/types/workflow'
import { NodeType } from '@/types/workflow'

// 节点类型列表
export const nodeTypeList: NodeTypeInfo[] = [
  {
    code: NodeType.START,
    name: '开始',
    description: '工作流入口节点',
    icon: '▶',
    color: '#10b981',
    category: '基础'
  },
  {
    code: NodeType.END,
    name: '结束',
    description: '工作流出口节点',
    icon: '■',
    color: '#ef4444',
    category: '基础'
  },
  {
    code: NodeType.LLM,
    name: 'LLM',
    description: '大语言模型调用节点',
    icon: '🤖',
    color: '#8b5cf6',
    category: 'AI'
  },
  {
    code: NodeType.AGENT,
    name: 'Agent',
    description: '智能体节点，支持工具调用',
    icon: '🎯',
    color: '#f59e0b',
    category: 'AI'
  },
  {
    code: NodeType.CONDITION,
    name: '条件分支',
    description: '条件判断节点',
    icon: '◆',
    color: '#ec4899',
    category: '逻辑'
  },
  {
    code: NodeType.KNOWLEDGE_RETRIEVAL,
    name: '知识检索',
    description: '从知识库检索相关内容',
    icon: '📚',
    color: '#06b6d4',
    category: 'AI'
  },
  {
    code: NodeType.CODE,
    name: '代码执行',
    description: '执行自定义代码',
    icon: '💻',
    color: '#6366f1',
    category: '工具'
  },
  {
    code: NodeType.HTTP,
    name: 'HTTP请求',
    description: '发送HTTP请求',
    icon: '🌐',
    color: '#84cc16',
    category: '工具'
  },
  {
    code: NodeType.TEMPLATE,
    name: '模板',
    description: '文本模板渲染',
    icon: '📝',
    color: '#f97316',
    category: '工具'
  },
  {
    code: NodeType.VARIABLE_ASSIGNER,
    name: '变量赋值',
    description: '设置变量值',
    icon: '🔧',
    color: '#14b8a6',
    category: '工具'
  }
]

// 按类别分组的节点类型
export const nodeTypesByCategory = nodeTypeList.reduce((acc, nodeType) => {
  if (!acc[nodeType.category]) {
    acc[nodeType.category] = []
  }
  acc[nodeType.category].push(nodeType)
  return acc
}, {} as Record<string, NodeTypeInfo[]>)

// 获取节点类型信息
export function getNodeTypeInfo(code: NodeType): NodeTypeInfo | undefined {
  return nodeTypeList.find(t => t.code === code)
}

// 节点配置定义
export const nodeConfigDefs: Record<NodeType, NodeConfigDef[]> = {
  [NodeType.START]: [
    {
      name: 'inputVariables',
      type: 'array',
      label: '输入变量',
      description: '定义工作流的输入变量'
    }
  ],
  
  [NodeType.END]: [
    {
      name: 'outputVariables',
      type: 'array',
      label: '输出变量',
      description: '定义工作流的输出变量'
    },
    {
      name: 'outputFormat',
      type: 'select',
      label: '输出格式',
      options: [
        { label: '对象', value: 'object' },
        { label: '文本', value: 'text' }
      ],
      defaultValue: 'object'
    }
  ],
  
  [NodeType.LLM]: [
    {
      name: 'systemPrompt',
      type: 'textarea',
      label: '系统提示词',
      description: '设置LLM的系统提示词，可使用 {{variable}} 引用变量'
    },
    {
      name: 'userPrompt',
      type: 'textarea',
      label: '用户提示词',
      description: '设置LLM的用户提示词，可使用 {{variable}} 引用变量',
      required: true
    },
    {
      name: 'model',
      type: 'string',
      label: '模型',
      description: '指定使用的LLM模型',
      defaultValue: 'gpt-3.5-turbo'
    },
    {
      name: 'temperature',
      type: 'number',
      label: 'Temperature',
      description: '控制输出的随机性，范围 0-2',
      defaultValue: 0.7
    },
    {
      name: 'maxTokens',
      type: 'number',
      label: '最大Token数',
      description: '限制输出长度',
      defaultValue: 2000
    },
    {
      name: 'outputKey',
      type: 'string',
      label: '输出变量名',
      description: 'LLM输出存储的变量名',
      defaultValue: 'llm_output'
    }
  ],
  
  [NodeType.AGENT]: [
    {
      name: 'systemPrompt',
      type: 'textarea',
      label: '系统提示词',
      description: '设置Agent的系统提示词'
    },
    {
      name: 'userPrompt',
      type: 'textarea',
      label: '用户提示词',
      description: '设置Agent的用户提示词'
    },
    {
      name: 'maxIterations',
      type: 'number',
      label: '最大迭代次数',
      description: 'Agent最大执行轮数',
      defaultValue: 5
    },
    {
      name: 'tools',
      type: 'array',
      label: '工具',
      description: 'Agent可调用的工具列表'
    },
    {
      name: 'outputKey',
      type: 'string',
      label: '输出变量名',
      defaultValue: 'agent_output'
    }
  ],
  
  [NodeType.CONDITION]: [
    {
      name: 'conditionType',
      type: 'select',
      label: '条件类型',
      options: [
        { label: '表达式', value: 'expression' },
        { label: '多分支', value: 'switch' }
      ],
      defaultValue: 'expression'
    },
    {
      name: 'expression',
      type: 'textarea',
      label: '条件表达式',
      description: 'JavaScript表达式，返回true/false'
    },
    {
      name: 'inputVariable',
      type: 'string',
      label: '输入变量',
      description: '用于多分支判断的变量'
    },
    {
      name: 'cases',
      type: 'array',
      label: '分支条件',
      description: '多分支条件配置'
    }
  ],
  
  [NodeType.KNOWLEDGE_RETRIEVAL]: [
    {
      name: 'knowledgeBaseId',
      type: 'string',
      label: '知识库ID',
      description: '要检索的知识库',
      required: true
    },
    {
      name: 'query',
      type: 'textarea',
      label: '查询',
      description: '检索查询，可使用 {{variable}} 引用变量'
    },
    {
      name: 'queryVariable',
      type: 'string',
      label: '查询变量',
      description: '从变量获取查询内容'
    },
    {
      name: 'topK',
      type: 'number',
      label: 'Top K',
      description: '返回的最相关文档数',
      defaultValue: 5
    },
    {
      name: 'scoreThreshold',
      type: 'number',
      label: '相似度阈值',
      description: '最低相似度分数',
      defaultValue: 0.7
    },
    {
      name: 'searchType',
      type: 'select',
      label: '搜索类型',
      options: [
        { label: '相似度', value: 'similarity' },
        { label: '混合', value: 'hybrid' },
        { label: 'MMR', value: 'mmr' }
      ],
      defaultValue: 'similarity'
    },
    {
      name: 'outputKey',
      type: 'string',
      label: '输出变量名',
      defaultValue: 'retrieved_context'
    }
  ],
  
  [NodeType.CODE]: [
    {
      name: 'language',
      type: 'select',
      label: '编程语言',
      options: [
        { label: 'JavaScript', value: 'javascript' }
      ],
      defaultValue: 'javascript'
    },
    {
      name: 'code',
      type: 'textarea',
      label: '代码',
      description: '要执行的代码',
      required: true
    },
    {
      name: 'inputMappings',
      type: 'json',
      label: '输入映射',
      description: '变量名到代码变量的映射'
    },
    {
      name: 'outputMappings',
      type: 'json',
      label: '输出映射',
      description: '代码变量到输出变量的映射'
    },
    {
      name: 'outputKey',
      type: 'string',
      label: '输出变量名',
      defaultValue: 'code_result'
    }
  ],
  
  [NodeType.HTTP]: [
    {
      name: 'url',
      type: 'string',
      label: 'URL',
      description: '请求地址，可使用 {{variable}} 引用变量',
      required: true
    },
    {
      name: 'method',
      type: 'select',
      label: '请求方法',
      options: [
        { label: 'GET', value: 'GET' },
        { label: 'POST', value: 'POST' },
        { label: 'PUT', value: 'PUT' },
        { label: 'PATCH', value: 'PATCH' },
        { label: 'DELETE', value: 'DELETE' }
      ],
      defaultValue: 'GET'
    },
    {
      name: 'headers',
      type: 'json',
      label: '请求头',
      description: 'HTTP请求头'
    },
    {
      name: 'body',
      type: 'textarea',
      label: '请求体',
      description: '请求体内容'
    },
    {
      name: 'timeout',
      type: 'number',
      label: '超时时间(ms)',
      defaultValue: 30000
    },
    {
      name: 'outputKey',
      type: 'string',
      label: '输出变量名',
      defaultValue: 'http_response'
    }
  ],
  
  [NodeType.TEMPLATE]: [
    {
      name: 'template',
      type: 'textarea',
      label: '模板',
      description: '模板内容，使用 {{variable}} 引用变量',
      required: true
    },
    {
      name: 'outputFormat',
      type: 'select',
      label: '输出格式',
      options: [
        { label: '文本', value: 'text' },
        { label: 'Markdown', value: 'markdown' },
        { label: 'HTML', value: 'html' }
      ],
      defaultValue: 'text'
    },
    {
      name: 'outputKey',
      type: 'string',
      label: '输出变量名',
      defaultValue: 'rendered_template'
    }
  ],
  
  [NodeType.VARIABLE_ASSIGNER]: [
    {
      name: 'assignments',
      type: 'array',
      label: '赋值列表',
      description: '变量赋值配置',
      required: true
    }
  ]
}

// 获取节点配置定义
export function getNodeConfigDefs(nodeType: NodeType): NodeConfigDef[] {
  return nodeConfigDefs[nodeType] || []
}
