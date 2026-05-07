import { useEffect, useRef, useState } from 'react'
import './ProfilePage.css'

const PLATFORMS = [
  { id: 'xhs', name: '小红书' },
  { id: 'gzh', name: '微信公众号' },
  { id: 'dy',  name: '抖音' },
  { id: 'sph', name: '视频号' },
]

const PRODUCT_OPTIONS  = ['重疾险', '医疗险', '意外险', '寿险', '年金险', '教育金', '养老金']
const AUDIENCE_OPTIONS = ['宝妈', '上班族', '中产家庭', '老人', '小孩', '00后', '已婚']

const DEFAULT = {
  name: '', title: '', city: '', intro: '',
  products: [], audiences: [], tags: '',
  avatarUrl: null,
  platforms: [],
}

// ─── API helpers ──────────────────────────────────────────
async function apiGet(path) {
  const res = await fetch(path)
  if (!res.ok) throw new Error(`GET ${path} ${res.status}`)
  return res.json()
}
async function apiPut(path, body) {
  const res = await fetch(path, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(`PUT ${path} ${res.status}`)
  return res.json()
}
async function apiPost(path, body) {
  const res = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(`POST ${path} ${res.status}`)
}
async function apiDelete(path) {
  const res = await fetch(path, { method: 'DELETE' })
  if (!res.ok) throw new Error(`DELETE ${path} ${res.status}`)
}

// 后端 DTO → 前端 state
function fromDto(dto) {
  return {
    name:      dto.name      || '',
    title:     dto.years     || '',
    city:      dto.region    || '',
    intro:     dto.bio       || '',
    products:  dto.primaryProducts  || [],
    audiences: dto.targetAudiences  || [],
    tags:      '',
    avatarUrl: dto.avatarUrl || null,
    platforms: dto.platforms || [],
  }
}

// 前端 state → 后端 DTO
function toDto(data) {
  return {
    name:            data.name,
    years:           data.title,
    region:          data.city,
    bio:             data.intro,
    avatarUrl:       data.avatarUrl,
    primaryProducts: data.products,
    targetAudiences: data.audiences,
  }
}

// ─── 组件 ─────────────────────────────────────────────────
export default function ProfilePage() {
  const [data,    setData]    = useState(DEFAULT)
  const [status,  setStatus]  = useState('idle')   // idle | saving | saved | error
  const [loading, setLoading] = useState(true)
  const fileRef = useRef(null)

  useEffect(() => {
    apiGet('/api/profile')
      .then(dto => setData(fromDto(dto)))
      .catch(e  => console.error('[Profile] 加载失败', e))
      .finally(() => setLoading(false))
  }, [])

  const update    = (patch) => setData(d => ({ ...d, ...patch }))
  const toggleArr = (key, value) => setData(d => {
    const arr = d[key] || []
    return { ...d, [key]: arr.includes(value) ? arr.filter(x => x !== value) : [...arr, value] }
  })

  const handleSave = async () => {
    setStatus('saving')
    try {
      const dto = await apiPut('/api/profile', toDto(data))
      setData(prev => ({ ...prev, platforms: dto.platforms || prev.platforms }))
      setStatus('saved')
      setTimeout(() => setStatus('idle'), 1800)
    } catch (e) {
      console.error('[Profile] 保存失败', e)
      setStatus('error')
      setTimeout(() => setStatus('idle'), 2500)
    }
  }

  const handleAvatarChange = async (e) => {
    const file = e.target.files[0]
    if (!file) return
    const fd = new FormData()
    fd.append('file', file)
    try {
      const res  = await fetch('/api/avatar/upload', { method: 'POST', body: fd })
      const json = await res.json()
      if (json.url) {
        const next = { ...data, avatarUrl: json.url }
        setData(prev => ({ ...prev, avatarUrl: json.url }))
        await apiPut('/api/profile', toDto(next))
      }
    } catch (e) {
      console.error('[Profile] 头像上传失败', e)
    }
    e.target.value = ''
  }

  const handleBind = async (platformId, label) => {
    const acct = window.prompt(`绑定 ${label}\n\n请输入账号名称（如 @xxx）：`)
    if (acct === null) return
    try {
      await apiPost('/api/profile/platform', { platform: platformId, accountName: acct })
      const dto = await apiGet('/api/profile')
      setData(prev => ({ ...prev, platforms: dto.platforms || [] }))
    } catch (e) {
      console.error('[Profile] 绑定失败', e)
    }
  }

  const handleUnbind = async (platformId, label) => {
    if (!window.confirm(`确认解绑 ${label} 账号吗？`)) return
    try {
      await apiDelete(`/api/profile/platform/${platformId}`)
      const dto = await apiGet('/api/profile')
      setData(prev => ({ ...prev, platforms: dto.platforms || [] }))
    } catch (e) {
      console.error('[Profile] 解绑失败', e)
    }
  }

  const btnLabel = { saving: '保存中…', saved: '✓ 已保存', error: '保存失败' }[status] || '保存设置'

  if (loading) return (
    <div className="profile-page" style={{ padding: 40, color: 'var(--ink-3)' }}>加载中…</div>
  )

  return (
    <div className="profile-page">
      <header className="profile-header">
        <div>
          <h2>个人信息</h2>
          <p className="page-sub">基础资料 · 业务画像 · 平台账号绑定</p>
        </div>
        <button
          className={'btn-save' + (status === 'saved' ? ' is-saved' : status === 'error' ? ' is-error' : '')}
          onClick={handleSave}
          disabled={status === 'saving'}>
          {btnLabel}
        </button>
      </header>

      {/* 基本信息 */}
      <section className="profile-card">
        <div className="card-head"><h3>基本信息</h3></div>

        <div className="avatar-row">
          <div className="avatar-circle" onClick={() => fileRef.current?.click()}>
            {data.avatarUrl
              ? <img src={data.avatarUrl} alt="avatar" style={{ width: '100%', height: '100%', objectFit: 'cover', borderRadius: '50%' }} />
              : <span>{(data.name || '?')[0]}</span>
            }
            <div className="avatar-overlay">换图</div>
          </div>
          <input ref={fileRef} type="file" accept="image/*" style={{ display: 'none' }} onChange={handleAvatarChange} />
        </div>

        <div className="profile-grid">
          <Field label="姓名 / 称呼">
            <input className="profile-input" value={data.name} placeholder="例如: 林老师"
              onChange={e => update({ name: e.target.value })} />
          </Field>
          <Field label="职业头衔 / 从业年限">
            <input className="profile-input" value={data.title} placeholder="例如: 5 年保障顾问"
              onChange={e => update({ title: e.target.value })} />
          </Field>
          <Field label="服务城市">
            <input className="profile-input" value={data.city} placeholder="例如: 上海"
              onChange={e => update({ city: e.target.value })} />
          </Field>
        </div>
        <Field label="一句话简介">
          <textarea className="profile-input" rows={2} value={data.intro}
            placeholder="例如: 专注中产家庭保障规划，服务超 200 个家庭"
            onChange={e => update({ intro: e.target.value })} />
        </Field>
      </section>

      {/* 业务画像 */}
      <section className="profile-card">
        <div className="card-head">
          <h3>业务画像</h3>
          <p className="card-tip">用于选题广场个性化排序、改写时套用人群语境</p>
        </div>
        <Field label="主营险种 (多选)">
          <div className="chip-row">
            {PRODUCT_OPTIONS.map(p => (
              <button key={p} className={'chip' + (data.products.includes(p) ? ' is-active' : '')}
                onClick={() => toggleArr('products', p)}>{p}</button>
            ))}
          </div>
        </Field>
        <Field label="目标客群 (多选)">
          <div className="chip-row">
            {AUDIENCE_OPTIONS.map(a => (
              <button key={a} className={'chip' + (data.audiences.includes(a) ? ' is-active' : '')}
                onClick={() => toggleArr('audiences', a)}>{a}</button>
            ))}
          </div>
        </Field>
        <Field label="自定义标签 (逗号分隔)">
          <input className="profile-input" value={data.tags}
            placeholder="如: 北上广深, 宝妈群, 高净值"
            onChange={e => update({ tags: e.target.value })} />
        </Field>
      </section>

      {/* 平台绑定 */}
      <section className="profile-card">
        <div className="card-head">
          <h3>平台账号绑定</h3>
          <p className="card-tip">绑定后可用于一键发布（功能持续完善中）</p>
        </div>
        <div className="binding-list">
          {PLATFORMS.map(p => {
            const bound = data.platforms.find(b => b.platform === p.id)
            return (
              <div key={p.id} className="binding-row">
                <div className={'plat-icon plat-' + p.id}>{p.name.charAt(0)}</div>
                <div className="binding-info">
                  <div className="binding-name">{p.name}</div>
                  <div className="binding-acct">{bound ? (bound.accountName || '已绑定') : '未绑定'}</div>
                </div>
                <button
                  className={'btn-bind' + (bound ? ' is-bound' : '')}
                  onClick={() => bound ? handleUnbind(p.id, p.name) : handleBind(p.id, p.name)}>
                  {bound ? '解绑' : '立即绑定'}
                </button>
              </div>
            )
          })}
        </div>
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
