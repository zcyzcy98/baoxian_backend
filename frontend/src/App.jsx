import { useState, useCallback, useEffect } from 'react'
import { useNavigate as useRouterNavigate, useLocation, Navigate } from 'react-router-dom'
import './index.css'
import './App.css'
import { fetchMe, logout as authLogout } from './auth'
import LandingPage from './pages/LandingPage'
import Sidebar from './components/Sidebar'
import Topbar from './components/Topbar'
import TopicSquarePage from './pages/TopicSquarePage'
import XhsCreatePage from './pages/XhsCreatePage'
import GzhCreatePage from './pages/GzhCreatePage'
import XhsRewritePage from './pages/XhsRewritePage'
import GzhRewritePage from './pages/GzhRewritePage'
import DramaPage from './pages/DramaPage'
import VideoKouboPage from './pages/VideoKouboPage'
import ViralXhsPage from './pages/ViralXhsPage'
import ViralDouyinPage from './pages/ViralDouyinPage'
import AdvisoryPage from './pages/AdvisoryPage'
import ProfilePage from './pages/ProfilePage'
import StylePage from './pages/StylePage'
import KnowledgePage from './pages/KnowledgePage'
import CreditsPage from './pages/CreditsPage'

const PAGE_MAP = {
  'topic-square': { component: TopicSquarePage, breadcrumb: ['选题广场'] },
  'xhs-create': { component: XhsCreatePage, breadcrumb: ['内容创作', '小红书创作'] },
  'video-create': { component: DramaPage, breadcrumb: ['内容创作', '视频创作'] },
  'video-koubo': { component: VideoKouboPage, breadcrumb: ['内容创作', '口播视频'] },
  'gzh-create': { component: GzhCreatePage, breadcrumb: ['内容创作', '公众号创作'] },
  'xhs-rewrite': { component: XhsRewritePage, breadcrumb: ['内容创作', '小红书仿写'] },
  'gzh-rewrite': { component: GzhRewritePage, breadcrumb: ['内容创作', '公众号仿写'] },
  'video-rip': { component: DramaPage, breadcrumb: ['内容创作', '视频仿做'] },
  'viral-xhs': { component: ViralXhsPage, breadcrumb: ['爆款拆解', '拆解小红书爆款'] },
  'viral-douyin': { component: ViralDouyinPage, breadcrumb: ['爆款拆解', '拆解抖音爆款'] },
  'advisory': { component: AdvisoryPage, breadcrumb: ['答疑逼单'] },
  'profile': { component: ProfilePage, breadcrumb: ['个人信息'] },
  'style': { component: StylePage, breadcrumb: ['个人风格'] },
  'kb-faq': { component: KnowledgePage, breadcrumb: ['保险知识库', '常见 FAQ'], kbType: 'faq' },
  'kb-claims': { component: KnowledgePage, breadcrumb: ['保险知识库', '理赔案例库'], kbType: 'claims' },
  'kb-products': { component: KnowledgePage, breadcrumb: ['保险知识库', '险种大全'], kbType: 'products' },
  'kb-tips': { component: KnowledgePage, breadcrumb: ['保险知识库', '投保注意事项'], kbType: 'tips' },
  'kb-coverage': { component: KnowledgePage, breadcrumb: ['保险知识库', '保障责任'], kbType: 'coverage' },
  'kb-compliance': { component: KnowledgePage, breadcrumb: ['保险知识库', '合规词库'], kbType: 'compliance' },
  'credits': { component: CreditsPage, breadcrumb: ['积分管理'] },
}

// auth status: 'loading' | 'guest' | 'no-access' | 'active'
function useAuth() {
  const [auth, setAuth] = useState({ status: 'loading', phone: '' })

  useEffect(() => {
    fetchMe().then(user => {
      if (!user) {
        setAuth({ status: 'guest', phone: '' })
      } else if (!user.hasAccess) {
        setAuth({ status: 'no-access', phone: user.phone })
      } else {
        setAuth({ status: 'active', phone: user.phone })
      }
    })
  }, [])

  function onAuthSuccess(user) {
    if (user.hasAccess) {
      setAuth({ status: 'active', phone: user.phone })
    } else {
      setAuth({ status: 'no-access', phone: user.phone })
    }
  }

  async function onLogout() {
    await authLogout()
    setAuth({ status: 'guest', phone: '' })
  }

  return { auth, onAuthSuccess, onLogout }
}

async function loadProfile() {
  try {
    const token = localStorage.getItem('chengzhi:token')
    const res = await fetch('/api/profile', {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    if (!res.ok) return null
    return res.json()
  } catch { return null }
}

export default function App() {
  const { auth, onAuthSuccess, onLogout } = useAuth()
  const location = useLocation()
  const routerNavigate = useRouterNavigate()
  const [topicPrefill, setTopicPrefill] = useState(null)
  const [userProfile, setUserProfile] = useState({ name: '', title: '', avatarUrl: null })
  const [visitedPages, setVisitedPages] = useState(new Set(['topic-square']))
  const [contentPrefill, setContentPrefill] = useState({})

  const pageId = location.pathname.slice(1)

  useEffect(() => {
    setVisitedPages(prev => {
      if (prev.has(pageId)) return prev
      return new Set([...prev, pageId])
    })
  }, [pageId])

  useEffect(() => {
    if (auth.status === 'active') {
      loadProfile().then(dto => {
        setUserProfile(dto
          ? { name: dto.name || '', title: dto.years || '', avatarUrl: dto.avatarUrl || null }
          : { name: '', title: '', avatarUrl: null })
      })
    } else if (auth.status === 'guest') {
      setUserProfile({ name: '', title: '', avatarUrl: null })
    }
  }, [auth.status])

  const handleProfileUpdate = useCallback((patch) => {
    setUserProfile(prev => ({ ...prev, ...patch }))
  }, [])

  const navigate = useCallback((id, prefill) => {
    routerNavigate('/' + id)
    if (prefill) setTopicPrefill(prefill)
  }, [routerNavigate])

  const navigateWithContentPrefill = useCallback((id, prefillData) => {
    setVisitedPages(prev => {
      if (prev.has(id)) return prev
      return new Set([...prev, id])
    })
    routerNavigate('/' + id)
    setContentPrefill(prev => ({ ...prev, [id]: prefillData }))
  }, [routerNavigate])

  // ── 加载中 ──────────────────────────────────────────
  if (auth.status === 'loading') {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', fontFamily: 'var(--sans)', color: 'var(--ink-3, #6B6862)', fontSize: 14 }}>
        加载中…
      </div>
    )
  }

  // ── 未登录 / 无权限 ──────────────────────────────────
  if (auth.status === 'guest' || auth.status === 'no-access') {
    if (location.pathname !== '/login') {
      return <Navigate to="/login" replace />
    }
    return (
      <LandingPage
        onAuthSuccess={onAuthSuccess}
        noAccess={auth.status === 'no-access'}
        phone={auth.phone}
        onLogout={onLogout}
      />
    )
  }

  // ── 已登录：从 /login 或 / 跳转到主页 ────────────────
  if (location.pathname === '/login' || location.pathname === '/') {
    return <Navigate to="/topic-square" replace />
  }

  // ── 主应用 ───────────────────────────────────────────
  const pageConfig = PAGE_MAP[pageId] || PAGE_MAP['topic-square']

  return (
    <div className="app">
      <Sidebar activeId={pageId} onNavigate={navigate} profile={userProfile} />
      <div className="main">
        <Topbar breadcrumb={pageConfig.breadcrumb} onNavigate={navigate} onLogout={onLogout} phone={auth.phone} profile={userProfile} />
        <div className="content scroll-y">
          <div className="content-inner">
            {[...visitedPages].map(vpId => {
              const cfg = PAGE_MAP[vpId]
              if (!cfg) return null
              const Comp = cfg.component
              const isActive = vpId === pageId
              return (
                <div key={vpId} style={{ display: isActive ? 'block' : 'none' }}>
                  <Comp
                    onNavigate={navigate}
                    onNavigateWithContentPrefill={navigateWithContentPrefill}
                    topicPrefill={isActive ? topicPrefill : null}
                    onPrefillConsumed={isActive ? () => setTopicPrefill(null) : undefined}
                    contentPrefill={contentPrefill[vpId]}
                    onContentPrefillConsumed={() => {
                      setContentPrefill(prev => {
                        const next = { ...prev }
                        delete next[vpId]
                        return next
                      })
                    }}
                    isActive={isActive}
                    kbType={cfg.kbType}
                    mode={vpId === 'video-rip' ? 'rip' : 'create'}
                    onProfileUpdate={handleProfileUpdate}
                  />
                </div>
              )
            })}
          </div>
        </div>
      </div>
    </div>
  )
}
