import { useState, useEffect, useCallback } from 'react'
import { fetchDailyTopics } from '../api'
import CreateTopicModal from '../components/CreateTopicModal'
import './TopicSquarePage.css'

const INSURANCE_TYPES = ['重疾险', '医疗险', '意外险', '寿险', '养老险', '车险', '财产险', '理财险']
const DEMOGRAPHICS = ['年轻人', '中年人', '老年人', '父母', '宝爸宝妈', '孩子', '上班族', '自由职业者', '创业者', '学生', '家庭主妇']

export default function TopicSquarePage({ onNavigate }) {
  const [topics, setTopics] = useState([])
  const [loading, setLoading] = useState(true)
  const [insuranceFilter, setInsuranceFilter] = useState([])
  const [demographicFilter, setDemographicFilter] = useState([])
  const [selectedTopic, setSelectedTopic] = useState(null)
  const [showCustomPanel, setShowCustomPanel] = useState(false)
  const [customTitle, setCustomTitle] = useState('')

  const loadTopics = useCallback(async () => {
    setLoading(true)
    try {
      const payload = {}
      if (insuranceFilter.length > 0) payload.insuranceTypesFilter = insuranceFilter
      if (demographicFilter.length > 0) payload.demographicsFilter = demographicFilter
      const data = await fetchDailyTopics(payload)
      setTopics(data.items || [])
    } catch (e) {
      console.error('Failed to load topics:', e)
      setTopics([])
    } finally {
      setLoading(false)
    }
  }, [insuranceFilter, demographicFilter])

  useEffect(() => {
    loadTopics()
  }, [loadTopics])

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
            <p className="topics-subtitle">每日推荐 · 基于热门模板和实时热点生成</p>
          </div>
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
      </div>

      {loading ? (
        <div className="topics-loading">
          <div className="loading-dots">
            <span></span><span></span><span></span>
          </div>
          <p>正在生成选题…</p>
        </div>
      ) : topics.length === 0 ? (
        <div className="topics-empty">
          <p>暂无匹配的选题，试试调整筛选条件</p>
          {activeFilters > 0 && (
            <button className="topics-filter-clear" onClick={clearFilters} style={{ marginTop: 12 }}>
              清除筛选
            </button>
          )}
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

  return (
    <div className="topic-card" onClick={onClick}>
      <div className="tc-top-row">
        <span className={`tc-source-badge tc-source--${(topic.sourceCategory || 'default').toLowerCase()}`}>
          {sourceBadge.icon} {sourceBadge.label}
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
