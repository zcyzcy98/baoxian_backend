import { useState, useRef, useEffect } from 'react'
import { fetchCreditsBalance } from '../api'
import './Topbar.css'

export default function Topbar({ breadcrumb, onNavigate, onLogout, phone, profile }) {
  const initial = (profile?.name || '我')[0]
  const avatarUrl = profile?.avatarUrl
  const [menuOpen, setMenuOpen] = useState(false)
  const menuRef = useRef(null)
  const [credits, setCredits] = useState(null) // { balance, total }

  const refreshCredits = () => fetchCreditsBalance().then(setCredits).catch(() => {})

  // 初次加载 + 面包屑变化时（说明刚切换了页面/完成了操作）刷新
  useEffect(() => {
    refreshCredits()
  }, [breadcrumb])

  // 每 30 秒轮询一次保证同步
  useEffect(() => {
    const id = setInterval(refreshCredits, 30_000)
    return () => clearInterval(id)
  }, [])

  useEffect(() => {
    if (!menuOpen) return
    function handleClick(e) {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        setMenuOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [menuOpen])

  return (
    <header className="topbar">
      <div className="breadcrumb">
        {breadcrumb.map((item, i) => (
          <span key={i}>
            {i > 0 && <span className="sep">/</span>}
            <span className={i === breadcrumb.length - 1 ? 'current' : ''}>
              {item}
            </span>
          </span>
        ))}
      </div>
      <div className="top-actions">
        <div className="credits-chip" onClick={() => onNavigate('credits')}>
          <span className="star">✦</span>
          <span className="num">{credits ? credits.balance.toLocaleString() : '—'}</span>
          <span className="total">/ {credits ? credits.total.toLocaleString() : '8,000'} 积分</span>
          <div className="credits-bar" style={credits ? { '--pct': `${(credits.balance / credits.total * 100).toFixed(1)}%` } : {}}></div>
        </div>
        <button className="icon-btn" aria-label="通知">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>
          <span className="badge"></span>
        </button>
        <div className="avatar-wrap" ref={menuRef}>
          <button className="avatar-btn" aria-label="账户菜单" onClick={() => setMenuOpen(v => !v)}
            style={avatarUrl ? { padding: 0, overflow: 'hidden' } : {}}>
            {avatarUrl
              ? <img src={avatarUrl} alt="avatar" style={{ width: '100%', height: '100%', objectFit: 'cover', borderRadius: '50%' }} />
              : initial}
          </button>
          {menuOpen && (
            <div className="avatar-menu">
              {phone && <div className="avatar-menu-phone">{phone}</div>}
              <button className="avatar-menu-item" onClick={() => { setMenuOpen(false); onNavigate('profile') }}>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 20c0-4 3.6-7 8-7s8 3 8 7"/></svg>
                个人信息
              </button>
              <div className="avatar-menu-divider" />
              <button className="avatar-menu-item avatar-menu-item--danger" onClick={() => { setMenuOpen(false); onLogout() }}>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
                退出登录
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  )
}
