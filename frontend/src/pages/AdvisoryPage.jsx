import { useEffect, useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {
  fetchAdvisorySessions,
  fetchAdvisorySession,
  createAdvisorySession,
  analyzeCustomer,
  deleteAdvisorySession,
} from '../api'
import { copyToClipboard, stripMarkdown } from '../utils/markdown'
import './AdvisoryPage.css'

const STRATEGY_TABS = [
  { id: 'stable',  label: '稳妥版', field: 'responseStable',  desc: '先建立信任不急推销' },
  { id: 'deep',    label: '深度版', field: 'responseDeep',    desc: '专业知识精准解答' },
  { id: 'close',   label: '促单版', field: 'responseClose',   desc: '引导成交或续费' },
]

const CHANNEL_OPTIONS = [
  { id: 'weixin',  label: '微信' },
  { id: 'xhs',     label: '小红书' },
  { id: 'douyin',  label: '抖音' },
  { id: 'phone',   label: '电话' },
  { id: 'offline', label: '线下' },
]

export default function AdvisoryPage({
  contentPrefill, onContentPrefillConsumed,
  onNavigate, onNavigateWithContentPrefill,
}) {
  const ST = {
    sessions: [],
    activeId: null,
    activeData: null,
    loadingList: false,
    loadingDetail: false,
    showNew: false,
    search: '',
    customerInfo: '',
    question: '',
    channel: '',
    analyzing: false,
    error: '',
    tab: '',
    pickedIdx: null,
  }
  const [sessions, setSessions] = useState(ST.sessions)
  const [activeId, setActiveId] = useState(ST.activeId)
  const [activeData, setActiveData] = useState(ST.activeData)
  const [loadingList, setLoadingList] = useState(ST.loadingList)
  const [loadingDetail, setLoadingDetail] = useState(ST.loadingDetail)
  const [showNew, setShowNew] = useState(ST.showNew)
  const [search, setSearch] = useState(ST.search)
  const [customerInfo, setCustomerInfo] = useState(ST.customerInfo)
  const [question, setQuestion] = useState(ST.question)
  const [channel, setChannel] = useState(ST.channel)
  const [analyzing, setAnalyzing] = useState(ST.analyzing)
  const [error, setError] = useState(ST.error)
  const [tab, setTab] = useState(ST.tab)
  const [pickedIdx, setPickedIdx] = useState(ST.pickedIdx)

  const resetState = () => {
    setActiveId(ST.activeId)
    setActiveData(ST.activeData)
    setCustomerInfo(ST.customerInfo)
    setQuestion(ST.question)
    setChannel(ST.channel)
    setError(ST.error)
    setTab(ST.tab)
    setPickedIdx(ST.pickedIdx)
    setShowNew(ST.showNew)
  }

  useEffect(() => {
    if (contentPrefill) {
      if (contentPrefill.activeId) setActiveId(contentPrefill.activeId)
      if (contentPrefill.customerInfo) setCustomerInfo(contentPrefill.customerInfo)
      if (contentPrefill.question) setQuestion(contentPrefill.question)
      if (contentPrefill.channel) setChannel(contentPrefill.channel)
      if (contentPrefill.tab) setTab(contentPrefill.tab)
      onContentPrefillConsumed?.()
    }
  }, [contentPrefill])

  const reloadList = async () => {
    setLoadingList(true)
    try {
      const list = await fetchAdvisorySessions()
      setSessions(Array.isArray(list) ? list : [])
    } catch (err) {
      setError(err.message)
    } finally {
      setLoadingList(false)
    }
  }

  useEffect(() => { reloadList() }, [])

  useEffect(() => {
    if (!activeId) { setActiveData(null); setPickedIdx(null); return }
    setLoadingDetail(true)
    fetchAdvisorySession(activeId)
      .then((d) => {
        setActiveData(d)
        setPickedIdx(null)
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoadingDetail(false))
  }, [activeId])

  const handleNewSession = async (name, summary) => {
    try {
      const s = await createAdvisorySession(name, summary)
      await reloadList()
      const id = s?.id
      if (id) setActiveId(id)
      setShowNew(false)
    } catch (err) {
      setError(err.message)
    }
  }

  const handleAnalyze = async () => {
    if (!activeId || !question.trim() || analyzing) return
    setAnalyzing(true)
    setError('')
    try {
      await analyzeCustomer(
        activeId,
        customerInfo.trim() || (activeData?.summary || ''),
        question.trim(),
        channel,
      )
      const fresh = await fetchAdvisorySession(activeId)
      setActiveData(fresh)
      setPickedIdx(null)  // 跳到最新一条
      setQuestion('')
      // customerInfo 保留, 方便连续追问同一个客户
    } catch (err) {
      setError(err.message)
    } finally {
      setAnalyzing(false)
    }
  }

  const handleDelete = async (id) => {
    if (!window.confirm('确定要删除这个客户对话吗?')) return
    try {
      await deleteAdvisorySession(id)
      await reloadList()
      if (activeId === id) setActiveId(null)
    } catch (err) {
      alert(err.message)
    }
  }

  const filtered = sessions.filter((s) =>
    !search.trim() || (s.name || '').toLowerCase().includes(search.toLowerCase())
  )

  const messages = activeData?.messages || []
  const currentMsg = messages.length > 0
    ? (pickedIdx == null ? messages[messages.length - 1] : messages[pickedIdx])
    : null
  const currentTab = STRATEGY_TABS.find((t) => t.id === tab) || STRATEGY_TABS[0]

  return (
    <div className="advisory-page">
      <header className="advisory-header">
        <div>
          <h2>客户答疑助手</h2>
          <p className="page-sub">客户列表 · 沟通记录 · AI 给三版话术 + 后续行动建议</p>
        </div>
        <button className="btn-primary" onClick={() => setShowNew(true)}>+ 新建客户对话</button>
        <button className="btn-ghost reset-btn" onClick={resetState} title="清除当前对话状态">
          重新开始
        </button>
      </header>

      {error && <div className="adv-error">{error} <button onClick={() => setError('')}>×</button></div>}

      <div className="adv-grid">
        {/* 左侧 客户列表 */}
        <aside className="adv-sidebar">
          <input
            className="adv-search"
            placeholder="搜索客户…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <div className="adv-session-list">
            {loadingList && <div className="adv-empty">加载中…</div>}
            {!loadingList && filtered.length === 0 && (
              <div className="adv-empty">
                <div className="empty-icon">👥</div>
                <div>还没有客户</div>
                <p>点右上"新建"开始</p>
              </div>
            )}
            {filtered.map((s) => (
              <div key={s.id}
                className={'adv-session' + (activeId === s.id ? ' is-active' : '')}
                onClick={() => setActiveId(s.id)}>
                <div className="adv-session-name">{s.name || '未命名'}</div>
                {s.summary && <div className="adv-session-tag">{s.summary.slice(0, 40)}</div>}
                {s.lastEmotion && (
                  <div className="adv-session-emotion">{s.lastEmotion}</div>
                )}
                {s.lastQuestion && <div className="adv-session-msg">{s.lastQuestion.slice(0, 50)}</div>}
                <button className="adv-del" onClick={(e) => { e.stopPropagation(); handleDelete(s.id) }}>×</button>
              </div>
            ))}
          </div>
        </aside>

        {/* 中间 历史记录 + 输入 */}
        <section className="adv-main">
          {!activeId ? (
            <div className="adv-empty adv-main-empty">
              <div className="empty-icon">💬</div>
              <div>选一个客户开始</div>
              <p>左侧选择, 这里看历史; 右侧 AI 出三版话术</p>
            </div>
          ) : (
            <>
              <div className="adv-main-head">
                <h3>{activeData?.name || '客户对话'}</h3>
                {activeData?.summary && <span className="adv-tag">{activeData.summary}</span>}
              </div>

              <div className="adv-messages">
                {loadingDetail && <div className="adv-empty">加载中…</div>}
                {!loadingDetail && messages.length === 0 && (
                  <div className="adv-empty">
                    <div className="empty-icon">📝</div>
                    <p>这是新对话, 把客户消息粘贴下面 → "分析", 右侧会出三版话术</p>
                  </div>
                )}
                {messages.map((m, i) => {
                  const isPicked = (pickedIdx == null && i === messages.length - 1) || pickedIdx === i
                  return (
                    <div key={m.id || i}
                      className={'adv-record' + (isPicked ? ' is-picked' : '')}
                      onClick={() => setPickedIdx(i)}>
                      <div className="adv-record-head">
                        <span className="adv-record-num">#{i + 1}</span>
                        {m.channel && <span className="adv-channel">{m.channel}</span>}
                        {m.emotionState && <span className="adv-emotion">{m.emotionState}</span>}
                        {m.anxietyLevel != null && (
                          <span className="adv-anxiety" title="焦虑程度">
                            焦虑 {'●'.repeat(m.anxietyLevel)}{'○'.repeat(5 - m.anxietyLevel)}
                          </span>
                        )}
                        <span className="adv-record-time">{(m.createdAt || '').slice(0, 16)}</span>
                      </div>
                      <div className="adv-record-q">
                        <span className="role-tag">客户</span>
                        <div>{m.question}</div>
                      </div>
                      {m.surfaceQuestion && (
                        <div className="adv-surface">
                          <span className="surface-label">表面问题</span>
                          {m.surfaceQuestion}
                        </div>
                      )}
                    </div>
                  )
                })}
              </div>

              <div className="adv-input-bar">
                <div className="adv-input-row">
                  <textarea
                    className="adv-input"
                    rows={2}
                    placeholder="客户基本情况 (可选, 第一次填一下后续可不填)"
                    value={customerInfo}
                    onChange={(e) => setCustomerInfo(e.target.value)}
                    disabled={analyzing}
                  />
                </div>
                <div className="adv-input-row">
                  <textarea
                    className="adv-input"
                    rows={2}
                    placeholder="客户的问题 / 消息原话, 回车提交, Shift+回车换行"
                    value={question}
                    onChange={(e) => setQuestion(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' && !e.shiftKey) {
                        e.preventDefault()
                        handleAnalyze()
                      }
                    }}
                    disabled={analyzing}
                  />
                </div>
                <div className="adv-input-foot">
                  <select
                    className="adv-channel-select"
                    value={channel}
                    onChange={(e) => setChannel(e.target.value)}
                    disabled={analyzing}>
                    {CHANNEL_OPTIONS.map((c) => (
                      <option key={c.id} value={c.id}>{c.label}</option>
                    ))}
                  </select>
                  <button
                    className="btn-primary"
                    onClick={handleAnalyze}
                    disabled={!question.trim() || analyzing}>
                    {analyzing ? '分析中…' : '分析 (3 积分)'}
                  </button>
                </div>
              </div>
            </>
          )}
        </section>

        {/* 右侧 策略面板 */}
        <aside className="adv-strategy">
          {!currentMsg && !analyzing && (
            <div className="adv-empty">
              <div className="empty-icon">📋</div>
              <div>等待客户问题</div>
              <p>提交一条消息后, 这里出现三版话术</p>
            </div>
          )}

          {analyzing && (
            <div className="adv-empty">
              <div className="spinner" />
              <div>AI 分析中…</div>
              <p>读取意图、生成话术、规划行动</p>
            </div>
          )}

          {currentMsg && !analyzing && (
            <>
              {currentMsg.trueIntent && (
                <div className="adv-intent">
                  <div className="intent-label">客户真实意图</div>
                  <div className="intent-text">{currentMsg.trueIntent}</div>
                </div>
              )}

              <div className="strategy-tabs">
                {STRATEGY_TABS.map((t) => (
                  <button key={t.id}
                    className={'strategy-tab' + (tab === t.id ? ' is-active' : '')}
                    onClick={() => setTab(t.id)}>
                    {t.label}
                  </button>
                ))}
              </div>
              <div className="strategy-tip">{currentTab.desc}</div>

              <div className="adv-strategy-body">
                {currentMsg[currentTab.field] ? (
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>
                    {currentMsg[currentTab.field]}
                  </ReactMarkdown>
                ) : (
                  <p className="adv-empty-text">该版本话术暂无</p>
                )}
                {currentMsg[currentTab.field] && (
                  <button
                    className="btn-copy-text"
                    onClick={() => copyToClipboard(stripMarkdown(currentMsg[currentTab.field]))}>
                    复制纯文本
                  </button>
                )}
              </div>

              {Array.isArray(currentMsg.nextSteps) && currentMsg.nextSteps.length > 0 && (
                <div className="adv-followup">
                  <div className="adv-followup-head">后续行动建议</div>
                  <ol className="adv-steps">
                    {currentMsg.nextSteps.map((s, i) => (
                      <li key={i}>{s}</li>
                    ))}
                  </ol>
                </div>
              )}
            </>
          )}
        </aside>
      </div>

      {showNew && (
        <NewSessionModal
          onCreate={handleNewSession}
          onClose={() => setShowNew(false)}
        />
      )}
    </div>
  )
}

function NewSessionModal({ onCreate, onClose }) {
  const [name, setName] = useState('')
  const [summary, setSummary] = useState('')
  return (
    <div className="modal-mask" onClick={onClose}>
      <div className="modal-card" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3>新建客户对话</h3>
          <button className="modal-close" onClick={onClose}>×</button>
        </div>
        <div className="profile-field">
          <label className="profile-label">客户称呼 <span className="required">*</span></label>
          <input className="profile-input" value={name} onChange={(e) => setName(e.target.value)}
            placeholder="例如: 王女士 / 上海宝妈 / 李总" />
        </div>
        <div className="profile-field">
          <label className="profile-label">画像 / 来源 (可选)</label>
          <textarea className="profile-input" rows={3} value={summary}
            onChange={(e) => setSummary(e.target.value)}
            placeholder="例如: 30 岁宝妈, 二胎, 预算 1 万/年, 关注重疾和教育金, 来自小红书私信" />
        </div>
        <div className="modal-foot">
          <button className="btn-cancel" onClick={onClose}>取消</button>
          <button className="btn-confirm" disabled={!name.trim()}
            onClick={() => onCreate(name.trim(), summary.trim())}>
            创建
          </button>
        </div>
      </div>
    </div>
  )
}
