import { useEffect, useState } from 'react'
import './ProfilePage.css'

const STORAGE_KEY = 'chengzhi:profile'

const PLATFORMS = [
  { id: 'xhs', name: '小红书' },
  { id: 'gzh', name: '微信公众号' },
  { id: 'douyin', name: '抖音' },
  { id: 'video', name: '视频号' },
]

const PRODUCT_OPTIONS = ['重疾险', '医疗险', '意外险', '寿险', '年金险', '教育金', '养老金']
const AUDIENCE_OPTIONS = ['宝妈', '上班族', '中产家庭', '老人', '小孩', '00后', '已婚']

const DEFAULT = {
  name: '',
  title: '',
  city: '',
  intro: '',
  products: [],
  audiences: [],
  tags: '',
  bindings: {},  // { platformId: 'account_handle' }
}

function load() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? { ...DEFAULT, ...JSON.parse(raw) } : DEFAULT
  } catch { return DEFAULT }
}

export default function ProfilePage() {
  const [data, setData] = useState(load)
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    if (!saved) return
    const t = setTimeout(() => setSaved(false), 1500)
    return () => clearTimeout(t)
  }, [saved])

  const update = (patch) => setData((d) => ({ ...d, ...patch }))
  const toggleArr = (key, value) => {
    setData((d) => {
      const arr = d[key] || []
      return { ...d, [key]: arr.includes(value) ? arr.filter((x) => x !== value) : [...arr, value] }
    })
  }
  const handleSave = () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
    setSaved(true)
  }
  const handleBindToggle = (id) => {
    setData((d) => {
      const next = { ...d.bindings }
      if (next[id]) delete next[id]
      else next[id] = `@user_${id}_${Math.random().toString(36).slice(2, 6)}`
      return { ...d, bindings: next }
    })
  }

  return (
    <div className="profile-page">
      <header className="profile-header">
        <div>
          <h2>个人信息</h2>
          <p className="page-sub">基础资料 + 业务画像 + 平台账号绑定; 改完点保存</p>
        </div>
        <button className={'btn-save' + (saved ? ' is-saved' : '')} onClick={handleSave}>
          {saved ? '✓ 已保存' : '保存设置'}
        </button>
      </header>

      <section className="profile-card">
        <div className="card-head">
          <h3>基本信息</h3>
        </div>
        <div className="profile-grid">
          <Field label="姓名">
            <input className="profile-input" value={data.name} placeholder="例如: 林老师"
              onChange={(e) => update({ name: e.target.value })} />
          </Field>
          <Field label="职业头衔">
            <input className="profile-input" value={data.title} placeholder="例如: 中产家庭保障顾问"
              onChange={(e) => update({ title: e.target.value })} />
          </Field>
          <Field label="服务城市">
            <input className="profile-input" value={data.city} placeholder="例如: 上海"
              onChange={(e) => update({ city: e.target.value })} />
          </Field>
        </div>
        <Field label="简介 / 一句话介绍">
          <textarea className="profile-input" rows={2} value={data.intro}
            placeholder="例如: 5 年保险从业, 专注中产家庭保障规划"
            onChange={(e) => update({ intro: e.target.value })} />
        </Field>
      </section>

      <section className="profile-card">
        <div className="card-head">
          <h3>业务画像</h3>
          <p className="card-tip">用于选题广场个性化排序、改写时套用人群语境</p>
        </div>
        <Field label="主营险种 (多选)">
          <div className="chip-row">
            {PRODUCT_OPTIONS.map((p) => (
              <button key={p}
                className={'chip' + (data.products.includes(p) ? ' is-active' : '')}
                onClick={() => toggleArr('products', p)}>
                {p}
              </button>
            ))}
          </div>
        </Field>
        <Field label="目标客群 (多选)">
          <div className="chip-row">
            {AUDIENCE_OPTIONS.map((a) => (
              <button key={a}
                className={'chip' + (data.audiences.includes(a) ? ' is-active' : '')}
                onClick={() => toggleArr('audiences', a)}>
                {a}
              </button>
            ))}
          </div>
        </Field>
        <Field label="自定义标签 (逗号分隔)">
          <input className="profile-input" value={data.tags}
            placeholder="如: 北上广深, 宝妈群, 高净值"
            onChange={(e) => update({ tags: e.target.value })} />
        </Field>
      </section>

      <section className="profile-card">
        <div className="card-head">
          <h3>平台账号绑定</h3>
          <p className="card-tip">绑定后, 后续创作可以一键发到对应平台 (功能开发中)</p>
        </div>
        <div className="binding-list">
          {PLATFORMS.map((p) => {
            const acct = data.bindings?.[p.id]
            return (
              <div key={p.id} className="binding-row">
                <div className={'plat-icon plat-' + p.id}>{p.name.charAt(0)}</div>
                <div className="binding-info">
                  <div className="binding-name">{p.name}</div>
                  <div className="binding-acct">{acct || '未绑定'}</div>
                </div>
                <button
                  className={'btn-bind' + (acct ? ' is-bound' : '')}
                  onClick={() => handleBindToggle(p.id)}>
                  {acct ? '解绑' : '立即绑定'}
                </button>
              </div>
            )
          })}
        </div>
        <p className="binding-tip">⚠️ 当前为本地占位, 真实 OAuth 绑定需后端实现</p>
      </section>
    </div>
  )
}

function Field({ label, children }) {
  return (
    <div className="profile-field">
      <label className="profile-label">{label}</label>
      {children}
    </div>
  )
}
