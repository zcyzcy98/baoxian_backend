import { useState } from 'react'
import './Sidebar.css'

const NAV_STRUCTURE = [
  {
    groupLabel: '— Workflow · 主工作流',
    items: [
      { id: 'topic-square', label: '选题广场', icon: 'sparkle' },
      {
        id: 'content', label: '内容创作', icon: 'doc', collapsible: true,
        children: [
          { id: 'xhs-create', label: '小红书创作' },
          { id: 'video-create', label: '视频创作' },
          { id: 'gzh-create', label: '公众号创作' },
          { id: 'xhs-rewrite', label: '小红书仿写' },
          { id: 'gzh-rewrite', label: '公众号仿写' },
          { id: 'video-rip', label: '视频仿做' },
        ],
      },
      {
        id: 'viral', label: '爆款拆解', icon: 'search', collapsible: true,
        children: [
          { id: 'viral-xhs', label: '拆解小红书爆款' },
          { id: 'viral-douyin', label: '拆解抖音爆款' },
        ],
      },
      { id: 'advisory', label: '答疑逼单', icon: 'chat' },
    ],
  },
  {
    groupLabel: '— Settings · 我的设置',
    items: [
      { id: 'profile', label: '个人信息', icon: 'card' },
      { id: 'style', label: '个人风格', icon: 'user' },
    ],
  },
  {
    groupLabel: '— Knowledge · 知识库',
    items: [
      {
        id: 'knowledge', label: '保险知识库', icon: 'book', collapsible: true,
        children: [
          { id: 'kb-faq', label: '常见 FAQ' },
          { id: 'kb-claims', label: '理赔案例库' },
          { id: 'kb-products', label: '险种大全' },
          { id: 'kb-tips', label: '投保注意事项' },
          { id: 'kb-coverage', label: '保障责任' },
          { id: 'kb-compliance', label: '合规词库' },
        ],
      },
    ],
  },
]

const ICONS = {
  sparkle: <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><path d="M12 2 L13.5 8.5 L20 10 L13.5 11.5 L12 18 L10.5 11.5 L4 10 L10.5 8.5 Z"/><circle cx="19" cy="5" r="1.2"/><circle cx="5" cy="19" r="1.2"/></svg>,
  doc: <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><path d="M4 4h12l4 4v12H4z"/><path d="M16 4v4h4"/><path d="M8 13h8M8 17h5"/></svg>,
  search: <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>,
  chat: <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/></svg>,
  card: <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="5" width="18" height="14" rx="2"/><circle cx="9" cy="11" r="2"/><line x1="14" y1="9" x2="18" y2="9"/><line x1="14" y1="13" x2="18" y2="13"/></svg>,
  user: <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 21v-1a8 8 0 0 1 16 0v1"/></svg>,
  book: <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>,
}

export default function Sidebar({ activeId, onNavigate, profile }) {
  const [expanded, setExpanded] = useState(() => {
    // Auto-expand the group that contains activeId
    const initial = new Set()
    NAV_STRUCTURE.forEach(group => {
      group.items.forEach(item => {
        if (item.collapsible && item.children) {
          if (item.children.some(c => c.id === activeId)) {
            initial.add(item.id)
          }
        }
      })
    })
    if (initial.size === 0) initial.add('content')
    return initial
  })

  const toggle = (id) => {
    setExpanded(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <span className="mark">
          <svg viewBox="0 0 28 28" fill="none">
            <circle cx="14" cy="14" r="13" stroke="#1F1F1E" strokeWidth="1.4"/>
            <circle cx="14" cy="9" r="2.2" fill="#CC785C"/>
            <circle cx="9" cy="17" r="2.2" fill="#3F5C52"/>
            <circle cx="19" cy="17" r="2.2" fill="#1F1F1E"/>
            <path d="M14 11.2 L9.8 15.6 M14 11.2 L18.2 15.6 M11 17.5 L17 17.5" stroke="#1F1F1E" strokeWidth="1.1" strokeLinecap="round"/>
          </svg>
        </span>
        <span className="name">承知<span className="en">CHENGZHI</span></span>
      </div>

      <nav className="sidebar-nav scroll-y">
        {NAV_STRUCTURE.map((group, gi) => (
          <div className="nav-group" key={gi}>
            <div className="nav-group-title">{group.groupLabel}</div>
            {group.items.map(item => {
              if (item.collapsible) {
                const isOpen = expanded.has(item.id)
                const hasActiveChild = item.children?.some(c => c.id === activeId)
                return (
                  <div className={`nav-collapsible ${isOpen ? 'open' : ''}`} key={item.id}>
                    <a className="nav-item" onClick={() => toggle(item.id)}>
                      <span className="icon">{ICONS[item.icon]}</span>
                      <span className="label">{item.label}</span>
                      <span className="chevron">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><polyline points="9 18 15 12 9 6"/></svg>
                      </span>
                    </a>
                    <div className="nav-sub">
                      {item.children.map(child => (
                        <a
                          key={child.id}
                          className={`nav-sub-item ${child.id === activeId ? 'active' : ''}`}
                          onClick={() => onNavigate(child.id)}
                        >
                          {child.label}
                        </a>
                      ))}
                    </div>
                  </div>
                )
              }
              return (
                <a
                  key={item.id}
                  className={`nav-item ${item.id === activeId ? 'active' : ''}`}
                  onClick={() => onNavigate(item.id)}
                >
                  <span className="icon">{ICONS[item.icon]}</span>
                  <span className="label">{item.label}</span>
                </a>
              )
            })}
          </div>
        ))}
      </nav>

      <div className="sidebar-foot">
        <div className="member-card">
          <div className="mc-label">— ANNUAL MEMBER</div>
          <div className="mc-days">还剩 <b>327</b> 天到期</div>
          <span className="mc-renew">续费 / 加购积分</span>
        </div>
        <div className="user-mini" onClick={() => onNavigate('profile')} style={{ cursor: 'pointer' }}>
          <div className="avatar" style={profile?.avatarUrl ? { padding: 0, overflow: 'hidden', background: 'transparent' } : {}}>
            {profile?.avatarUrl
              ? <img src={profile.avatarUrl} alt="avatar" style={{ width: '100%', height: '100%', objectFit: 'cover', borderRadius: '50%' }} />
              : (profile?.name || '我')[0]}
          </div>
          <div className="info">
            <div className="uname">{profile?.name || '我的账号'}</div>
            <div className="role">{profile?.title || '保险顾问'}</div>
          </div>
          <span className="more">⋯</span>
        </div>
      </div>
    </aside>
  )
}
