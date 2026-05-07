import './CreateTopicModal.css'

const FORMATS = [
  {
    id: 'xhs',
    label: '小红书创作',
    icon: '📕',
    desc: '图文笔记 · 标题优化 · 标签推荐',
    route: 'xhs-create',
  },
  {
    id: 'gzh',
    label: '公众号创作',
    icon: '📘',
    desc: '长文写作 · 排版优化 · 素材配图',
    route: 'gzh-create',
  },
  {
    id: 'video',
    label: '视频创作',
    icon: '🎬',
    desc: '脚本生成 · 分镜建议 · 口播文案',
    route: 'video-create',
  },
]

export default function CreateTopicModal({ topic, onClose, onNavigate }) {
  if (!topic) return null

  const handleFormat = (format) => {
    onNavigate(format.route, {
      topic: topic.title,
      angle: topic.angle || '',
    })
  }

  return (
    <div className="ctm-overlay" onClick={onClose}>
      <div className="ctm-card" onClick={(e) => e.stopPropagation()}>
        <button className="ctm-close" onClick={onClose}>
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
            <path d="M18 6L6 18M6 6L18 18"/>
          </svg>
        </button>

        <div className="ctm-header">
          <span className="ctm-badge">选题创作</span>
          <h3 className="ctm-title">{topic.title}</h3>
          {topic.reason && <p className="ctm-reason">{topic.reason}</p>}
        </div>

        <div className="ctm-divider" />

        <div className="ctm-formats">
          <p className="ctm-formats-label">选择创作方式</p>
          <div className="ctm-formats-grid">
            {FORMATS.map((fmt) => (
              <button
                key={fmt.id}
                className="ctm-format-btn"
                onClick={() => handleFormat(fmt)}
              >
                <span className="ctm-format-icon">{fmt.icon}</span>
                <span className="ctm-format-label">{fmt.label}</span>
                <span className="ctm-format-desc">{fmt.desc}</span>
              </button>
            ))}
          </div>
        </div>

        <button className="ctm-cancel" onClick={onClose}>
          取消
        </button>
      </div>
    </div>
  )
}
