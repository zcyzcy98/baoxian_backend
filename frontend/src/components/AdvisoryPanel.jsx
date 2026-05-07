import { useEffect, useRef, useState } from 'react'
import {
  analyzeCustomer,
  createAdvisorySession,
  deleteAdvisorySession,
  fetchAdvisorySession,
  fetchAdvisorySessions,
} from '../api'
import './AdvisoryPanel.css'

const CHANNELS = ['微信', '抖音私信', '小红书评论', '视频号', '公众号', '线下', '电话']

export default function AdvisoryPanel() {
  const [sessions, setSessions] = useState([])
  const [activeSessionId, setActiveSessionId] = useState(null)
  const [currentSession, setCurrentSession] = useState(null)
  const [currentAnalysis, setCurrentAnalysis] = useState(null)
  const [loading, setLoading] = useState(false)
  const [customerInfo, setCustomerInfo] = useState('')
  const [question, setQuestion] = useState('')
  const [channel, setChannel] = useState('微信')
  const [activeTab, setActiveTab] = useState('stable')
  const [search, setSearch] = useState('')
  const scrollRef = useRef(null)

  useEffect(() => { loadSessions() }, [])

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight
    }
  }, [currentSession?.messages, loading])

  const loadSessions = async () => {
    try {
      const list = await fetchAdvisorySessions()
      setSessions(list)
    } catch (e) {
      console.error(e)
    }
  }

  const selectSession = async (id) => {
    setActiveSessionId(id)
    setCurrentAnalysis(null)
    try {
      const session = await fetchAdvisorySession(id)
      setCurrentSession(session)
      // Show the last analysis if exists
      const msgs = session.messages || []
      if (msgs.length > 0) setCurrentAnalysis(msgs[msgs.length - 1])
    } catch (e) {
      console.error(e)
    }
  }

  const handleNewSession = async () => {
    const name = window.prompt('请输入客户姓名或代号：')
    if (!name || !name.trim()) return
    try {
      const session = await createAdvisorySession(name.trim())
      await loadSessions()
      await selectSession(session.id)
    } catch (e) {
      alert('创建失败：' + e.message)
    }
  }

  const handleDelete = async (e, id) => {
    e.stopPropagation()
    if (!window.confirm('确认删除该客户的所有记录？')) return
    try {
      await deleteAdvisorySession(id)
      if (activeSessionId === id) {
        setActiveSessionId(null)
        setCurrentSession(null)
        setCurrentAnalysis(null)
      }
      await loadSessions()
    } catch (e) {
      alert('删除失败：' + e.message)
    }
  }

  const handleAnalyze = async () => {
    if (!activeSessionId) return alert('请先选择或新建一个客户')
    if (!customerInfo.trim()) return alert('请填写客户基本情况')
    if (!question.trim()) return alert('请填写客户的问题')
    setLoading(true)
    try {
      const result = await analyzeCustomer(activeSessionId, customerInfo, question, channel)
      setCurrentAnalysis(result)
      setActiveTab('stable')
      const updated = await fetchAdvisorySession(activeSessionId)
      setCurrentSession(updated)
      await loadSessions()
    } catch (e) {
      alert('分析失败：' + e.message)
    } finally {
      setLoading(false)
    }
  }

  const filteredSessions = sessions.filter((s) =>
    !search || s.name.includes(search) || (s.lastQuestion || '').includes(search),
  )

  const anxietyDots = (level) => {
    const n = Math.min(5, Math.max(1, level || 3))
    return Array.from({ length: 5 }, (_, i) => (
      <span key={i} className={`anxiety-dot ${i < n ? 'active' : ''}`} />
    ))
  }

  return (
    <div className="advisory-panel">
      {/* ── Left: Customer List ── */}
      <div className="adv-left">
        <div className="adv-left-header">
          <button className="btn-new-session" onClick={handleNewSession}>＋ 新建客户</button>
          <input
            className="adv-search"
            placeholder="搜索客户…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <div className="adv-session-list">
          {filteredSessions.length === 0 && (
            <div className="adv-empty-hint">还没有客户记录，点击上方新建</div>
          )}
          {filteredSessions.map((s) => (
            <div
              key={s.id}
              className={`adv-session-item ${activeSessionId === s.id ? 'active' : ''}`}
              onClick={() => selectSession(s.id)}
            >
              <div className="adv-session-avatar">{s.name[0]}</div>
              <div className="adv-session-info">
                <div className="adv-session-name">{s.name}</div>
                <div className="adv-session-preview">{s.lastQuestion || s.summary || '暂无记录'}</div>
              </div>
              {s.lastEmotion && <div className="adv-session-emotion">{s.lastEmotion}</div>}
              <button className="adv-delete-btn" onClick={(e) => handleDelete(e, s.id)} title="删除">×</button>
            </div>
          ))}
        </div>
      </div>

      {/* ── Center: Conversation + Input ── */}
      <div className="adv-center">
        <div className="adv-center-header">
          {currentSession ? (
            <>
              <div className="adv-customer-avatar">{currentSession.name[0]}</div>
              <div>
                <div className="adv-customer-name">{currentSession.name}</div>
                {currentSession.summary && (
                  <div className="adv-customer-summary">{currentSession.summary}</div>
                )}
              </div>
            </>
          ) : (
            <div className="adv-no-session">← 选择客户，或新建一个开始答疑</div>
          )}
        </div>

        <div className="adv-conversation" ref={scrollRef}>
          {(currentSession?.messages || []).map((msg) => (
            <div key={msg.id} className="adv-msg-group">
              <div className="adv-msg-bubble adv-msg-customer">
                {msg.channel && <span className="adv-msg-channel">{msg.channel}</span>}
                {msg.customerInfo && (
                  <div className="adv-msg-info-line">
                    <span className="adv-label">客户情况</span>
                    <span>{msg.customerInfo}</span>
                  </div>
                )}
                <div className="adv-msg-info-line">
                  <span className="adv-label">客户问题</span>
                  <span className="adv-msg-question">"{msg.question}"</span>
                </div>
              </div>
              {msg.trueIntent && (
                <div className="adv-msg-bubble adv-msg-analysis">
                  <div className="adv-analysis-brief">
                    <span className="adv-brief-icon">◉</span>
                    <div>
                      <div className="adv-brief-emotion">{msg.emotionState}</div>
                      <div className="adv-brief-intent">{msg.trueIntent}</div>
                    </div>
                  </div>
                </div>
              )}
            </div>
          ))}
          {loading && (
            <div className="adv-msg-bubble adv-msg-analysis adv-loading">
              <span className="adv-spinner" /> 正在分析客户意图…
            </div>
          )}
        </div>

        {/* Input area */}
        <div className="adv-input-area">
          <div className="adv-input-row">
            <div className="adv-input-block">
              <label className="adv-input-label">客户基本情况 <span className="required">*</span></label>
              <textarea
                className="adv-textarea"
                rows={3}
                placeholder="如：32岁宝妈，互联网行业，年收入约80万，已购百万医疗险"
                value={customerInfo}
                onChange={(e) => setCustomerInfo(e.target.value)}
                disabled={loading}
              />
            </div>
            <div className="adv-input-block">
              <label className="adv-input-label">客户的问题 <span className="required">*</span></label>
              <textarea
                className="adv-textarea"
                rows={3}
                placeholder="直接粘贴客户的原话最好"
                value={question}
                onChange={(e) => setQuestion(e.target.value)}
                disabled={loading}
              />
            </div>
          </div>
          <div className="adv-input-footer">
            <div className="adv-channels">
              {CHANNELS.map((c) => (
                <button
                  key={c}
                  type="button"
                  className={`adv-channel-btn ${channel === c ? 'active' : ''}`}
                  onClick={() => setChannel(c)}
                  disabled={loading}
                >
                  {c}
                </button>
              ))}
            </div>
            <button
              className="btn-analyze"
              onClick={handleAnalyze}
              disabled={loading || !activeSessionId}
            >
              {loading ? '分析中…' : '生成应对 →'}
            </button>
          </div>
        </div>
      </div>

      {/* ── Right: Analysis Panel ── */}
      <div className={`adv-right ${currentAnalysis ? 'has-content' : ''}`}>
        {!currentAnalysis ? (
          <div className="adv-right-empty">提交问题后，分析结果将显示在这里</div>
        ) : (
          <>
            <div className="adv-right-header">当前应对方案分析</div>

            {/* 意图分析 */}
            <div className="adv-card">
              <div className="adv-card-title">
                <span className="adv-card-icon">◎</span> 客户意图分析
                <span className="adv-card-sub">— Intent</span>
              </div>
              {currentAnalysis.surfaceQuestion && (
                <div className="adv-intent-row">
                  <span className="adv-intent-label">表面问题</span>
                  <span>{currentAnalysis.surfaceQuestion}</span>
                </div>
              )}
              <div className="adv-intent-row">
                <span className="adv-intent-label">真实意图</span>
                <span>{currentAnalysis.trueIntent}</span>
              </div>
              {currentAnalysis.emotionState && (
                <div className="adv-intent-row adv-emotion-row">
                  <span className="adv-intent-label">情绪状态</span>
                  <span className="adv-emotion-tag">{currentAnalysis.emotionState}</span>
                  <div className="adv-anxiety-dots">
                    {anxietyDots(currentAnalysis.anxietyLevel)}
                  </div>
                  <span className="adv-anxiety-label">{currentAnalysis.anxietyLevel} / 5</span>
                </div>
              )}
            </div>

            {/* 应对方案 */}
            <div className="adv-card">
              <div className="adv-card-title">
                <span className="adv-card-icon">□</span> 应对方案
                <span className="adv-card-sub">— Response</span>
              </div>
              <div className="adv-tabs">
                {[
                  { key: 'stable', label: '稳妥版', sub: '先建立信任' },
                  { key: 'deep', label: '深度版', sub: '展示专业' },
                  { key: 'close', label: '促单版', sub: '引导成交' },
                ].map((t) => (
                  <button
                    key={t.key}
                    className={`adv-tab ${activeTab === t.key ? 'active' : ''}`}
                    onClick={() => setActiveTab(t.key)}
                  >
                    {t.label}
                    <span className="adv-tab-sub">{t.sub}</span>
                  </button>
                ))}
              </div>
              <div className="adv-response-content">
                {activeTab === 'stable' && currentAnalysis.responseStable}
                {activeTab === 'deep' && currentAnalysis.responseDeep}
                {activeTab === 'close' && currentAnalysis.responseClose}
              </div>
            </div>

            {/* 接下来怎么走 */}
            {currentAnalysis.nextSteps && currentAnalysis.nextSteps.length > 0 && (
              <div className="adv-card">
                <div className="adv-card-title">
                  <span className="adv-card-icon">↗</span> 接下来该怎么走
                  <span className="adv-card-sub">— Next steps</span>
                </div>
                <div className="adv-steps">
                  {currentAnalysis.nextSteps.map((step, i) => (
                    <div key={i} className="adv-step">
                      <span className="adv-step-num">第{i + 1}步</span>
                      <span>{step}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
