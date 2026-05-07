import { useEffect, useMemo, useState } from 'react'
import { fetchDailyTopics } from '../api'
import './TopicSquarePanel.css'

const SOURCE_LABELS = {
  TEMPLATE: { label: '飞书模板', icon: '表', color: '#526d45' },
  HOTSPOT:  { label: '飞书数据', icon: '表', color: '#526d45' },
  USER:     { label: '飞书数据', icon: '表', color: '#526d45' },
}

const CATEGORY_FILTERS = [
  { id: 'all',      label: '全部' },
  { id: 'bitable',  label: '飞书表' },
]

function loadProfile() {
  try {
    const raw = localStorage.getItem('topic-user-profile')
    if (!raw) return null
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export default function TopicSquarePanel({ onUseTopic }) {
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [meta, setMeta] = useState({ activeBitables: 0 })
  const [filter, setFilter] = useState('all')
  const profile = useMemo(loadProfile, [])

  const refresh = async (chosenFilter = filter) => {
    setLoading(true)
    setError('')
    try {
      const payload = {
        profile: profile || undefined,
        categories: chosenFilter === 'all' ? null : [chosenFilter],
        limit: 30,
      }
      const data = await fetchDailyTopics(payload)
      setItems(data?.items || [])
      setMeta({
        activeBitables: data?.activeBitables || 0,
      })
    } catch (err) {
      setError(err?.message || '加载失败')
      setItems([])
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { refresh('all') /* eslint-disable-line */ }, [])

  const handleFilter = (id) => {
    setFilter(id)
    refresh(id)
  }

  return (
    <div className="topic-square">
      <header className="topic-header">
        <div>
          <h2>今日选题广场</h2>
          <p className="topic-sub">
            只展示已接入飞书多维表格中的数据, 自动按"爆款概率"打分;
            点 <b>用这个选题</b> 一键跳到对应智能体生成内容。
          </p>
        </div>
        <div className="topic-actions">
          <button onClick={() => refresh()} disabled={loading} className="btn-refresh">
            {loading ? '加载中…' : '🔄 刷新'}
          </button>
        </div>
      </header>

      {meta.activeBitables === 0 && (
        <div className="topic-banner">
          <b>当前数据状态:</b>
          {' '}飞书表 <b>0</b> 张已接入。请在 <code>bitable-config.json</code> 中配置
          <code> appToken</code>、<code>tableId</code> 和 <code>kind</code>。
        </div>
      )}

      <div className="topic-filters">
        {CATEGORY_FILTERS.map((f) => (
          <button
            key={f.id}
            className={`filter-chip ${filter === f.id ? 'active' : ''}`}
            onClick={() => handleFilter(f.id)}
          >
            {f.label}
          </button>
        ))}
        {profile && (
          <span className="profile-pill">
            👤 {profile.primaryProducts?.join('/') || '全险种'}
            {profile.targetAudiences?.length ? ` · ${profile.targetAudiences.join('/')}` : ''}
          </span>
        )}
      </div>

      {error && <div className="topic-error">{error}</div>}

      <div className="topic-grid">
        {items.map((c) => (
          <TopicCard key={c.id} candidate={c} onUse={() => onUseTopic?.(c)} />
        ))}
        {!loading && items.length === 0 && !error && (
          <div className="topic-empty">没有飞书数据。请先配置并确认多维表格可以读取。</div>
        )}
      </div>
    </div>
  )
}

function TopicCard({ candidate, onUse }) {
  const meta = SOURCE_LABELS[candidate.source] || SOURCE_LABELS.USER
  return (
    <div className="topic-card" style={{ borderTopColor: meta.color }}>
      <div className="topic-card-top">
        <span className="topic-source" style={{ background: meta.color }}>
          {candidate.sourceLabel || meta.label}
        </span>
        <span className="topic-score">{candidate.score}</span>
      </div>
      <h3 className="topic-card-title">{candidate.title}</h3>
      {candidate.angle && <p className="topic-card-angle">{candidate.angle}</p>}
      {candidate.tags?.length > 0 && (
        <div className="topic-tags">
          {candidate.tags.slice(0, 5).map((t, i) => (
            <span key={`${t}-${i}`} className="topic-tag">{t}</span>
          ))}
        </div>
      )}
      <div className="topic-card-bottom">
        {candidate.sourceUrl && (
          <a className="topic-link" href={candidate.sourceUrl} target="_blank" rel="noreferrer">
            查看原文 ↗
          </a>
        )}
        <button className="btn-use" onClick={onUse}>用这个选题 →</button>
      </div>
    </div>
  )
}
