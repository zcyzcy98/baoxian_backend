import { useState } from 'react'
import './CreditsPage.css'

const PACKAGES = [
  { id: 'p1', credits: 1000, price: 99, save: 0 },
  { id: 'p2', credits: 5000, price: 459, save: 36 },
  { id: 'p3', credits: 10000, price: 859, save: 131 },
]

const FILTER_TABS = [
  { id: 'all', label: '全部' },
  { id: 'create', label: '生成内容' },
  { id: 'qa', label: '答疑' },
  { id: 'topup', label: '充值' },
]

const PLATFORM_LABEL = {
  xhs: { label: '小红书', cls: 'plat-xhs' },
  gzh: { label: '公众号', cls: 'plat-gzh' },
  douyin: { label: '抖音', cls: 'plat-dy' },
  video: { label: '视频号', cls: 'plat-video' },
}

// Mock data — 后端目前没有 /api/credits/* 接口, 暂时用 mock + localStorage.
const MOCK_RECORDS = [
  { id: 1, kind: 'create', platform: 'xhs', title: '生成小红书笔记', detail: '给爸妈买重疾险的 3 个坑', cost: -5, time: '2026-05-04 14:22' },
  { id: 2, kind: 'create', platform: 'gzh', title: '公众号长文', detail: '保险里的"小坑大病"系列', cost: -25, time: '2026-05-04 13:08' },
  { id: 3, kind: 'qa', platform: null, title: '客户答疑', detail: '宝妈购险预算 5000 应该怎么配', cost: -3, time: '2026-05-04 11:50' },
  { id: 4, kind: 'topup', platform: null, title: '充值', detail: '5000 积分套餐', cost: 5000, time: '2026-05-03 18:00' },
  { id: 5, kind: 'create', platform: 'douyin', title: '拆解抖音爆款', detail: 'v.douyin.com/iJxxxx', cost: -2, time: '2026-05-03 17:12' },
]

export default function CreditsPage() {
  const [filter, setFilter] = useState('all')
  const [showBuy, setShowBuy] = useState(false)
  const [selectedPkg, setSelectedPkg] = useState(PACKAGES[1].id)
  const [payMethod, setPayMethod] = useState('wechat')

  const balance = 6847
  const annualTotal = 8000
  const annualUsed = annualTotal - balance
  const annualPct = Math.round((annualUsed / annualTotal) * 100)

  const records = MOCK_RECORDS.filter((r) => filter === 'all' || r.kind === filter)
  const pkg = PACKAGES.find((p) => p.id === selectedPkg)

  return (
    <div className="credits-page">
      <header className="credits-header">
        <div>
          <h2>积分管理</h2>
          <p className="credits-sub">查看积分余额、消耗记录, 不够用了可以加购</p>
        </div>
        <button className="btn-buy" onClick={() => setShowBuy(true)}>+ 加购积分</button>
      </header>

      <section className="balance-card">
        <div className="balance-main">
          <div className="balance-label">当前余额</div>
          <div className="balance-num">{balance.toLocaleString()}</div>
          <div className="balance-tip">年卡到期前可用</div>
        </div>
        <div className="balance-progress">
          <div className="prog-row">
            <span>本年度已用 {annualUsed.toLocaleString()}</span>
            <span>年卡总额 {annualTotal.toLocaleString()}</span>
          </div>
          <div className="prog-bar">
            <div className="prog-fill" style={{ width: `${annualPct}%` }} />
          </div>
        </div>
      </section>

      <section className="records-section">
        <div className="records-head">
          <h3>消耗记录</h3>
          <div className="records-tabs">
            {FILTER_TABS.map((t) => (
              <button
                key={t.id}
                className={'records-tab' + (filter === t.id ? ' is-active' : '')}
                onClick={() => setFilter(t.id)}
              >
                {t.label}
              </button>
            ))}
          </div>
        </div>

        <div className="records-list">
          {records.map((r) => {
            const plat = r.platform ? PLATFORM_LABEL[r.platform] : null
            return (
              <div key={r.id} className="record-row">
                <div className="record-icon">{r.cost > 0 ? '+' : '-'}</div>
                <div className="record-main">
                  <div className="record-title">
                    <span>{r.title}</span>
                    {plat && <span className={'plat-tag ' + plat.cls}>{plat.label}</span>}
                  </div>
                  <div className="record-detail">{r.detail}</div>
                </div>
                <div className="record-side">
                  <div className={'record-cost ' + (r.cost > 0 ? 'is-pos' : '')}>
                    {r.cost > 0 ? '+' : ''}{r.cost}
                  </div>
                  <div className="record-time">{r.time}</div>
                </div>
              </div>
            )
          })}
        </div>

        <div className="records-foot">
          <button className="btn-text">加载更多</button>
          <a className="btn-text" href="#contact">联系客服</a>
        </div>
      </section>

      {showBuy && (
        <div className="modal-mask" onClick={() => setShowBuy(false)}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()}>
            <div className="modal-head">
              <h3>加购积分</h3>
              <button className="modal-close" onClick={() => setShowBuy(false)}>×</button>
            </div>

            <div className="pkg-grid">
              {PACKAGES.map((p) => (
                <button
                  key={p.id}
                  className={'pkg-card' + (selectedPkg === p.id ? ' is-active' : '')}
                  onClick={() => setSelectedPkg(p.id)}
                >
                  <div className="pkg-credits">{p.credits.toLocaleString()} 积分</div>
                  <div className="pkg-price">¥{p.price}</div>
                  {p.save > 0 && <div className="pkg-save">省 ¥{p.save}</div>}
                </button>
              ))}
            </div>

            <div className="pay-row">
              <div className="pay-label">支付方式</div>
              <div className="pay-methods">
                {[
                  { id: 'wechat', label: '微信支付' },
                  { id: 'alipay', label: '支付宝' },
                ].map((m) => (
                  <label key={m.id} className={'pay-opt' + (payMethod === m.id ? ' is-active' : '')}>
                    <input
                      type="radio"
                      name="pay"
                      checked={payMethod === m.id}
                      onChange={() => setPayMethod(m.id)}
                    />
                    {m.label}
                  </label>
                ))}
              </div>
            </div>

            <div className="modal-foot">
              <div className="modal-total">合计 ¥{pkg?.price || 0}</div>
              <button className="btn-confirm" onClick={() => alert('支付功能尚未对接')}>
                确认支付
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
