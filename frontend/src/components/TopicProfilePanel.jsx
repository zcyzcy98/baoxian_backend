import { useState } from 'react'
import './TopicProfilePanel.css'

const PRODUCT_OPTIONS = ['重疾', '医疗', '意外', '寿险', '年金', '教育金', '养老']
const AUDIENCE_OPTIONS = ['宝妈', '上班族', '老人', '00后', '中年人', '小孩', '已婚', '单身']

const STORAGE_KEY = 'topic-user-profile'

function loadProfile() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    return JSON.parse(raw)
  } catch {
    return null
  }
}

function saveProfile(p) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(p))
}

export default function TopicProfilePanel() {
  const initial = loadProfile() || {}
  const [primaryProducts, setPrimaryProducts] = useState(initial.primaryProducts || [])
  const [targetAudiences, setTargetAudiences] = useState(initial.targetAudiences || [])
  const [ageRange, setAgeRange] = useState(initial.ageRange || '')
  const [region, setRegion] = useState(initial.region || '')
  const [style, setStyle] = useState(initial.style || '')
  const [saved, setSaved] = useState(false)

  const toggle = (list, setList, value) => {
    const has = list.includes(value)
    setList(has ? list.filter((v) => v !== value) : [...list, value])
    setSaved(false)
  }

  const handleSave = () => {
    const p = { primaryProducts, targetAudiences, ageRange, region, style }
    saveProfile(p)
    setSaved(true)
    setTimeout(() => setSaved(false), 1800)
  }

  const handleReset = () => {
    if (!window.confirm('确定要清空你的画像设置吗?')) return
    localStorage.removeItem(STORAGE_KEY)
    setPrimaryProducts([])
    setTargetAudiences([])
    setAgeRange('')
    setRegion('')
    setStyle('')
    setSaved(false)
  }

  return (
    <div className="profile-panel">
      <header>
        <h2>我的画像</h2>
        <p className="profile-sub">
          这些信息会用于<b>个性化排序</b>选题广场, 也会传给小红书/视频生成的 prompt 让内容更贴合你的客群。
          仅保存在本地浏览器, 不上传服务器。
        </p>
      </header>

      <section>
        <label className="profile-label">主营险种 (多选)</label>
        <div className="chip-row">
          {PRODUCT_OPTIONS.map((p) => (
            <button
              key={p}
              className={`profile-chip ${primaryProducts.includes(p) ? 'on' : ''}`}
              onClick={() => toggle(primaryProducts, setPrimaryProducts, p)}
            >
              {p}
            </button>
          ))}
        </div>
      </section>

      <section>
        <label className="profile-label">目标客群 (多选)</label>
        <div className="chip-row">
          {AUDIENCE_OPTIONS.map((a) => (
            <button
              key={a}
              className={`profile-chip ${targetAudiences.includes(a) ? 'on' : ''}`}
              onClick={() => toggle(targetAudiences, setTargetAudiences, a)}
            >
              {a}
            </button>
          ))}
        </div>
      </section>

      <section className="profile-row">
        <label className="profile-label">客群年龄段</label>
        <input
          className="profile-input"
          type="text"
          value={ageRange}
          onChange={(e) => { setAgeRange(e.target.value); setSaved(false) }}
          placeholder="例如: 30-45"
        />
      </section>

      <section className="profile-row">
        <label className="profile-label">服务区域</label>
        <input
          className="profile-input"
          type="text"
          value={region}
          onChange={(e) => { setRegion(e.target.value); setSaved(false) }}
          placeholder="例如: 上海"
        />
      </section>

      <section className="profile-row">
        <label className="profile-label">偏好风格</label>
        <input
          className="profile-input"
          type="text"
          value={style}
          onChange={(e) => { setStyle(e.target.value); setSaved(false) }}
          placeholder="例如: 干货 + 治愈"
        />
      </section>

      <div className="profile-actions">
        <button className="btn-save" onClick={handleSave}>
          {saved ? '✅ 已保存' : '保存设置'}
        </button>
        <button className="btn-reset" onClick={handleReset}>
          清空
        </button>
      </div>
    </div>
  )
}
