import { useEffect, useState, useCallback } from 'react'
import { fetchCreditsSummary, fetchCreditsRecords } from '../api'
import './CreditsPage.css'

const PACKAGES = [
  { id: 'p1', credits: 1000, price: 99,  save: 0 },
  { id: 'p2', credits: 5000, price: 459, save: 36 },
  { id: 'p3', credits: 10000,price: 859, save: 131 },
]

const FILTER_TABS = [
  { id: 'all',    label: '全部' },
  { id: 'create', label: '生成内容' },
  { id: 'qa',     label: '答疑' },
]

const PLATFORM_LABEL = {
  xhs:    { label: '小红书', cls: 'plat-xhs' },
  gzh:    { label: '公众号', cls: 'plat-gzh' },
  douyin: { label: '抖音',   cls: 'plat-dy' },
  video:  { label: '视频号', cls: 'plat-video' },
}

const KIND_ICON = { create: '✦', qa: '💬', topup: '+' }


export default function CreditsPage() {
  const [filter,      setFilter]      = useState('all')
  const [records,     setRecords]     = useState([])
  const [summary,     setSummary]     = useState(null)
  const [loading,     setLoading]     = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [page,        setPage]        = useState(0)
  const [hasMore,     setHasMore]     = useState(true)
  const [showBuy,     setShowBuy]     = useState(false)
  const [selectedPkg, setSelectedPkg] = useState(PACKAGES[1].id)
  const [payMethod,   setPayMethod]   = useState('wechat')
  const [payTip,      setPayTip]      = useState('')

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

  const pkg = PACKAGES.find(p => p.id === selectedPkg)

  const handlePay = () => {
    setPayTip('支付功能正在对接中，请联系客服充值。')
    setTimeout(() => setPayTip(''), 3000)
  }

  return (
    <div className="credits-page">
      <header className="credits-header">
        <div>
          <h2>积分管理</h2>
          <p className="credits-sub">查看 AI 使用记录，积分不够用可以加购</p>
        </div>
        <button className="btn-buy" onClick={() => setShowBuy(true)}>+ 加购积分</button>
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
          <h3>使用记录</h3>
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
              return (
                <div key={r.id} className="record-row">
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
        <div className="modal-mask" onClick={() => setShowBuy(false)}>
          <div className="modal-card" onClick={e => e.stopPropagation()}>
            <div className="modal-head">
              <h3>加购积分</h3>
              <button className="modal-close" onClick={() => setShowBuy(false)}>×</button>
            </div>

            <div className="pkg-grid">
              {PACKAGES.map(p => (
                <button
                  key={p.id}
                  className={'pkg-card' + (selectedPkg === p.id ? ' is-active' : '')}
                  onClick={() => setSelectedPkg(p.id)}>
                  <div className="pkg-credits">{p.credits.toLocaleString()} 积分</div>
                  <div className="pkg-price">¥{p.price}</div>
                  {p.save > 0 && <div className="pkg-save">省 ¥{p.save}</div>}
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

            {payTip && <div className="pay-tip">{payTip}</div>}

            <div className="modal-foot">
              <div className="modal-total">合计 ¥{pkg?.price || 0}</div>
              <button className="btn-confirm" onClick={handlePay}>确认支付</button>
            </div>
          </div>
        </div>
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
