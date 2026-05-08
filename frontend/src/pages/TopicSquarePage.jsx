import { useState, useEffect } from 'react'
import { fetchDailyTopics, searchHotTopics } from '../api'
import CreateTopicModal from '../components/CreateTopicModal'
import './TopicSquarePage.css'

const INSURANCE_TYPES = ['重疾险', '医疗险', '意外险', '寿险', '养老险', '车险', '财产险', '理财险']
const DEMOGRAPHICS = ['年轻人', '中年人', '老年人', '父母', '宝爸宝妈', '孩子', '上班族', '自由职业者', '创业者', '学生', '家庭主妇']

// localStorage 持久化, 让刷新页面后选题不丢
const CACHE_KEY = 'chengzhi:topics-cache'
const FILTER_KEY = 'chengzhi:topics-filters'

function loadCache() {
  try {
    const raw = localStorage.getItem(CACHE_KEY)
    if (!raw) return { topics: [], lastFetchedAt: null }
    return JSON.parse(raw)
  } catch { return { topics: [], lastFetchedAt: null } }
}

function loadFilters() {
  try {
    const raw = localStorage.getItem(FILTER_KEY)
    if (!raw) return { ins: [], demo: [] }
    return JSON.parse(raw)
  } catch { return { ins: [], demo: [] } }
}

function formatRelativeTime(iso) {
  if (!iso) return ''
  const diff = Date.now() - new Date(iso).getTime()
  const m = Math.floor(diff / 60000)
  if (m < 1) return '刚刚'
  if (m < 60) return `${m} 分钟前`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h} 小时前`
  return `${Math.floor(h / 24)} 天前`
}

export default function TopicSquarePage({ onNavigate }) {
  // 初始从 localStorage 读, 这样刷新页面不会丢已经拉到的选题
  const cache = loadCache()
  const persistedFilters = loadFilters()

  const [topics, setTopics] = useState(cache.topics || [])
  const [lastFetchedAt, setLastFetchedAt] = useState(cache.lastFetchedAt)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [insuranceFilter, setInsuranceFilter] = useState(persistedFilters.ins || [])
  const [demographicFilter, setDemographicFilter] = useState(persistedFilters.demo || [])
  const [selectedTopic, setSelectedTopic] = useState(null)
  const [showCustomPanel, setShowCustomPanel] = useState(false)
  const [customTitle, setCustomTitle] = useState('')
  // TopHub 关键词搜索 (调 /api/topics/search → TopHubDataService.searchByKeyword)
  const [searchKeyword, setSearchKeyword] = useState('')
  const [searching, setSearching] = useState(false)
  const [searchMode, setSearchMode] = useState(false) // true = 当前展示的是搜索结果, false = 当前是 daily 推荐

  // 持久化筛选
  useEffect(() => {
    localStorage.setItem(FILTER_KEY, JSON.stringify({
      ins: insuranceFilter,
      demo: demographicFilter,
    }))
  }, [insuranceFilter, demographicFilter])

  const loadTopics = async () => {
    if (loading) return
    setLoading(true)
    setError('')
    setSearchMode(false)
    try {
      // categories: ['hotspot'] 让后端只走 TopHubDataService.fetchHotTopics, 跳过飞书表
      const payload = { categories: ['hotspot'] }
      if (insuranceFilter.length > 0) payload.insuranceTypesFilter = insuranceFilter
      if (demographicFilter.length > 0) payload.demographicsFilter = demographicFilter
      const data = await fetchDailyTopics(payload)
      const items = data.items || []
      const fetchedAt = new Date().toISOString()
      setTopics(items)
      setLastFetchedAt(fetchedAt)
      localStorage.setItem(CACHE_KEY, JSON.stringify({
        topics: items,
        lastFetchedAt: fetchedAt,
      }))
    } catch (e) {
      console.error('Failed to load topics:', e)
      setError(e?.message || '拉取失败')
    } finally {
      setLoading(false)
    }
  }

  // 调 /api/topics/search → TopHubDataService.searchByKeyword
  // 直接全网搜某个关键词的热点 (微博/知乎/抖音/B站等), 返回真实热点 + 真实 sourceUrl
  const handleSearchHotspots = async () => {
    const kw = searchKeyword.trim()
    if (!kw || searching) return
    setSearching(true)
    setError('')
    try {
      const data = await searchHotTopics(kw, 50)
      const items = data.items || []
      setTopics(items)
      setSearchMode(true)
      setLastFetchedAt(new Date().toISOString())
      if (items.length === 0) {
        setError(`关键词 "${kw}" 没有匹配的热点, 换个词试试`)
      }
    } catch (e) {
      console.error('Search failed:', e)
      setError(e?.message || '搜索失败')
    } finally {
      setSearching(false)
    }
  }

  const toggleInsurance = (type) => {
    setInsuranceFilter(prev =>
      prev.includes(type) ? prev.filter(t => t !== type) : [...prev, type]
    )
  }

  const toggleDemographic = (demo) => {
    setDemographicFilter(prev =>
      prev.includes(demo) ? prev.filter(d => d !== demo) : [...prev, demo]
    )
  }

  const clearFilters = () => {
    setInsuranceFilter([])
    setDemographicFilter([])
  }

  const activeFilters = insuranceFilter.length + demographicFilter.length

  const handleCardClick = (topic) => {
    setSelectedTopic(topic)
  }

  const handleCloseModal = () => {
    setSelectedTopic(null)
  }

  const handleCustomTopicCreate = () => {
    if (!customTitle.trim()) return
    const customTopic = {
      id: 'user-' + Date.now(),
      title: customTitle.trim(),
      reason: '用户自定义选题，立即开始创作',
      angle: '',
      sourceCategory: 'USER_WRITE',
      score: 100,
    }
    setSelectedTopic(customTopic)
  }

  return (
    <div className="topics-page">
      <div className="topics-header-section">
        <div className="topics-header-row">
          <div>
            <h2 className="topics-title">选题广场</h2>
            <p className="topics-subtitle">
              热门模板 · 知识库 · TopHub 实时热点 · 自定义
              {lastFetchedAt && (
                <span className="topics-fetch-time"> · 数据 {formatRelativeTime(lastFetchedAt)}更新</span>
              )}
            </p>
          </div>
          <div className="topics-header-actions">
            <button
              className="topics-refresh-btn"
              onClick={loadTopics}
              disabled={loading}
              title="重新从后端拉取最新选题"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" style={loading ? { animation: 'spin 1s linear infinite' } : {}}>
                <polyline points="23 4 23 10 17 10"/>
                <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
              </svg>
              {loading ? '拉取中…' : (topics.length === 0 ? '加载选题' : '刷新选题')}
            </button>
            <button
              className="topics-custom-btn"
              onClick={() => setShowCustomPanel(!showCustomPanel)}
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                <path d="M12 20h9"/>
                <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/>
              </svg>
              指定选题创作内容
            </button>
          </div>
        </div>

        {showCustomPanel && (
          <div className="topics-custom-panel">
            <p className="topics-custom-label">输入你的选题想法，直接进入创作</p>
            <div className="topics-custom-row">
              <input
                className="topics-custom-input"
                type="text"
                placeholder="例如：30岁买保险的注意事项..."
                value={customTitle}
                onChange={(e) => setCustomTitle(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleCustomTopicCreate()}
                autoFocus
              />
              <button
                className="topics-custom-submit"
                disabled={!customTitle.trim()}
                onClick={handleCustomTopicCreate}
              >
                开始创作
              </button>
            </div>
          </div>
        )}

        {/* TopHub 关键词搜索 — 直接调 TopHubDataService.searchByKeyword */}
        <div className="topics-search-row">
          <div className="topics-search-icon">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round">
              <circle cx="11" cy="11" r="8"/>
              <path d="m21 21-4.35-4.35"/>
            </svg>
          </div>
          <input
            className="topics-search-input"
            type="text"
            placeholder="搜索全网热点 (微博/知乎/抖音/B站...) — 例如: 重疾、医保、35岁..."
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSearchHotspots()}
            disabled={searching}
          />
          <button
            className="topics-search-btn"
            onClick={handleSearchHotspots}
            disabled={!searchKeyword.trim() || searching}
          >
            {searching ? '搜索中…' : '搜热点'}
          </button>
          {searchMode && (
            <button className="topics-search-back" onClick={loadTopics} disabled={loading}>
              ← 返回每日推荐
            </button>
          )}
        </div>

        <div className="topics-filter-section">
          <div className="topics-filter-group">
            <span className="topics-filter-label">险种</span>
            <div className="topics-filter-chips">
              {INSURANCE_TYPES.map(type => (
                <button
                  key={type}
                  className={`tfc-chip ${insuranceFilter.includes(type) ? 'active' : ''}`}
                  onClick={() => toggleInsurance(type)}
                >
                  {type}
                </button>
              ))}
            </div>
          </div>
          <div className="topics-filter-group">
            <span className="topics-filter-label">人群</span>
            <div className="topics-filter-chips">
              {DEMOGRAPHICS.map(demo => (
                <button
                  key={demo}
                  className={`tfc-chip ${demographicFilter.includes(demo) ? 'active' : ''}`}
                  onClick={() => toggleDemographic(demo)}
                >
                  {demo}
                </button>
              ))}
            </div>
          </div>
          {activeFilters > 0 && (
            <button className="topics-filter-clear" onClick={clearFilters}>
              清除筛选 ({activeFilters})
            </button>
          )}
        </div>

        {activeFilters > 0 && topics.length > 0 && (
          <div className="topics-filter-hint">
            ⓘ 筛选条件已变化，点击 <b>"刷新选题"</b> 应用最新筛选
          </div>
        )}
      </div>

      {error && (
        <div className="topics-error">
          ⚠️ 拉取失败: {error}
          <button onClick={() => setError('')}>×</button>
        </div>
      )}

      {loading ? (
        <div className="topics-loading">
          <div className="loading-dots">
            <span></span><span></span><span></span>
          </div>
          <p>正在拉取选题…</p>
        </div>
      ) : topics.length === 0 ? (
        <div className="topics-empty">
          <div className="topics-empty-icon">
            <svg width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
              <circle cx="12" cy="12" r="10"/>
              <polyline points="23 4 23 10 17 10"/>
              <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
            </svg>
          </div>
          <p className="topics-empty-title">还没有选题</p>
          <p className="topics-empty-desc">点击右上角 <b>"加载选题"</b> 从后端拉取热门模板 + 知识库 + TopHub 热点</p>
          <button className="topics-empty-btn" onClick={loadTopics} disabled={loading}>
            {loading ? '拉取中…' : '立即加载'}
          </button>
        </div>
      ) : (
        <div className="topics-grid">
          {topics.map((topic, i) => (
            <TopicCard
              key={topic.id || i}
              topic={topic}
              onClick={() => handleCardClick(topic)}
            />
          ))}
        </div>
      )}

      {selectedTopic && (
        <CreateTopicModal
          topic={selectedTopic}
          onClose={handleCloseModal}
          onNavigate={onNavigate}
        />
      )}
    </div>
  )
}

function TopicCard({ topic, onClick }) {
  const score = topic.score ?? 0
  const scorePct = Math.min(100, Math.max(0, score))
  const scoreColor = scorePct >= 80 ? '#d45a2e' : scorePct >= 60 ? '#b8860b' : '#888'

  const sourceBadge = getSourceBadge(topic)
  const hasReason = topic.reason && topic.reason.trim()
  const hasInsuranceTypes = topic.insuranceTypes && topic.insuranceTypes.length > 0
  const hasDemographics = topic.demographics && topic.demographics.length > 0
  const hasSourceUrl = topic.sourceUrl && topic.sourceUrl.trim()

  return (
    <div className="topic-card" onClick={onClick}>
      <div className="tc-top-row">
        {/* 优先用后端给的 sourceLabel (例如 "今日热榜 · 🔥 微博"), fallback 才用分类默认标签 */}
        <span className={`tc-source-badge tc-source--${(topic.sourceCategory || 'default').toLowerCase()}`}>
          {topic.sourceLabel || (sourceBadge.icon + ' ' + sourceBadge.label)}
        </span>
        <div className="tc-score-block">
          <div className="tc-score-bar-bg">
            <div
              className="tc-score-bar-fill"
              style={{ width: `${scorePct}%`, background: scoreColor }}
            />
          </div>
          <span className="tc-score-value" style={{ color: scoreColor }}>
            {scorePct}%
          </span>
        </div>
      </div>

      <h4 className="tc-card-title">{topic.title}</h4>

      {hasReason && <p className="tc-card-reason">{topic.reason}</p>}

      <div className="tc-card-tags">
        {hasInsuranceTypes && topic.insuranceTypes.map((t, i) => (
          <span key={`ins-${i}`} className="tc-card-tag tc-tag--insurance">{t}</span>
        ))}
        {hasDemographics && topic.demographics.map((d, i) => (
          <span key={`demo-${i}`} className="tc-card-tag tc-tag--demographic">{d}</span>
        ))}
      </div>

      {hasSourceUrl && (
        <a
          className="tc-card-source-link"
          href={topic.sourceUrl}
          target="_blank"
          rel="noreferrer"
          onClick={(e) => e.stopPropagation()}
          title={topic.sourceUrl}
        >
          ↗ 查看原文 / 热点来源
        </a>
      )}
    </div>
  )
}

function getSourceBadge(topic) {
  const cat = topic.sourceCategory || ''
  if (cat === 'HOT_TEMPLATE') return { icon: '📌', label: '热门模板' }
  if (cat === 'KNOWLEDGE_BASE') return { icon: '📚', label: '知识库' }
  if (cat === 'NEWS_HOTSPOT') return { icon: '🔥', label: '热点追踪' }
  if (cat === 'USER_WRITE') return { icon: '✏️', label: '自定义' }
  return { icon: '📋', label: '推荐选题' }
}
