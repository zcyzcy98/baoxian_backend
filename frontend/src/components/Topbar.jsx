import './Topbar.css'

export default function Topbar({ breadcrumb, onNavigate, profile }) {
  const initial = (profile?.name || '我')[0]
  const avatarUrl = profile?.avatarUrl

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
          <span className="num">6,847</span>
          <span className="total">/ 8,000 积分</span>
          <div className="credits-bar"></div>
        </div>
        <button className="icon-btn" aria-label="通知">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>
          <span className="badge"></span>
        </button>
        <button className="avatar-btn" aria-label="个人信息" onClick={() => onNavigate('profile')}
          style={avatarUrl ? { padding: 0, overflow: 'hidden' } : {}}>
          {avatarUrl
            ? <img src={avatarUrl} alt="avatar" style={{ width: '100%', height: '100%', objectFit: 'cover', borderRadius: '50%' }} />
            : initial}
        </button>
      </div>
    </header>
  )
}
