import { useState, useEffect, useRef } from 'react'
import { fetchDailyTopics, searchHotTopics, refreshTopics, fetchUserProfile, fetchCreditsBalance } from '../api'
import CreateTopicModal from '../components/CreateTopicModal'
import './TopicSquarePage.css'

const INSURANCE_TYPES = ['重疾险', '医疗险', '意外险', '寿险', '养老险', '车险', '财产险', '理财险']
const DEMOGRAPHICS = ['年轻人', '中年人', '老年人', '宝妈', '上班族', '创业者', '学生', '中产']
const PLATFORMS = ['小红书', '抖音', '公众号', '视频号']
const DEFAULT_LIMIT = 18
const REFRESH_COST = 1

const FILTER_KEY = 'chengzhi:topics-filters-v2'

function loadFilters() {
  try {
    const raw = localStorage.getItem(FILTER_KEY)
    return raw ? JSON.parse(raw) : { ins: [], demo: [], plat: [] }
  } catch { return { ins: [], demo: [], plat: [] } }
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

function formatScore(score) {
  const v = Math.min(100, Math.max(0, score ?? 0))
  return (v / 10).toFixed(1)
}

const PLATFORM_COLORS = {
  '抖音': '#161823',
  '小红书': '#fe2c55',
  '公众号': '#07c160',
  '视频号': '#1677ff',
}

export default function TopicSquarePage({ onNavigate }) {
  const persistedFilters = loadFilters()

  const [topics, setTopics] = useState([])
  const [lastFetchedAt, setLastFetchedAt] = useState(null)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState('')

  const [userProfile, setUserProfile] = useState(null)
  const [creditsBalance, setCreditsBalance] = useState(null)

  const [insuranceFilter, setInsuranceFilter] = useState(persistedFilters.ins || [])
  const [demographicFilter, setDemographicFilter] = useState(persistedFilters.demo || [])
  const [platformFilter, setPlatformFilter] = useState(persistedFilters.plat || [])
  const [sourceFilter, setSourceFilter] = useState('NEWS_HOTSPOT') // 'HOT_TEMPLATE'=爆款库, 'NEWS_HOTSPOT'=热点库

  const [selectedTopic, setSelectedTopic] = useState(null)
  const [searchKeyword, setSearchKeyword] = useState('')
  const [searching, setSearching] = useState(false)
  const [searchMode, setSearchMode] = useState(false)

  useEffect(() => {
    localStorage.setItem(FILTER_KEY, JSON.stringify({
      ins: insuranceFilter,
      demo: demographicFilter,
      plat: platformFilter,
    }))
  }, [insuranceFilter, demographicFilter, platformFilter])

  useEffect(() => {
    loadPersonalized('NEWS_HOTSPOT')
  }, [])

  const loadPersonalized = async (sourceCategory) => {
    setLoading(true)
    setError('')
    setSearchMode(false)
    try {
      const [profile, balance] = await Promise.allSettled([
        fetchUserProfile(),
        fetchCreditsBalance(),
      ])
      if (profile.status === 'fulfilled') setUserProfile(profile.value)
      if (balance.status === 'fulfilled') setCreditsBalance(balance.value.balance ?? balance.value.credits ?? 0)

      const profileData = profile.status === 'fulfilled' ? profile.value : null
      const payload = { categories: ['hotspot'], limit: DEFAULT_LIMIT }
      if (sourceCategory) payload.sourceCategory = sourceCategory
      if (profileData) {
        payload.profile = {
          primaryProducts: profileData.primaryProducts || [],
          targetAudiences: profileData.targetAudiences || [],
          style: profileData.style || '',
        }
      }
      const data = await fetchDailyTopics(payload)
      const items = data.items || []
      setTopics(items)
      setLastFetchedAt(new Date().toISOString())
    } catch (e) {
      setError(e?.message || '拉取失败')
    } finally {
      setLoading(false)
    }
  }

  const handleRefresh = async () => {
    if (refreshing) return
    setRefreshing(true)
    setError('')
    try {
      const payload = { limit: DEFAULT_LIMIT }
      if (userProfile) {
        payload.profile = {
          primaryProducts: userProfile.primaryProducts || [],
          targetAudiences: userProfile.targetAudiences || [],
          style: userProfile.style || '',
        }
      }
      const data = await refreshTopics(payload)
      const items = data.items || []
      setTopics(items)
      setLastFetchedAt(new Date().toISOString())
      if (data.balance !== undefined) setCreditsBalance(data.balance)
      else if (creditsBalance !== null) setCreditsBalance(c => Math.max(0, c - REFRESH_COST))
    } catch (e) {
      if (e?.code === 'INSUFFICIENT_CREDITS') {
        setError('积分不足，无法刷新数据（需要 1 积分）。请充值后重试。')
      } else {
        setError(e?.message || '刷新失败')
      }
    } finally {
      setRefreshing(false)
    }
  }

  const handleSearchHotspots = async () => {
    const kw = searchKeyword.trim()
    if (!kw || searching) return
    setSearching(true)
    setError('')
    try {
      const profilePayload = userProfile ? {
        primaryProducts: userProfile.primaryProducts || [],
        targetAudiences: userProfile.targetAudiences || [],
        style: userProfile.style || '',
      } : null
      const data = await searchHotTopics(kw, 50, '', profilePayload)
      const items = data.items || []
      setTopics(items)
      setSearchMode(true)
      setLastFetchedAt(new Date().toISOString())
      if (items.length === 0) setError(`关键词 "${kw}" 没有匹配的热点，换个词试试`)
    } catch (e) {
      setError(e?.message || '搜索失败')
    } finally {
      setSearching(false)
    }
  }

  const filteredTopics = topics.filter(t => {
    if (insuranceFilter.length > 0) {
      const types = t.insuranceTypes || []
      if (!insuranceFilter.some(f => types.includes(f))) return false
    }
    if (demographicFilter.length > 0) {
      const demos = t.demographics || []
      if (!demographicFilter.some(f => demos.includes(f))) return false
    }
    if (platformFilter.length > 0) {
      const plats = t.recommendedPlatforms || []
      if (!platformFilter.some(f => plats.includes(f))) return false
    }
    return true
  }).sort((a, b) => (b.score || 0) - (a.score || 0))

  const toggleFilter = (arr, setArr, val) => {
    setArr(prev => prev.includes(val) ? prev.filter(v => v !== val) : [...prev, val])
  }

  const clearFilters = () => {
    setInsuranceFilter([])
    setDemographicFilter([])
    setPlatformFilter([])
  }

  const activeFilterCount = insuranceFilter.length + demographicFilter.length + platformFilter.length

  const userName = userProfile?.name || userProfile?.phone || '保险代理人'
  const greeting = getGreeting()

  return (
    <div className="tsq-page">
      {/* ── 欢迎横幅 + 刷新 ── */}
      <div className="tsq-welcome-banner">
        <div className="tsq-welcome-left">
          <div className="tsq-welcome-avatar">
            {userProfile?.avatarUrl ? (
              <img src={userProfile.avatarUrl} alt="" className="tsq-avatar-img" referrerPolicy="no-referrer" />
            ) : (
              (userName || '代')[0]
            )}
          </div>
          <div className="tsq-welcome-text">
            <p className="tsq-welcome-greeting">{greeting}，{userName}</p>
            <p className="tsq-welcome-sub">
              已为你智能推荐个性化选题，快去创作吧！
            </p>
            {lastFetchedAt && (
              <div className="tsq-welcome-meta">
                <span className="tsq-fetch-time">{formatRelativeTime(lastFetchedAt)}更新</span>
              </div>
            )}
          </div>
        </div>
        <button
          className={`tsq-btn-refresh ${refreshing ? 'loading' : ''}`}
          onClick={handleRefresh}
          disabled={refreshing || loading}
        >
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.3" strokeLinecap="round"
            style={refreshing ? { animation: 'tsqSpin 0.9s linear infinite' } : {}}>
            <polyline points="23 4 23 10 17 10"/>
            <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
          </svg>
          {refreshing ? '刷新中…' : '刷新数据'}
          <span className="tsq-credit-cost">-{REFRESH_COST}积分</span>
        </button>
      </div>

      {/* ── 搜索栏 ── */}
      <div className="tsq-search-row">
        <div className="tsq-search-wrap">
          <svg className="tsq-search-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round">
            <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
          </svg>
          <input
            className="tsq-search-input"
            type="text"
            placeholder="搜索热点关键词（如：医保、延迟退休、重疾）..."
            value={searchKeyword}
            onChange={e => setSearchKeyword(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleSearchHotspots()}
            disabled={searching}
          />
          <button className="tsq-search-btn" onClick={handleSearchHotspots} disabled={!searchKeyword.trim() || searching}>
            {searching ? '搜索中…' : '搜热点'}
          </button>
          {searchMode && (
            <button className="tsq-search-back" onClick={() => loadPersonalized(sourceFilter)}>
              ← 返回推荐
            </button>
          )}
        </div>
      </div>

      {/* ── 筛选栏 ── */}
      <div className="tsq-filter-bar">
        <div className="tsq-filter-chips-row">
          <div className="tsq-filter-left">
            <button className={`tsq-source-btn ${sourceFilter === 'NEWS_HOTSPOT' ? 'active' : ''}`} onClick={() => { setSourceFilter('NEWS_HOTSPOT'); loadPersonalized('NEWS_HOTSPOT') }}>热点库</button>
            <button className={`tsq-source-btn ${sourceFilter === 'HOT_TEMPLATE' ? 'active' : ''}`} onClick={() => { setSourceFilter('HOT_TEMPLATE'); loadPersonalized('HOT_TEMPLATE') }}>爆款库</button>
          </div>
          <div className="tsq-filter-right">
            <FilterDropdown label="险种" options={INSURANCE_TYPES} active={insuranceFilter} onToggle={v => toggleFilter(insuranceFilter, setInsuranceFilter, v)} />
            <FilterDropdown label="客群" options={DEMOGRAPHICS} active={demographicFilter} onToggle={v => toggleFilter(demographicFilter, setDemographicFilter, v)} />
            <FilterDropdown label="平台" options={PLATFORMS} active={platformFilter} onToggle={v => toggleFilter(platformFilter, setPlatformFilter, v)} />
          </div>
        </div>

        <div className="tsq-filter-right">
          {activeFilterCount > 0 && (
            <button className="tsq-clear-filter" onClick={clearFilters}>清除筛选 ({activeFilterCount})</button>
          )}
          <span className="tsq-result-count">{filteredTopics.length} 个结果</span>

        </div>
      </div>

      {error && (
        <div className="tsq-error">
          ⚠️ {error}
          <button onClick={() => setError('')}>×</button>
        </div>
      )}

      {/* ── 内容区 ── */}
      {loading ? (
        <div className="tsq-loading">
          <div className="tsq-loading-dots"><span/><span/><span/></div>
          <p>AI 正在筛选分析热点，稍等一下…</p>
        </div>
      ) : filteredTopics.length === 0 ? (
        <div className="tsq-empty">
          {topics.length === 0 ? (
            <>
              <div className="tsq-empty-icon">⚡</div>
              <p className="tsq-empty-title">还没有选题</p>
              <p className="tsq-empty-desc">AI 正在为你智能推荐个性化选题，请稍后刷新</p>
              <button className="tsq-empty-btn" onClick={handleRefresh} disabled={refreshing}>
                刷新数据（-{REFRESH_COST}积分）
              </button>
            </>
          ) : (
            <>
              <div className="tsq-empty-icon">🔍</div>
              <p className="tsq-empty-title">筛选结果为空</p>
              <p className="tsq-empty-desc">当前筛选条件下没有匹配的选题，试试调整险种或客群筛选</p>
              <button className="tsq-empty-btn" onClick={clearFilters}>清除筛选</button>
            </>
          )}
        </div>
      ) : (
        <div className="tsq-grid">
          {filteredTopics.map((topic, i) => (
            <TopicCard
              key={topic.id || i}
              topic={topic}
              rank={i}
              onClick={() => setSelectedTopic(topic)}
            />
          ))}
        </div>
      )}

      {selectedTopic && (
        <CreateTopicModal
          topic={selectedTopic}
          onClose={() => setSelectedTopic(null)}
          onNavigate={onNavigate}
        />
      )}
    </div>
  )
}

function getGreeting() {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 9) return '早上好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  if (h < 22) return '晚上好'
  return '夜深了'
}

function FilterDropdown({ label, options, active, onToggle }) {
  const [open, setOpen] = useState(false)
  const ref = useRef(null)

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (ref.current && !ref.current.contains(e.target)) {
        setOpen(false)
      }
    }
    if (open) {
      document.addEventListener('mousedown', handleClickOutside)
    }
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [open])

  const valueText = active.length === 0
    ? '全部'
    : active.length === 1
      ? active[0]
      : `${active[0]} +${active.length - 1}`

  return (
    <div className={`tsq-filter-chip ${active.length > 0 ? 'active' : ''}`} ref={ref}>
      <button className="tsq-filter-chip-btn" onClick={(e) => { e.stopPropagation(); setOpen(!open) }}>
        <span className="tsq-filter-chip-label">{label}</span>
        <span className="tsq-filter-chip-val">{valueText}</span>
        <svg className={`tsq-filter-chip-chevron ${open ? 'open' : ''}`} width="10" height="6" viewBox="0 0 10 6" fill="none">
          <path d="M1 1l4 4 4-4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </button>
      {open && (
        <div className="tsq-filter-menu">
          {options.map(opt => (
            <div
              key={opt}
              className={`tsq-filter-menu-item ${active.includes(opt) ? 'checked' : ''}`}
              onClick={(e) => { e.stopPropagation(); onToggle(opt) }}
            >
              <span className="tsq-filter-menu-check">
                {active.includes(opt) ? '✓' : ''}
              </span>
              {opt}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function TopicCard({ topic, rank, onClick }) {
  const score = topic.score ?? 0
  const scoreStr = formatScore(score)
  const scoreNum = parseFloat(scoreStr)
  const isHigh = scoreNum >= 8.0
  const scoreMid = scoreNum >= 6.0

  const platforms = topic.recommendedPlatforms || []
  const audiences = topic.demographics || []
  const insuranceTypes = topic.insuranceTypes || []
  const hasSourceUrl = topic.sourceUrl && topic.sourceUrl.trim()
  const whyText = topic.whyThisTopic || topic.reason || ''

  return (
    <div className="tsq-card" onClick={onClick}>

      <div className="tsq-card-header">
        <div className="tsq-score-block">
          <span className="tsq-score-icon">🔥</span>
          <span className={`tsq-score-num ${isHigh ? 'high' : scoreMid ? 'mid' : 'low'}`}>
            {scoreStr}
          </span>
          <span className="tsq-score-unit">/ HOT</span>
        </div>
        <div className="tsq-card-actions">
          {hasSourceUrl && (
            <a
              className="tsq-source-link"
              href={topic.sourceUrl}
              target="_blank"
              rel="noreferrer"
              onClick={e => e.stopPropagation()}
              title="查看原文"
            >
              ↗
            </a>
          )}
        </div>
      </div>

      <h4 className="tsq-card-title">{topic.title}</h4>

      {whyText && (
        <div className="tsq-why-block">
          <span className="tsq-why-label">— WHY THIS TOPIC</span>
          <p className="tsq-why-text">{whyText}</p>
        </div>
      )}

      {insuranceTypes.length > 0 && (
        <div className="tsq-tag-row">
          <span className="tsq-tag-icon ins-icon">🛡</span>
          <span className="tsq-tag-key">险种</span>
          <div className="tsq-tag-list">
            {insuranceTypes.map((t, i) => (
              <span key={i} className="tsq-tag tsq-tag--ins">{t}</span>
            ))}
          </div>
        </div>
      )}

      {audiences.length > 0 && (
        <div className="tsq-tag-row">
          <span className="tsq-tag-icon">👥</span>
          <span className="tsq-tag-key">适合</span>
          <div className="tsq-tag-list">
            {audiences.map((d, i) => (
              <span key={i} className="tsq-tag tsq-tag--demo">{d}</span>
            ))}
          </div>
        </div>
      )}

      {platforms.length > 0 && (
        <div className="tsq-tag-row">
          <span className="tsq-tag-icon">📱</span>
          <span className="tsq-tag-key">平台</span>
          <div className="tsq-tag-list">
            {platforms.map((p, i) => (
              <span key={i} className="tsq-tag tsq-tag--plat" style={{ background: PLATFORM_COLORS[p] || '#555' }}>
                {p}
              </span>
            ))}
          </div>
        </div>
      )}

      <div className="tsq-card-footer">
        <button className="tsq-btn-create" onClick={onClick}>
          开始创作 →
        </button>
        {topic.sourceLabel && (
          <span className="tsq-source-badge">{topic.sourceLabel}</span>
        )}
      </div>
    </div>
  )
}
