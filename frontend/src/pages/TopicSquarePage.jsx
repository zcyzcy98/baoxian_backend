import { useState, useEffect, useCallback } from 'react'
import { fetchDailyTopics, searchHotTopics } from '../api'
import CreateTopicModal from '../components/CreateTopicModal'
import './TopicSquarePage.css'

const INSURANCE_TYPES = ['重疾险', '医疗险', '意外险', '寿险', '养老险', '车险', '财产险', '理财险']
const DEMOGRAPHICS = ['年轻人', '中年人', '老年人', '宝妈', '上班族', '创业者', '学生', '中产']
const PLATFORMS = ['小红书', '抖音', '公众号', '视频号']
const LIMIT_OPTIONS = [10, 20, 30]

const CACHE_KEY = 'chengzhi:topics-cache-v2'
const FILTER_KEY = 'chengzhi:topics-filters-v2'

function loadCache() {
  try {
    const raw = localStorage.getItem(CACHE_KEY)
    return raw ? JSON.parse(raw) : { topics: [], lastFetchedAt: null }
  } catch { return { topics: [], lastFetchedAt: null } }
}

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

// score 0-100 → "X.X"
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
  const cache = loadCache()
  const persistedFilters = loadFilters()

  const [topics, setTopics] = useState(cache.topics || [])
  const [lastFetchedAt, setLastFetchedAt] = useState(cache.lastFetchedAt)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [insuranceFilter, setInsuranceFilter] = useState(persistedFilters.ins || [])
  const [demographicFilter, setDemographicFilter] = useState(persistedFilters.demo || [])
  const [platformFilter, setPlatformFilter] = useState(persistedFilters.plat || [])
  const [limitCount, setLimitCount] = useState(20)
  const [selectedTopic, setSelectedTopic] = useState(null)
  const [showCustomPanel, setShowCustomPanel] = useState(false)
  const [customTitle, setCustomTitle] = useState('')
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

  const loadTopics = useCallback(async (count = limitCount) => {
    if (loading) return
    setLoading(true)
    setError('')
    setSearchMode(false)
    try {
      const payload = { categories: ['hotspot'], limit: count }
      const data = await fetchDailyTopics(payload)
      const items = data.items || []
      const fetchedAt = new Date().toISOString()
      setTopics(items)
      setLastFetchedAt(fetchedAt)
      localStorage.setItem(CACHE_KEY, JSON.stringify({ topics: items, lastFetchedAt: fetchedAt }))
    } catch (e) {
      setError(e?.message || '拉取失败')
    } finally {
      setLoading(false)
    }
  }, [loading, limitCount])

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
      if (items.length === 0) setError(`关键词 "${kw}" 没有匹配的热点，换个词试试`)
    } catch (e) {
      setError(e?.message || '搜索失败')
    } finally {
      setSearching(false)
    }
  }

  // 前端筛选（不重新请求，直接过滤已加载数据）
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
  })

  const toggleFilter = (arr, setArr, val) => {
    setArr(prev => prev.includes(val) ? prev.filter(v => v !== val) : [...prev, val])
  }

  const clearFilters = () => {
    setInsuranceFilter([])
    setDemographicFilter([])
    setPlatformFilter([])
  }

  const activeFilterCount = insuranceFilter.length + demographicFilter.length + platformFilter.length

  const handleCustomTopicCreate = () => {
    if (!customTitle.trim()) return
    setSelectedTopic({
      id: 'user-' + Date.now(),
      title: customTitle.trim(),
      reason: '用户自定义选题，立即开始创作',
      angle: '',
      sourceCategory: 'USER_WRITE',
      score: 100,
    })
  }

  const handleLimitChange = (count) => {
    setLimitCount(count)
    loadTopics(count)
  }

  const topTopic = filteredTopics[0]

  return (
    <div className="tsq-page">
      {/* ── 顶部 Header ── */}
      <div className="tsq-header">
        <div className="tsq-header-left">
          <h2 className="tsq-title">选题广场</h2>
          <p className="tsq-subtitle">
            AI 筛选 · TopHub 实时热点 · 保险内容专属推荐
            {lastFetchedAt && (
              <span className="tsq-fetch-time"> · {formatRelativeTime(lastFetchedAt)}更新</span>
            )}
          </p>
        </div>
        <div className="tsq-header-right">
          <button className="tsq-btn-refresh" onClick={() => loadTopics()} disabled={loading}>
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.3" strokeLinecap="round"
              style={loading ? { animation: 'tsqSpin 0.9s linear infinite' } : {}}>
              <polyline points="23 4 23 10 17 10"/>
              <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
            </svg>
            {loading ? '拉取中…' : topics.length === 0 ? '加载选题' : '刷新数据'}
          </button>
        </div>
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
            <button className="tsq-search-back" onClick={() => loadTopics()}>
              ← 返回推荐
            </button>
          )}
        </div>
      </div>

      {/* ── 筛选栏 ── */}
      <div className="tsq-filter-bar">
        <div className="tsq-filter-groups">
          <FilterGroup label="险种" options={INSURANCE_TYPES} active={insuranceFilter} onToggle={v => toggleFilter(insuranceFilter, setInsuranceFilter, v)} />
          <FilterGroup label="客群" options={DEMOGRAPHICS} active={demographicFilter} onToggle={v => toggleFilter(demographicFilter, setDemographicFilter, v)} />
          <FilterGroup label="平台" options={PLATFORMS} active={platformFilter} onToggle={v => toggleFilter(platformFilter, setPlatformFilter, v)} />
        </div>

        <div className="tsq-filter-right">
          {activeFilterCount > 0 && (
            <button className="tsq-clear-filter" onClick={clearFilters}>清除筛选 ({activeFilterCount})</button>
          )}
          <span className="tsq-result-count">{filteredTopics.length} 个结果</span>

          {/* 10/20/30 选择 */}
          <div className="tsq-limit-group">
            {LIMIT_OPTIONS.map(n => (
              <button
                key={n}
                className={`tsq-limit-btn ${limitCount === n ? 'active' : ''}`}
                onClick={() => handleLimitChange(n)}
                disabled={loading}
              >
                {n}条
              </button>
            ))}
          </div>

          {/* 一键创作 Top 选题 */}
          {topTopic && (
            <button className="tsq-btn-top-create" onClick={() => setSelectedTopic(topTopic)}>
              <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/></svg>
              一键创作 Top 选题
            </button>
          )}

          {/* 自定义选题 */}
          <button className="tsq-btn-custom" onClick={() => setShowCustomPanel(!showCustomPanel)}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
              <path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/>
            </svg>
            指定选题
          </button>
        </div>
      </div>

      {/* ── 自定义选题输入 ── */}
      {showCustomPanel && (
        <div className="tsq-custom-panel">
          <p className="tsq-custom-label">输入你的选题想法，直接进入创作</p>
          <div className="tsq-custom-row">
            <input
              className="tsq-custom-input"
              type="text"
              placeholder="例如：30岁买保险的注意事项..."
              value={customTitle}
              onChange={e => setCustomTitle(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && handleCustomTopicCreate()}
              autoFocus
            />
            <button className="tsq-custom-submit" disabled={!customTitle.trim()} onClick={handleCustomTopicCreate}>
              开始创作
            </button>
          </div>
        </div>
      )}

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
              <p className="tsq-empty-desc">点击"加载选题"，AI 将从 TopHub 实时热榜中筛选最适合保险内容创作的热点</p>
              <button className="tsq-empty-btn" onClick={() => loadTopics()}>立即加载</button>
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

function FilterGroup({ label, options, active, onToggle }) {
  return (
    <div className="tsq-filter-group">
      <span className="tsq-filter-label">{label}</span>
      <div className="tsq-filter-chips">
        {options.map(opt => (
          <button
            key={opt}
            className={`tsq-chip ${active.includes(opt) ? 'active' : ''}`}
            onClick={() => onToggle(opt)}
          >
            {opt}
            {active.includes(opt) && <span className="tsq-chip-count">✓</span>}
          </button>
        ))}
      </div>
    </div>
  )
}

function TopicCard({ topic, rank, onClick }) {
  const score = topic.score ?? 0
  const scoreStr = formatScore(score)
  const scoreNum = parseFloat(scoreStr)
  const isTop = rank === 0
  const isHigh = scoreNum >= 8.0
  const scoreMid = scoreNum >= 6.0

  const platforms = topic.recommendedPlatforms || []
  const audiences = topic.demographics || []
  const insuranceTypes = topic.insuranceTypes || []
  const hasSourceUrl = topic.sourceUrl && topic.sourceUrl.trim()
  const whyText = topic.whyThisTopic || topic.reason || ''

  return (
    <div className={`tsq-card ${isTop ? 'tsq-card--top' : ''}`} onClick={onClick}>
      {isTop && <div className="tsq-card-top-badge">TOP</div>}

      {/* 评分行 */}
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

      {/* 标题 */}
      <h4 className="tsq-card-title">{topic.title}</h4>

      {/* WHY THIS TOPIC */}
      {whyText && (
        <div className="tsq-why-block">
          <span className="tsq-why-label">— WHY THIS TOPIC</span>
          <p className="tsq-why-text">{whyText}</p>
        </div>
      )}

      {/* 险种标签 */}
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

      {/* 适合人群 */}
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

      {/* 发布平台 */}
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

      {/* 底部操作 */}
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
