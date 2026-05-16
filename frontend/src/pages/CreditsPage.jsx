import { useEffect, useState, useCallback, useRef } from 'react'
import { QRCodeSVG } from 'qrcode.react'
import { fetchCreditsSummary, fetchCreditsRecords, fetchCreditsRecordContent } from '../api'
import { getToken } from '../auth'
import './CreditsPage.css'


const FILTER_TABS = [
  { id: 'all',    label: '全部' },
  { id: 'create', label: '生成内容' },
  { id: 'qa',     label: '答疑' },
  { id: 'topup',  label: '充值' },
]

const PLATFORM_LABEL = {
  xhs:    { label: '小红书', cls: 'plat-xhs' },
  gzh:    { label: '公众号', cls: 'plat-gzh' },
  douyin: { label: '抖音',   cls: 'plat-dy' },
  video:  { label: '视频号', cls: 'plat-video' },
}

const KIND_ICON = { create: '✦', qa: '💬', topup: '+' }

const CONTENT_TYPE_LABEL = {
  xhs_post: '小红书正文', xhs_title: '小红书标题', gzh_article: '公众号文章',
  gzh_title: '公众号标题', video_script: '视频脚本', drama_script: '短剧脚本',
  image: 'AI 配图', viral_xhs: '小红书爆款拆解', viral_douyin: '抖音爆款拆解',
  advisory: '客户答疑',
}

const CONTENT_PAGE_MAP = {
  xhs_post:       { page: 'xhs-create',     build: c => ({ content: c.content, topic: c.topic }) },
  xhs_title:      { page: 'xhs-create',     build: c => ({ titles: safeSplit(c.content), topic: c.topic }) },
  gzh_article:    { page: 'gzh-create',     build: c => ({ content: c.content, topic: c.topic }) },
  gzh_title:      { page: 'gzh-create',     build: c => ({ titles: safeSplit(c.content), topic: c.topic }) },
  video_script:   { page: 'video-rip',      build: c => ({ content: c.content, topic: c.topic }) },
  drama_script:   { page: 'video-create',   build: c => ({ content: c.content, topic: c.topic }) },
  viral_xhs:      { page: 'viral-xhs',      build: c => ({ result: c.content, url: c.topic }) },
  viral_douyin:   { page: 'viral-douyin',   build: c => ({ result: c.content, url: c.topic }) },
  advisory:       { page: 'advisory',       build: c => ({ question: c.topic, content: c.content }) },
  image:          { page: 'xhs-create',     build: c => ({ images: [{ url: c.image_url }], topic: c.topic }) },
}

function safeSplit(str) {
  if (!str) return []
  return str.split('\n').filter(Boolean)
}


export default function CreditsPage({ onNavigate, onNavigateWithContentPrefill, isActive }) {
  const [filter,      setFilter]      = useState('all')
  const [records,     setRecords]     = useState([])
  const [summary,     setSummary]     = useState(null)
  const [loading,     setLoading]     = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [page,        setPage]        = useState(0)
  const [hasMore,     setHasMore]     = useState(true)
  const [showBuy,     setShowBuy]     = useState(false)
  const [packages,    setPackages]    = useState([])
  const [selectedPkg, setSelectedPkg] = useState('')
  const [payMethod,   setPayMethod]   = useState('wechat')
  const [viewingRecord, setViewingRecord] = useState(null)
  const [viewingContent, setViewingContent] = useState(null)
  const [viewingLoading, setViewingLoading] = useState(false)

  // 支付流程状态: select → qr → success
  const [payStep,    setPayStep]    = useState('select')
  const [codeUrl,    setCodeUrl]    = useState('')
  const [outTradeNo, setOutTradeNo] = useState('')
  const [payLoading, setPayLoading] = useState(false)
  const [payErr,     setPayErr]     = useState('')
  const pollRef = useRef(null)

  useEffect(() => {
    fetch('/api/pay/products')
      .then(r => r.json())
      .then(data => {
        const pkgs = data.products || []
        setPackages(pkgs)
        if (pkgs.length > 0) setSelectedPkg(pkgs[Math.min(1, pkgs.length - 1)].id)
      })
      .catch(() => {})
  }, [])

  const stopPolling = () => {
    if (pollRef.current) {
      clearInterval(pollRef.current)
      pollRef.current = null
    }
  }

  const closeBuy = () => {
    stopPolling()
    setShowBuy(false)
    setPayStep('select')
    setCodeUrl('')
    setOutTradeNo('')
    setPayErr('')
    setPayLoading(false)
  }

  const startPolling = useCallback((tradeNo) => {
    stopPolling()
    pollRef.current = setInterval(async () => {
      try {
        const res = await fetch(`/api/pay/status/${tradeNo}`, {
          headers: { Authorization: `Bearer ${getToken()}` },
        })
        if (!res.ok) return
        const data = await res.json()
        if (data.status === 'PAID') {
          stopPolling()
          setPayStep('success')
          fetchCreditsSummary().then(setSummary).catch(() => {})
          loadRecords(filter, 0)
          window.dispatchEvent(new CustomEvent('credits:updated'))
        }
      } catch {
        // 网络抖动，继续轮询
      }
    }, 2000)
  }, [])

  // 弹窗关闭时清理
  useEffect(() => {
    return () => stopPolling()
  }, [])

  const handlePay = async () => {
    if (payMethod !== 'wechat') {
      setPayErr('目前仅支持微信支付，支付宝敬请期待。')
      return
    }
    setPayErr('')
    setPayLoading(true)
    try {
      const res = await fetch('/api/pay/order', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${getToken()}`,
        },
        body: JSON.stringify({ product: selectedPkg }),
      })
      const data = await res.json()
      if (!res.ok) {
        setPayErr(data.error || '创建订单失败，请稍后重试。')
        return
      }
      setCodeUrl(data.codeUrl)
      setOutTradeNo(data.outTradeNo)
      setPayStep('qr')
      startPolling(data.outTradeNo)
    } catch {
      setPayErr('网络异常，请检查连接后重试。')
    } finally {
      setPayLoading(false)
    }
  }

  const loadRecords = useCallback(async (f, p, append = false) => {
    try {
      const data = await fetchCreditsRecords(f, p, 20)
      setRecords(prev => append ? [...prev, ...data] : data)
      setHasMore(data.length === 20)
    } catch (e) {
      console.error('[Credits] 记录加载失败', e)
    }
  }, [])

  useEffect(() => {
    setLoading(true)
    setPage(0)
    Promise.all([
      fetchCreditsSummary().then(setSummary).catch(console.error),
      loadRecords(filter, 0),
    ]).finally(() => setLoading(false))
  }, [filter, loadRecords])

  const prevActive = useRef(isActive)
  useEffect(() => {
    if (isActive && !prevActive.current) {
      setLoading(true)
      setPage(0)
      Promise.all([
        fetchCreditsSummary().then(setSummary).catch(console.error),
        loadRecords(filter, 0),
      ]).finally(() => setLoading(false))
    }
    prevActive.current = isActive
  }, [isActive, filter, loadRecords])

  const handleLoadMore = async () => {
    const next = page + 1
    setLoadingMore(true)
    await loadRecords(filter, next, true)
    setPage(next)
    setLoadingMore(false)
  }

  const handleFilterChange = (f) => {
    if (f === filter) return
    setFilter(f)
  }

  const balance      = summary?.balance      ?? null
  const totalCredits = summary?.total        || 8000
  const totalConsumed = summary?.totalConsumed || 0
  const actionCounts = summary?.actionCounts || {}
  const totalCreate  = Object.entries(actionCounts)
    .filter(([k]) => !['advisory','topic_refresh','topup'].includes(k))
    .reduce((a, [,v]) => a + Number(v), 0)
  const totalQa      = actionCounts['advisory'] || 0

  const pkg = packages.find(p => p.id === selectedPkg)

  const handleViewContent = async (record) => {
    if (record.kind === 'topup') return
    setViewingRecord(record)
    setViewingContent(null)
    setViewingLoading(true)
    try {
      const data = await fetchCreditsRecordContent(record.id)
      setViewingContent(data)
    } catch (e) {
      console.error('[Credits] 加载内容失败', e)
      setViewingContent({ error: '加载内容失败' })
    } finally {
      setViewingLoading(false)
    }
  }

  const handleOpenInEditor = () => {
    if (!viewingContent || !viewingContent.content_type) return
    const mapping = CONTENT_PAGE_MAP[viewingContent.content_type]
    if (!mapping) return
    const prefill = mapping.build(viewingContent)
    setViewingRecord(null)
    setViewingContent(null)
    if (onNavigateWithContentPrefill) {
      onNavigateWithContentPrefill(mapping.page, prefill)
    } else if (onNavigate) {
      onNavigate(mapping.page)
    }
  }

  return (
    <div className="credits-page">
      <header className="credits-header">
        <div>
          <h2>积分管理</h2>
          <p className="credits-sub">查看 AI 使用记录，积分不够用可以加购</p>
        </div>
        <button className="btn-buy" onClick={() => { setPayStep('select'); setShowBuy(true) }}>+ 加购积分</button>
      </header>

      {/* 汇总卡片 */}
      <section className="balance-card">
        <div className="balance-main">
          <div className="balance-label">剩余积分</div>
          <div className="balance-num">{balance !== null ? balance.toLocaleString() : '—'}</div>
          <div className="balance-tip">/ {totalCredits.toLocaleString()} 积分</div>
          <div className="balance-bar">
            <div className="balance-bar-fill" style={{ width: balance !== null ? `${(balance / totalCredits * 100).toFixed(1)}%` : '0%' }} />
          </div>
        </div>
        <div className="balance-stats">
          <div className="stat-item">
            <div className="stat-num">{totalConsumed.toLocaleString()}</div>
            <div className="stat-label">累计消耗</div>
          </div>
          <div className="stat-divider" />
          <div className="stat-item">
            <div className="stat-num">{totalCreate}</div>
            <div className="stat-label">内容生成次数</div>
          </div>
          <div className="stat-divider" />
          <div className="stat-item">
            <div className="stat-num">{totalQa}</div>
            <div className="stat-label">客户答疑次数</div>
          </div>
        </div>
      </section>

      {/* 记录列表 */}
      <section className="records-section">
        <div className="records-head">
          <h3>积分流水</h3>
          <div className="records-tabs">
            {FILTER_TABS.map(t => (
              <button
                key={t.id}
                className={'records-tab' + (filter === t.id ? ' is-active' : '')}
                onClick={() => handleFilterChange(t.id)}>
                {t.label}
              </button>
            ))}
          </div>
        </div>

        {loading ? (
          <div className="records-empty">加载中…</div>
        ) : records.length === 0 ? (
          <div className="records-empty">暂无记录，开始使用 AI 功能后会在这里展示</div>
        ) : (
          <div className="records-list">
            {records.map(r => {
              const plat = r.platform ? PLATFORM_LABEL[r.platform] : null
              const canClick = r.kind !== 'topup'
              return (
                <div
                  key={r.id}
                  className={'record-row' + (canClick ? ' is-clickable' : '')}
                  onClick={() => canClick && handleViewContent(r)}
                  role={canClick ? 'button' : undefined}
                  tabIndex={canClick ? 0 : undefined}
                  onKeyDown={e => canClick && e.key === 'Enter' && handleViewContent(r)}>
                  <div className={'record-icon kind-' + r.kind}>
                    {KIND_ICON[r.kind] || '-'}
                  </div>
                  <div className="record-main">
                    <div className="record-title">
                      <span>{r.title}</span>
                      {plat && <span className={'plat-tag ' + plat.cls}>{plat.label}</span>}
                    </div>
                    <div className="record-detail">{r.detail}</div>
                  </div>
                  <div className="record-side">
                    <div className={'record-cost' + (r.cost > 0 ? ' is-pos' : '')}>
                      {r.cost > 0 ? '+' : ''}{r.cost}
                    </div>
                    <div className="record-time">{formatTime(r.time)}</div>
                  </div>
                </div>
              )
            })}
          </div>
        )}

        {!loading && records.length > 0 && (
          <div className="records-foot">
            {hasMore ? (
              <button className="btn-text" onClick={handleLoadMore} disabled={loadingMore}>
                {loadingMore ? '加载中…' : '加载更多'}
              </button>
            ) : (
              <span className="records-end">已加载全部记录</span>
            )}
            <a className="btn-text" href="mailto:support@example.com">联系客服</a>
          </div>
        )}
      </section>

      {/* 加购弹窗 */}
      {showBuy && (
        <div className="modal-mask" onClick={closeBuy}>
          <div className="modal-card" onClick={e => e.stopPropagation()}>
            <div className="modal-head">
              <h3>{payStep === 'success' ? '充值成功' : payStep === 'qr' ? '扫码支付' : '加购积分'}</h3>
              <button className="modal-close" onClick={closeBuy}>×</button>
            </div>

            {/* ── 选套餐 ── */}
            {payStep === 'select' && (
              <>
                <div className="pkg-grid">
                  {packages.map(p => (
                    <button
                      key={p.id}
                      className={'pkg-card' + (selectedPkg === p.id ? ' is-active' : '')}
                      onClick={() => setSelectedPkg(p.id)}>
                      <div className="pkg-credits">{p.credits.toLocaleString()} 积分</div>
                      <div className="pkg-price">¥{p.priceYuan}</div>
                      {p.saveFen > 0 && <div className="pkg-save">省 ¥{p.saveYuan}</div>}
                    </button>
                  ))}
                </div>

                <div className="pay-row">
                  <div className="pay-label">支付方式</div>
                  <div className="pay-methods">
                    {[{ id: 'wechat', label: '微信支付' }, { id: 'alipay', label: '支付宝' }].map(m => (
                      <label key={m.id} className={'pay-opt' + (payMethod === m.id ? ' is-active' : '')}>
                        <input type="radio" name="pay" checked={payMethod === m.id} onChange={() => setPayMethod(m.id)} />
                        {m.label}
                      </label>
                    ))}
                  </div>
                </div>

                {payErr && <div className="pay-error">{payErr}</div>}

                <div className="modal-foot">
                  <div className="modal-total">合计 ¥{pkg?.priceYuan ?? '--'}</div>
                  <button className="btn-confirm" onClick={handlePay} disabled={payLoading}>
                    {payLoading ? '创建订单中…' : '确认支付'}
                  </button>
                </div>
              </>
            )}

            {/* ── 二维码 ── */}
            {payStep === 'qr' && (
              <>
                <div className="qr-wrap">
                  <p className="qr-tip">请使用微信扫一扫</p>
                  {codeUrl ? (
                    <QRCodeSVG value={codeUrl} size={200} />
                  ) : (
                    <div className="rv-loading">生成二维码中…</div>
                  )}
                  <p className="qr-sub">支付后页面将自动更新，请勿关闭</p>
                  <p className="qr-amount">实付 ¥0.01（测试）· {pkg?.credits?.toLocaleString()} 积分</p>
                </div>
                {payErr && <div className="pay-error">{payErr}</div>}
                <div className="modal-foot" style={{ justifyContent: 'center' }}>
                  <button className="btn-text" onClick={() => { stopPolling(); setPayStep('select') }}>
                    返回重选
                  </button>
                </div>
              </>
            )}

            {/* ── 支付成功 ── */}
            {payStep === 'success' && (
              <div className="pay-success">
                <div className="success-icon">✓</div>
                <div className="success-text">支付成功！</div>
                <div className="success-sub">
                  {pkg?.credits?.toLocaleString()} 积分已到账，感谢支持
                </div>
                <button className="btn-confirm" onClick={closeBuy}>完成</button>
              </div>
            )}
          </div>
        </div>
      )}

      {/* 结果查看弹窗 */}
      {viewingRecord && (
        <ResultViewModal
          record={viewingRecord}
          content={viewingContent}
          loading={viewingLoading}
          onClose={() => { setViewingRecord(null); setViewingContent(null) }}
          onOpenInEditor={handleOpenInEditor}
        />
      )}
    </div>
  )
}

function formatTime(str) {
  if (!str) return ''
  const d = new Date(str)
  if (isNaN(d)) return str
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function ResultViewModal({ record, content, loading, onClose, onOpenInEditor }) {
  const typeLabel = (content && content.content_type) ? (CONTENT_TYPE_LABEL[content.content_type] || content.content_type) : ''
  const hasEditorMapping = content && content.content_type && CONTENT_PAGE_MAP[content.content_type]

  const renderContent = () => {
    if (loading) return <div className="rv-loading">加载中…</div>
    if (!content) return <div className="rv-loading">无法加载内容</div>
    if (content.error) return <div className="rv-error">{content.error}</div>

    const ct = content.content_type
    const text = content.content || ''

    if (ct === 'advisory') {
      return (
        <div className="rv-advisory">
          <div className="rv-ad-q"><span className="rv-label">问题</span>{record.title || content.topic}</div>
          <div className="rv-ad-a"><span className="rv-label">分析</span><pre className="rv-pre">{text}</pre></div>
        </div>
      )
    }

    if (ct === 'viral_xhs' || ct === 'viral_douyin') {
      return (
        <div className="rv-viral">
          <div className="rv-v-url"><span className="rv-label">来源</span>{content.topic || record.title}</div>
          <div className="rv-v-result"><pre className="rv-pre">{text}</pre></div>
        </div>
      )
    }

    if (ct === 'image') {
      return (
        <div className="rv-image">
          {content.image_url ? (
            <img src={content.image_url} alt={content.topic || '配图'} className="rv-img" />
          ) : (
            <div className="rv-noimg">暂无图片预览</div>
          )}
          {content.content && <pre className="rv-pre">{content.content}</pre>}
        </div>
      )
    }

    return (
      <div className="rv-text">
        {content.topic && <h4 className="rv-topic">{content.topic}</h4>}
        <pre className="rv-pre">{text}</pre>
      </div>
    )
  }

  return (
    <div className="modal-mask" onClick={onClose}>
      <div className="modal-card rv-card" onClick={e => e.stopPropagation()}>
        <div className="modal-head rv-head">
          <div>
            <h3>生成结果</h3>
            {typeLabel && <span className="rv-type-tag">{typeLabel}</span>}
          </div>
          <button className="modal-close" onClick={onClose}>×</button>
        </div>

        <div className="rv-body">
          {renderContent()}
        </div>

        <div className="rv-foot">
          {hasEditorMapping && (
            <button className="btn-ghost rv-edit-btn" onClick={onOpenInEditor}>
              ✎ 在编辑器中打开
            </button>
          )}
          <button className="btn-ghost" onClick={onClose}>关闭</button>
        </div>
      </div>
    </div>
  )
}
