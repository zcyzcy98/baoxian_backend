import { useEffect, useMemo, useRef, useState } from 'react'
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
  { id: 'stable', label: '稳妥版', field: 'responseStable', tip: '先建立信任', recommend: true },
  { id: 'deep',   label: '深度版', field: 'responseDeep',   tip: '展示专业' },
  { id: 'close',  label: '促单版', field: 'responseClose',  tip: '引导成交' },
]

const CHANNEL_OPTIONS = [
  { id: 'weixin',  label: '微信' },
  { id: 'xhs',     label: '小红书' },
  { id: 'douyin',  label: '抖音' },
  { id: 'phone',   label: '电话' },
  { id: 'offline', label: '线下' },
]

const AVATAR_PALETTES = ['c1', 'c2', 'c3', 'c4']

function getAvatarChar(name) {
  if (!name) return '客'
  return name.trim().charAt(0)
}
function getAvatarPalette(id) {
  if (!id) return AVATAR_PALETTES[0]
  let h = 0
  for (let i = 0; i < String(id).length; i++) h = (h * 31 + String(id).charCodeAt(i)) >>> 0
  return AVATAR_PALETTES[h % AVATAR_PALETTES.length]
}
function formatRelTime(iso) {
  if (!iso) return ''
  const t = new Date(iso).getTime()
  if (Number.isNaN(t)) return ''
  const diff = Date.now() - t
  if (diff < 60_000) return '刚刚'
  if (diff < 3600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3600_000)} 小时前`
  if (diff < 7 * 86_400_000) return `${Math.floor(diff / 86_400_000)} 天前`
  return iso.slice(0, 10)
}
function formatTime(iso) {
  if (!iso) return ''
  return iso.slice(11, 16)
}
function formatDay(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const today = new Date()
  const yesterday = new Date(today.getTime() - 86_400_000)
  const ymd = (x) => `${x.getFullYear()}-${x.getMonth()}-${x.getDate()}`
  if (ymd(d) === ymd(today)) return '今天'
  if (ymd(d) === ymd(yesterday)) return '昨天'
  return `${d.getMonth() + 1} 月 ${d.getDate()} 日`
}
function channelLabel(id) {
  return CHANNEL_OPTIONS.find((c) => c.id === id)?.label || id || ''
}

export default function AdvisoryPage({
  contentPrefill, onContentPrefillConsumed,
}) {
  const [sessions, setSessions] = useState([])
  const [activeId, setActiveId] = useState(null)
  const [activeData, setActiveData] = useState(null)
  const [loadingList, setLoadingList] = useState(false)
  const [loadingDetail, setLoadingDetail] = useState(false)
  const [showNew, setShowNew] = useState(false)
  const [search, setSearch] = useState('')
  const [question, setQuestion] = useState('')
  const [channel, setChannel] = useState('weixin')
  const [analyzingSid, setAnalyzingSid] = useState(null) // 当前正在分析的 session id, null = 没在分析
  const analyzing = analyzingSid === activeId && analyzingSid != null
  const [error, setError] = useState('')
  const [tab, setTab] = useState('stable')
  const [drawerMsgId, setDrawerMsgId] = useState(null) // 当前抽屉对应的 message id
  const bodyRef = useRef(null)

  useEffect(() => {
    if (contentPrefill) {
      if (contentPrefill.activeId) setActiveId(contentPrefill.activeId)
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
    if (!activeId) { setActiveData(null); setDrawerMsgId(null); return }
    setLoadingDetail(true)
    fetchAdvisorySession(activeId)
      .then((d) => {
        setActiveData(d)
        const msgs = d?.messages || []
        // 默认打开最新一条的抽屉
        setDrawerMsgId(msgs.length > 0 ? msgs[msgs.length - 1].id || msgs.length - 1 : null)
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoadingDetail(false))
  }, [activeId])

  // 自动滚到底
  useEffect(() => {
    if (bodyRef.current) bodyRef.current.scrollTop = bodyRef.current.scrollHeight
  }, [activeData?.messages?.length])

  const handleNewSession = async (name, summary) => {
    try {
      const s = await createAdvisorySession(name, summary)
      await reloadList()
      if (s?.id) setActiveId(s.id)
      setShowNew(false)
    } catch (err) {
      setError(err.message)
    }
  }

  const handleAnalyze = async () => {
    if (!activeId || !question.trim() || analyzing) return
    const sid = activeId
    const q = question.trim()
    const summary = activeData?.summary || ''
    const ch = channel
    setAnalyzingSid(sid)
    setError('')
    setQuestion('')
    try {
      await analyzeCustomer(sid, summary, q, ch)
      const fresh = await fetchAdvisorySession(sid)
      // 只有用户仍在这个 session 才更新展示数据
      if (sid === activeId) {
        setActiveData(fresh)
        const msgs = fresh?.messages || []
        if (msgs.length > 0) setDrawerMsgId(msgs[msgs.length - 1].id || msgs.length - 1)
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setAnalyzingSid((prev) => (prev === sid ? null : prev))
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

  // 当前抽屉里看的那条
  const currentMsg = useMemo(() => {
    if (drawerMsgId == null) return null
    return messages.find((m, i) => (m.id || i) === drawerMsgId) || null
  }, [drawerMsgId, messages])

  const currentTab = STRATEGY_TABS.find((t) => t.id === tab) || STRATEGY_TABS[0]
  const drawerOpen = currentMsg != null || analyzing

  // 按天分组渲染
  const grouped = useMemo(() => {
    const out = []
    let lastDay = null
    messages.forEach((m, i) => {
      const day = formatDay(m.createdAt)
      if (day && day !== lastDay) {
        out.push({ kind: 'divider', day, key: `div-${i}` })
        lastDay = day
      }
      out.push({ kind: 'msg', msg: m, idx: i, key: m.id || `msg-${i}` })
    })
    return out
  }, [messages])

  return (
    <div className="adv-page">
      {error && (
        <div className="adv-error">
          {error}
          <button onClick={() => setError('')}>×</button>
        </div>
      )}

      <div className={'qa-layout' + (drawerOpen ? ' drawer-open' : '')}>

        {/* ===== 左：客户列表 ===== */}
        <aside className="client-list">
          <div className="cl-head">
            <div className="cl-title">
              <h4>客户对话</h4>
              <span className="cl-count">{sessions.length}</span>
            </div>
            <button className="btn-newclient" onClick={() => setShowNew(true)}>
              <span className="plus">+</span>
              <span>新建客户对话</span>
            </button>
            <div className="cl-search">
              <span className="si">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
                  <circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" />
                </svg>
              </span>
              <input
                type="text"
                placeholder="搜索客户"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>
          </div>

          <div className="clients scroll-y">
            {loadingList && <div className="adv-empty small">加载中…</div>}
            {!loadingList && filtered.length === 0 && (
              <div className="adv-empty small">
                <div className="empty-icon">👥</div>
                <div>还没有客户</div>
                <p>点上方"新建"开始</p>
              </div>
            )}
            {filtered.map((s) => (
              <div
                key={s.id}
                className={'client-item' + (activeId === s.id ? ' active' : '')}
                onClick={() => setActiveId(s.id)}
              >
                <div className={'avatar ' + getAvatarPalette(s.id)}>{getAvatarChar(s.name)}</div>
                <div className="info">
                  <div className="name">{s.name || '未命名'}</div>
                  {s.summary && <div className="desc">{s.summary}</div>}
                  {s.lastEmotion && <div className="emo">{s.lastEmotion}</div>}
                  <div className="time">{formatRelTime(s.updatedAt || s.createdAt)}</div>
                </div>
                <button
                  className="ci-del"
                  onClick={(e) => { e.stopPropagation(); handleDelete(s.id) }}
                  title="删除"
                >×</button>
              </div>
            ))}
          </div>
        </aside>

        {/* ===== 中：对话区 ===== */}
        <main className="conversation">
          {!activeId ? (
            <div className="adv-empty conv-empty">
              <div className="empty-icon">💬</div>
              <div>选一个客户开始</div>
              <p>左侧选择, 右侧 AI 出三版话术 + 后续行动</p>
            </div>
          ) : (
            <>
              <div className="conv-head">
                <div className={'avatar ' + getAvatarPalette(activeData?.id)}>
                  {getAvatarChar(activeData?.name)}
                </div>
                <div className="info">
                  <h3>{activeData?.name || '客户对话'}</h3>
                  {activeData?.summary && <div className="desc">{activeData.summary}</div>}
                </div>
              </div>

              <div className="conv-body scroll-y" ref={bodyRef}>
                <div className="conv-inner">
                  {loadingDetail && <div className="adv-empty">加载中…</div>}
                  {!loadingDetail && messages.length === 0 && (
                    <div className="adv-empty conv-empty">
                      <div className="empty-icon">📝</div>
                      <p>新对话, 把客户消息粘贴到下方 → 生成应对, AI 会给三版话术</p>
                    </div>
                  )}

                  {grouped.map((g) => {
                    if (g.kind === 'divider') {
                      return <div key={g.key} className="msg-divider">— {g.day} —</div>
                    }
                    const m = g.msg
                    const msgKey = m.id || g.idx
                    const isPicked = drawerMsgId === msgKey
                    return (
                      <div key={g.key} className="msg-group">
                        {/* 客户气泡 */}
                        <div className="msg-user">
                          <div className="msg-user-bubble">
                            {m.channel && (
                              <div className="channel-badge">{channelLabel(m.channel)}</div>
                            )}
                            <div className="field">
                              <div className="field-label">— 客户的问题</div>
                              <div className="field-text">{m.question}</div>
                            </div>
                            {m.surfaceQuestion && (
                              <div className="field">
                                <div className="field-label">— 表面问题</div>
                                <div className="field-text">{m.surfaceQuestion}</div>
                              </div>
                            )}
                          </div>
                          <div className="msg-user-time">{formatTime(m.createdAt)}</div>
                        </div>

                        {/* AI 气泡 */}
                        <div className="msg-ai">
                          <div className="msg-ai-avatar">
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                              <path d="M12 2 L13.5 8.5 L20 10 L13.5 11.5 L12 18 L10.5 11.5 L4 10 L10.5 8.5 Z" />
                            </svg>
                          </div>
                          <div className="msg-ai-content">
                            <div className="msg-ai-bubble">
                              <div className="ai-tag">
                                <span className="leaf-icon">✓</span>
                                <span>分析完成 · 已生成应对方案</span>
                              </div>
                              {m.trueIntent && (
                                <div className="ai-summary">
                                  {m.trueIntent}
                                </div>
                              )}
                              <div className="msg-ai-actions">
                                <button
                                  className={'btn-view-analysis' + (isPicked ? ' active' : '')}
                                  onClick={() => setDrawerMsgId(isPicked ? null : msgKey)}
                                >
                                  <span>{isPicked ? '收起完整分析' : '查看完整分析'}</span>
                                  <span className="arr">{isPicked ? '×' : '→'}</span>
                                </button>
                                <div className="meta">
                                  {m.emotionState && (
                                    <span className="meta-tag">{m.emotionState}</span>
                                  )}
                                  <span className="credits-used">-3 积分</span>
                                </div>
                              </div>
                            </div>
                            <div className="msg-ai-time">{formatTime(m.createdAt)}</div>
                          </div>
                        </div>
                      </div>
                    )
                  })}

                  {analyzing && (
                    <div className="msg-ai">
                      <div className="msg-ai-avatar">
                        <div className="spinner-mini" />
                      </div>
                      <div className="msg-ai-content">
                        <div className="msg-ai-bubble loading">
                          <span>AI 正在读取意图、生成话术、规划行动…</span>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              </div>

              {/* 输入区 */}
              <div className="conv-input">
                <div className="input-inner">
                  <div className="input-fields single">
                    <div className="input-field">
                      <label>— 客户的问题 <span className="req">*</span></label>
                      <textarea
                        placeholder="直接粘贴客户的原话最好, 回车提交 (Shift+回车换行)"
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
                  </div>
                  <div className="input-toolbar">
                    <div className="tool-group">
                      <span className="tg-label">渠道：</span>
                      {CHANNEL_OPTIONS.map((c) => (
                        <span
                          key={c.id}
                          className={'channel-chip' + (channel === c.id ? ' active' : '')}
                          onClick={() => !analyzing && setChannel(c.id)}
                        >
                          {c.label}
                        </span>
                      ))}
                    </div>
                    <div className="input-actions">
                      <span className="credits-tip">本次约消耗 <b>3</b> 积分</span>
                      <button
                        className="btn-generate"
                        onClick={handleAnalyze}
                        disabled={!question.trim() || analyzing}
                      >
                        <span>{analyzing ? '分析中…' : '生成应对'}</span>
                        {!analyzing && <span className="ic">→</span>}
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </>
          )}
        </main>

        {/* ===== 右：分析抽屉 ===== */}
        <aside className="analysis-drawer">
          <div className="drawer-body scroll-y">

            {analyzing && (
              <div className="adv-empty">
                <div className="spinner" />
                <div>AI 分析中…</div>
                <p>读取意图、生成话术、规划行动</p>
              </div>
            )}

            {!analyzing && currentMsg && (
              <>
                {/* 卡片 ① 意图 + 情绪 */}
                <div className="acard intent">
                  <div className="acard-head">
                    <div className="acard-icon">
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
                        <circle cx="12" cy="12" r="10" /><circle cx="12" cy="12" r="6" /><circle cx="12" cy="12" r="2" fill="currentColor" />
                      </svg>
                    </div>
                    <h5>客户意图分析</h5>
                    <span className="acard-meta">— Intent</span>
                  </div>
                  <div className="acard-body">
                    {currentMsg.surfaceQuestion && (
                      <div className="intent-block surface">
                        <span className="intent-tag">表面问题</span>
                        <span className="intent-text">{currentMsg.surfaceQuestion}</span>
                      </div>
                    )}
                    {currentMsg.trueIntent && (
                      <div className="intent-block real">
                        <span className="intent-tag">真实意图</span>
                        <span className="intent-text">{currentMsg.trueIntent}</span>
                      </div>
                    )}

                  </div>
                </div>

                {/* 卡片 ② 应对方案 */}
                <div className="acard response">
                  <div className="acard-head">
                    <div className="acard-icon">
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
                        <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                      </svg>
                    </div>
                    <h5>应对方案</h5>
                    <span className="acard-meta">— Response</span>
                  </div>

                  <div className="response-tabs">
                    {STRATEGY_TABS.map((t) => (
                      <div
                        key={t.id}
                        className={'response-tab'
                          + (tab === t.id ? ' active' : '')
                          + (t.recommend ? ' recommend' : '')}
                        onClick={() => setTab(t.id)}
                      >
                        <span className="tab-name">{t.label}</span>
                        <span className="tab-tip">{t.tip}</span>
                      </div>
                    ))}
                  </div>

                  <div className="response-content">
                    <div className="response-script">
                      {currentMsg[currentTab.field] ? (
                        <ReactMarkdown remarkPlugins={[remarkGfm]}>
                          {currentMsg[currentTab.field]}
                        </ReactMarkdown>
                      ) : (
                        <p className="adv-empty-text">该版本话术暂无</p>
                      )}
                    </div>

                    {currentMsg[currentTab.field] && (
                      <div className="response-actions">
                        <button
                          className="response-action-btn primary"
                          onClick={() => copyToClipboard(stripMarkdown(currentMsg[currentTab.field]))}
                        >
                          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
                            <rect x="9" y="9" width="13" height="13" rx="2" />
                            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                          </svg>
                          <span>复制话术</span>
                        </button>
                      </div>
                    )}
                  </div>
                </div>

                {/* 卡片 ③ 后续动作 */}
                {Array.isArray(currentMsg.nextSteps) && currentMsg.nextSteps.length > 0 && (
                  <div className="acard next">
                    <div className="acard-head">
                      <div className="acard-icon">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                          <polyline points="3 17 9 11 13 15 21 7" />
                          <polyline points="14 7 21 7 21 14" />
                        </svg>
                      </div>
                      <h5>接下来该怎么走</h5>
                      <span className="acard-meta">— Next steps</span>
                    </div>
                    <div className="acard-body">
                      {currentMsg.nextSteps.map((s, i) => (
                        <div key={i} className={'next-step ' + (i === 0 ? 'now' : 'branch')}>
                          <div className="step-marker">{i === 0 ? '现' : String.fromCharCode(64 + i)}</div>
                          <div className="step-content">
                            <div className="step-action">{s}</div>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </>
            )}

            {!analyzing && !currentMsg && (
              <div className="adv-empty">
                <div className="empty-icon">📋</div>
                <div>等待客户问题</div>
                <p>提交一条消息后, 这里出现三版话术</p>
              </div>
            )}
          </div>
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
    <div className="modal-bg active" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <button className="modal-close" onClick={onClose}>×</button>
        <h3>新建客户对话</h3>
        <p className="modal-sub">
          先建一个客户档案, AI 会在后续对话中持续记住这位客户的上下文。详细资料可以在每次提问时补充。
        </p>

        <div className="modal-field">
          <label>— 客户称呼 <span className="req">*</span></label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="如：王女士、李工程师、张总"
            autoComplete="off"
          />
          <div className="hint">用于在对话中识别, 可以用化名</div>
        </div>

        <div className="modal-field">
          <label>— 基本画像 <span className="req">*</span></label>
          <textarea
            value={summary}
            onChange={(e) => setSummary(e.target.value)}
            placeholder="如：32 岁宝妈、二胎、互联网行业、年收入 80 万、已购百万医疗险…"
          />
          <div className="hint">一两句话描述这位客户最关键的特征, 越具体 AI 给出的应对方案越精准</div>
        </div>

        <div className="modal-actions">
          <button className="btn-cancel" onClick={onClose}>取消</button>
          <button
            className="btn-create"
            disabled={!name.trim() || !summary.trim()}
            onClick={() => onCreate(name.trim(), summary.trim())}
          >
            创建对话
          </button>
        </div>
      </div>
    </div>
  )
}
