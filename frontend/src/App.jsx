import { useState, useCallback } from 'react'
import './index.css'
import './App.css'
import Sidebar from './components/Sidebar'
import Topbar from './components/Topbar'
import TopicSquarePage from './pages/TopicSquarePage'
import XhsCreatePage from './pages/XhsCreatePage'
import GzhCreatePage from './pages/GzhCreatePage'
import XhsRewritePage from './pages/XhsRewritePage'
import GzhRewritePage from './pages/GzhRewritePage'
import DramaPage from './pages/DramaPage'
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

export default function App() {
  const [activeId, setActiveId] = useState(() => {
    return localStorage.getItem('chengzhi:active-page') || 'topic-square'
  })
  const [topicPrefill, setTopicPrefill] = useState(null)

  const navigate = useCallback((id, prefill) => {
    setActiveId(id)
    localStorage.setItem('chengzhi:active-page', id)
    if (prefill) setTopicPrefill(prefill)
  }, [])

  const pageConfig = PAGE_MAP[activeId] || PAGE_MAP['topic-square']
  const PageComponent = pageConfig.component

  return (
    <div className="app">
      <Sidebar activeId={activeId} onNavigate={navigate} />
      <div className="main">
        <Topbar breadcrumb={pageConfig.breadcrumb} onNavigate={navigate} />
        <div className="content scroll-y">
          <div className="content-inner">
            <PageComponent
              key={activeId}
              onNavigate={navigate}
              topicPrefill={topicPrefill}
              onPrefillConsumed={() => setTopicPrefill(null)}
              kbType={pageConfig.kbType}
              mode={activeId === 'video-rip' ? 'rip' : 'create'}
            />
          </div>
        </div>
      </div>
    </div>
  )
}
