// ─── All agent definitions (flat, used for routing/lookup) ───────────────

export const ALL_AGENTS = [
  // Topic
  {
    id: 'topic-square', name: '今日选题广场', type: 'topic',
    intro: '汇总已接入的飞书多维表格数据, 一站式生成今日候选选题, 点"用这个"直接跳到对应智能体生成内容。',
  },
  {
    id: 'topic-profile', name: '我的画像设置', type: 'profile',
    intro: '配置你的主营险种 / 客群 / 偏好风格, 用于个性化排序。',
  },

  // 小红书创作
  {
    id: 'xhs-title', name: '小红书标题', type: 'form', endpoint: 'title',
    intro: '输入一个主题, 生成 5 个有爆款潜力的小红书标题。',
    fields: [
      { name: 'topic', label: '主题', placeholder: '例如: 新手化妆教程', required: true },
    ],
  },
  {
    id: 'xhs-text', name: '小红书正文', type: 'form', endpoint: 'text',
    intro: '输入主题和风格, 生成一篇小红书笔记正文。',
    fields: [
      { name: 'topic', label: '主题', placeholder: '例如: 周末上海 city walk', required: true },
      { name: 'style', label: '风格', placeholder: '可选: 治愈 / 干货 / 种草 / 吐槽 ...' },
    ],
  },
  {
    id: 'xhs-image', name: '小红书图片', type: 'form', endpoint: 'image',
    intro: '输入想要的画面描述, 生成图片提示词并调用图片 API 生成配图。',
    fields: [
      { name: 'topic', label: '画面主题', placeholder: '例如: 阳光下的早餐桌', required: true },
    ],
  },
  { id: 'xhs-hot-comment', name: '小红书爆款评论', type: 'form', disabled: true },
  { id: 'xhs-title-pro', name: '小红书爆款标题(进阶版)', type: 'form', disabled: true },
  { id: 'xhs-cs', name: '小红书智能客服', type: 'form', disabled: true },

  // 视频创作
  {
    id: 'video-script', name: '视频脚本', type: 'form', endpoint: 'video-script',
    intro: '给一个视频主题, 生成一篇可直接拍摄的口播脚本(60-90 秒)。',
    fields: [
      { name: 'topic', label: '主题', placeholder: '例如: 给爸妈买保险的 3 个坑', required: true },
      { name: 'style', label: '风格', placeholder: '可选: 干货 / 故事 / 吐槽 / 反转 ...' },
      { name: 'duration', label: '时长', placeholder: '可选: 30秒 / 60秒 / 90秒' },
    ],
  },
  {
    id: 'video-storyboard', name: '视频分镜脚本', type: 'form', endpoint: 'video-storyboard',
    intro: '把脚本拆成可拍摄的分镜表(镜号/景别/画面/口播/字幕)。',
    fields: [
      { name: 'topic', label: '主题', placeholder: '例如: 给爸妈买保险的 3 个坑' },
      { name: 'style', label: '风格', placeholder: '可选: 干货 / 故事 / 反转 ...' },
      { name: 'duration', label: '时长', placeholder: '可选: 60秒 / 90秒' },
      { name: 'script', label: '已有脚本', placeholder: '可选: 把现成的口播脚本贴进来', textarea: true },
    ],
  },
  {
    id: 'video-title', name: '视频标题文案', type: 'form', endpoint: 'video-title',
    intro: '生成抖音/视频号/小红书视频的标题候选 + 发布文案。',
    fields: [
      { name: 'topic', label: '主题', placeholder: '例如: 给爸妈买保险的 3 个坑', required: true },
      { name: 'style', label: '风格', placeholder: '可选: 干货 / 吐槽 / 共鸣 ...' },
    ],
  },
  {
    id: 'video-cover', name: '视频封面图', type: 'form', endpoint: 'video-cover',
    intro: '竖版 9:16 大字封面, 自动生成钩子文字 + 调图片 API 出图。',
    fields: [
      { name: 'topic', label: '视频主题', placeholder: '例如: 给爸妈买保险的 3 个坑', required: true },
      { name: 'style', label: '风格', placeholder: '可选: 揭秘/避坑 / 干货 / 共鸣 / 吐槽' },
    ],
  },
  {
    id: 'video-t2v', name: 'LibTV 视频生成', type: 'form', endpoint: 'video-generate',
    intro: '粘贴你的完整脚本或生成要求, 后端直接调用 LibTV 生成视频。口播模式会先用 AI 把脚本优化成适合真人口播的格式再发给 LibTV。',
    fields: [
      { name: 'script', label: '视频脚本 / 生成要求', placeholder: '把脚本或对视频的要求粘贴到这里', required: true, textarea: true },
      {
        name: 'targetMode',
        label: '生成模式',
        options: [
          { value: '', label: '默认（分镜/自由描述）' },
          { value: 'koubo', label: '口播模式（AI 预处理脚本）' },
        ],
      },
      {
        name: 'characterImageUrl',
        label: '参考人物',
        upload: true,
        accept: 'image/*',
        hint: '口播模式可选：上传角色照片，LibTV 会以该人物形象生成主播。',
      },
      {
        name: 'backgroundImageUrl',
        label: '参考背景',
        upload: true,
        accept: 'image/*',
        hint: '口播模式可选：上传背景图片，LibTV 会以该图片风格渲染视频背景。',
      },
      { name: 'style', label: '补充风格', placeholder: '可选: 写实 / 口播 / 信息流广告 ...' },
      { name: 'duration', label: '时长', placeholder: '可选: 5秒 / 10秒 / 15秒 / 30秒' },
    ],
  },
  {
    id: 'video-merge', name: '视频合成(MP4)', type: 'merge', endpoint: 'video-merge',
    intro: '从当前 LibTV 视频生成历史中选择多个视频, 手动调整顺序后提交拼接。',
  },
  { id: 'video-tts', name: 'AI 配音(TTS)', type: 'form', disabled: true },

  // 公众号创作
  { 
    id: 'wechat-create', 
    name: '公众号内容创作', 
    type: 'form', 
    endpoint: 'wechat-create', 
    intro: '输入一个主题，生成完整的公众号文章，可以选择文章字数、是否需要封面图和配图。', 
    fields: [
      { name: 'topic', label: '文章主题', placeholder: '如：给父母买保险怎么选', required: true },
      { name: 'wordCount', label: '文章字数', placeholder: '默认2000字，建议1500-4000' },
      { name: 'style', label: '风格偏好', placeholder: '可选：如专业严谨/轻松幽默/故事化/案例式' },
      { name: 'needCover', label: '是否需要封面图', options: [
        { value: true, label: '是（2.35:1）' },
        { value: false, label: '否' }
      ]},
      { name: 'needImages', label: '是否需要配图', options: [
        { value: true, label: '是（2.35:1）' },
        { value: false, label: '否' }
      ]}
    ]
  },

  // 小红书仿写
  {
    id: 'xhs-to-wx', name: '小红书转小绿书', type: 'workflow',
    intro: '提取小红书笔记 → 一键复刻改写 → 推送公众号草稿。',
  },

  // 公众号仿写
  {
    id: 'wechat-extract', name: '公众号文章提取', type: 'wechat-extract',
    intro: '粘贴 mp.weixin.qq.com 文章链接，直接拿到标题/正文/封面/摘要/账号信息。',
  },
  {
    id: 'wechat-rewrite', name: '公众号文章仿写', type: 'workflow',
    intro: '提取公众号文章，支持多种仿写模式：转小红书、转短文案/朋友圈、摘要提取、深度改写、标题开头优化。',
  },

  // 视频仿做
  {
    id: 'video-rip', name: '视频翻拍(链接→脚本)', type: 'form', endpoint: 'video-to-script',
    intro: '粘贴视频链接（支持小红书分享页链接），系统会先解析/转写为文档，再二次改编成可拍摄的视频翻拍脚本。',
    fields: [
      { name: 'videoUrl', label: '视频链接', placeholder: '粘贴小红书分享页链接或其他可公网访问的视频直链', required: true },
      {
        name: 'outputFormat',
        label: '文档格式',
        options: [
          { value: 'md', label: 'Markdown 文档' },
          { value: 'txt', label: '纯文本 TXT' },
        ],
      },
      { name: 'style', label: '翻拍脚本风格', placeholder: '可选: 干货 / 故事 / 吐槽 / 保险科普 / 口播带货 ...' },
      { name: 'duration', label: '目标时长', placeholder: '可选: 30秒 / 60秒 / 90秒 / 3分钟' },
    ],
  },

  // 爆款拆解
  {
    id: 'viral-xhs', name: '拆解小红书爆款', type: 'form', endpoint: 'viral-xhs',
    intro: '粘贴任意小红书笔记链接，自动提取内容并拆解爆款结构：标题钩子、正文套路、互动设计、爆款公式，以及可直接套用的保险选题建议。',
    fields: [
      { name: 'videoUrl', label: '小红书笔记链接', placeholder: '粘贴 xhslink.cn 或 www.xiaohongshu.com/explore/... 链接', required: true },
    ],
  },
  {
    id: 'viral-douyin', name: '拆解抖音爆款', type: 'form', endpoint: 'viral-douyin',
    intro: '粘贴抖音链接，或直接粘贴 App 复制的完整分享文本（含文案和短链）。自动提取标题/互动数据，拆解爆款结构并给出保险内容复用建议。无需登录 Cookie。',
    fields: [
      { name: 'videoUrl', label: '抖音链接 / 分享文本', placeholder: '粘贴 v.douyin.com/... 短链，或粘贴 App 复制的完整分享文字（含链接）', required: true, textarea: true },
    ],
  },

  // 答疑逼单
  {
    id: 'advisory-main', name: '客户答疑助手', type: 'advisory',
    intro: '输入客户基本情况和问题，AI 分析客户意图、给出三版应对方案和后续行动建议。',
  },

  // 保险知识库
  {
    id: 'kb-faq', name: '常见FAQ', type: 'form', endpoint: 'kb-faq',
    intro: '输入你的保险疑问，从常见FAQ知识库中召回相关解答，由AI整合为完整回答。',
    fields: [
      { name: 'question', label: '你的问题', placeholder: '例如：重疾险的等待期是多久？理赔时需要哪些材料？', required: true, textarea: true },
    ],
  },
  {
    id: 'kb-claims', name: '理赔案例库', type: 'form', endpoint: 'kb-claims',
    intro: '描述你遇到的理赔情况，从真实案例库中匹配相似案例，帮助你了解理赔流程和关键注意点。',
    fields: [
      { name: 'question', label: '描述情况或问题', placeholder: '例如：客户发生了车祸，住院手术，重疾险和医疗险能同时赔吗？', required: true, textarea: true },
    ],
  },
  {
    id: 'kb-products', name: '险种大全', type: 'form', endpoint: 'kb-products',
    intro: '输入你想了解的险种，从险种知识库中召回核心定义、适用人群和产品价值，帮助客户选择合适的保障。',
    fields: [
      { name: 'question', label: '想了解的险种或问题', placeholder: '例如：百万医疗险和重疾险有什么区别？定期寿险适合哪些人？', required: true, textarea: true },
    ],
  },
  {
    id: 'kb-tips', name: '投保注意事项', type: 'form', endpoint: 'kb-tips',
    intro: '输入投保场景或疑虑，从投保注意事项知识库中召回相关要点，帮你和客户少踩坑。',
    fields: [
      { name: 'question', label: '投保场景或疑问', placeholder: '例如：有高血压的人能买重疾险吗？带病投保有哪些注意事项？', required: true, textarea: true },
    ],
  },
  {
    id: 'kb-coverage', name: '保障责任', type: 'form', endpoint: 'kb-coverage',
    intro: '输入你想确认的保障责任或赔付条件，从保障责任知识库召回精准条款解读。',
    fields: [
      { name: 'question', label: '保障责任相关问题', placeholder: '例如：重疾险的轻症责任一般包括哪些？医疗险的免赔额是怎么算的？', required: true, textarea: true },
    ],
  },
  {
    id: 'xhs-text-compliance', name: '合规词库检测', type: 'form', endpoint: 'text-compliance-check',
    intro: '粘贴一段文案，基于合规词库做 RAG 召回和精确命中检测，找出疑似违规词并给出替换建议。',
    fields: [
      { name: 'content', label: '检测文本', placeholder: '把要检测的正文粘贴到这里', required: true, textarea: true },
    ],
  },
]

export const findAgent = (id) => ALL_AGENTS.find((a) => a.id === id)

// ─── Navigation structure (drives the sidebar) ────────────────────────────

export const NAV = [
  {
    groupLabel: 'WORKFLOW · 主工作流',
    items: [
      { id: 'topic', label: '选题广场', agentId: 'topic-square' },
      {
        id: 'content', label: '内容创作',
        children: [
          { subLabel: '小红书创作', agentIds: ['xhs-title', 'xhs-text', 'xhs-image'] },
          { subLabel: '视频创作', agentIds: ['video-script', 'video-storyboard', 'video-title', 'video-cover', 'video-t2v', 'video-merge'] },
          { subLabel: '公众号创作', agentIds: ['wechat-create'] },
          { subLabel: '小红书仿写', agentIds: ['xhs-to-wx'] },
          { subLabel: '公众号仿写', agentIds: ['wechat-rewrite', 'wechat-extract'] },
          { subLabel: '视频仿做', agentIds: ['video-rip'] },
        ],
      },
      {
        id: 'viral', label: '爆款拆解',
        children: [
          { agentIds: ['viral-xhs', 'viral-douyin'] },
        ],
      },
      { id: 'advisory', label: '答疑逼单', agentId: 'advisory-main' },
    ],
  },
  {
    groupLabel: 'SETTINGS · 我的设置',
    items: [
      { id: 'profile', label: '个人风格', agentId: 'topic-profile' },
    ],
  },
  {
    groupLabel: 'KNOWLEDGE · 知识库',
    items: [
      {
        id: 'knowledge', label: '保险知识库',
        children: [
          { agentIds: ['kb-faq', 'kb-claims', 'kb-products', 'kb-tips', 'kb-coverage', 'xhs-text-compliance'] },
        ],
      },
    ],
  },
]

// ─── Legacy compat exports (still used by some components) ────────────────
export const SECTIONS = []
export const AGENT_GROUPS = []
export const groupsForSection = () => []
export const firstEnabledAgent = () => ALL_AGENTS.find((a) => !a.disabled)
