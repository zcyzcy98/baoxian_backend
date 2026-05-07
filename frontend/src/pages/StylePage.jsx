import { useEffect, useState, useCallback } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { fetchStyleProfile, addStyleSource, deleteStyleSource, trainStyle, previewStyle } from '../api'
import './StylePage.css'

const RADAR_KEYS = ['语气温度', '专业密度', '句式节奏', '情绪强度', '修辞偏好', '结构习惯']
const PREVIEW_TOPICS = ['重疾险避坑', '医疗险怎么选', '给父母配保险', '年金险和储蓄险', '百万医疗险']

// ─── Radar Chart (SVG) ──────────────────────────────────────────────────────
function RadarChart({ radar }) {
  const size = 240
  const cx = size / 2
  const cy = size / 2
  const R  = 90
  const n  = RADAR_KEYS.length
  const levels = [2, 4, 6, 8, 10]

  const angle = (i) => (Math.PI * 2 * i) / n - Math.PI / 2

  const point = (i, r) => ({
    x: cx + r * Math.cos(angle(i)),
    y: cy + r * Math.sin(angle(i)),
  })

  const labelOffset = (i) => {
    const a = angle(i)
    const dx = Math.cos(a)
    const dy = Math.sin(a)
    const px = cx + (R + 22) * dx
    const py = cy + (R + 22) * dy
    let anchor = 'middle'
    if (dx > 0.3) anchor = 'start'
    else if (dx < -0.3) anchor = 'end'
    return { x: px, y: py, anchor }
  }

  const gridPath = (level) => {
    const r = (level / 10) * R
    return RADAR_KEYS.map((_, i) => {
      const { x, y } = point(i, r)
      return i === 0 ? `M${x},${y}` : `L${x},${y}`
    }).join(' ') + 'Z'
  }

  const dataPath = () => {
    if (!radar) return ''
    return RADAR_KEYS.map((key, i) => {
      const val = radar[key] ?? 5
      const r = (val / 10) * R
      const { x, y } = point(i, r)
      return i === 0 ? `M${x},${y}` : `L${x},${y}`
    }).join(' ') + 'Z'
  }

  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="radar-svg">
      {/* Grid levels */}
      {levels.map(lv => (
        <path key={lv} d={gridPath(lv)} fill="none" stroke="var(--line)" strokeWidth="1" />
      ))}
      {/* Axis lines */}
      {RADAR_KEYS.map((_, i) => {
        const { x, y } = point(i, R)
        return <line key={i} x1={cx} y1={cy} x2={x} y2={y} stroke="var(--line)" strokeWidth="1" />
      })}
      {/* Data polygon */}
      {radar && (
        <>
          <path d={dataPath()} fill="rgba(204,120,92,0.15)" stroke="var(--clay)" strokeWidth="2" />
          {RADAR_KEYS.map((key, i) => {
            const val = radar[key] ?? 5
            const r = (val / 10) * R
            const { x, y } = point(i, r)
            return <circle key={i} cx={x} cy={y} r="4" fill="var(--clay)" />
          })}
        </>
      )}
      {/* Labels */}
      {RADAR_KEYS.map((key, i) => {
        const { x, y, anchor } = labelOffset(i)
        return (
          <text key={i} x={x} y={y} textAnchor={anchor} dominantBaseline="middle"
            fontSize="11" fill="var(--ink-2)" fontFamily="var(--sans)">
            {key}
          </text>
        )
      })}
    </svg>
  )
}

// ─── Main Page ───────────────────────────────────────────────────────────────
export default function StylePage() {
  const [profile, setProfile]   = useState(null)
  const [sources, setSources]   = useState([])
  const [loading, setLoading]   = useState(true)
  const [training, setTraining] = useState(false)
  const [showAdd, setShowAdd]   = useState(false)
  const [showPreview, setShowPreview] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const data = await fetchStyleProfile()
      setProfile(data.profile)
      setSources(data.sources || [])
    } catch (e) {
      console.warn('load profile failed:', e)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load() }, [load])

  const handleTrain = async () => {
    if (sources.length === 0) return alert('请先添加至少一篇素材')
    setTraining(true)
    try {
      const result = await trainStyle()
      setProfile({ ...result, trainedAt: new Date().toISOString() })
    } catch (e) {
      alert('训练失败: ' + e.message)
    } finally {
      setTraining(false)
    }
  }

  const handleAddDone = (source) => {
    setSources(prev => [source, ...prev])
    setShowAdd(false)
  }

  const handleDelete = async (id) => {
    if (!confirm('确认删除这篇素材？')) return
    try {
      await deleteStyleSource(id)
      setSources(prev => prev.filter(s => s.id !== id))
    } catch (e) {
      alert('删除失败: ' + e.message)
    }
  }

  const formatTime = (iso) => {
    if (!iso) return ''
    const diff = Date.now() - new Date(iso).getTime()
    const h = Math.floor(diff / 3600000)
    const d = Math.floor(diff / 86400000)
    if (h < 1) return '刚刚'
    if (h < 24) return `${h} 小时前`
    return `${d} 天前`
  }

  const totalWords = sources.reduce((s, r) => s + (r.wordCount || 0), 0)

  return (
    <div className="style-page">
      {/* ── Hero Section ── */}
      <div className="style-hero-wrap">
        {profile ? (
          <>
            {/* Top meta */}
            <div className="style-meta-bar">
              <div className="style-version-badge">V{profile.version || 1}</div>
              <span className="style-meta-item">基于 <b>{profile.sourceCount}</b> 篇素材</span>
              <span className="style-meta-dot">·</span>
              <span className="style-meta-item"><b>{(profile.totalWords || 0).toLocaleString()}</b> 字</span>
              <span className="style-meta-dot">·</span>
              <span className="style-meta-item">最后训练 <b>{formatTime(profile.trainedAt)}</b></span>
              <button className="btn-retrain" onClick={handleTrain} disabled={training}>
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
                {training ? '训练中…' : '重新训练'}
              </button>
            </div>

            {/* Signature */}
            <div className="style-signature-block">
              <div className="style-signature-label">— STYLE SIGNATURE · 风格签名</div>
              <div className="style-signature-text">{profile.signature}</div>
              <div className="style-signature-sub">由 AI 从你的 {profile.sourceCount} 篇素材里提取</div>
            </div>

            {/* Radar + Traits */}
            <div className="style-analysis-grid">
              <div className="style-radar-card">
                <div className="analysis-label">— STYLE RADAR · 6维度雷达</div>
                <RadarChart radar={profile.radar} />
                {profile.radar && (
                  <div className="radar-scores">
                    {RADAR_KEYS.map(k => (
                      <div key={k} className="radar-score-row">
                        <span className="rs-label">{k}</span>
                        <span className="rs-val">{(profile.radar[k] ?? 0).toFixed(1)} <span className="rs-max">/ 10</span></span>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <div className="style-traits-card">
                <div className="analysis-label">— STYLE TRAITS · 风格特征标签</div>
                <div className="traits-cloud">
                  {(profile.traits || []).map((t, i) => (
                    <span key={i} className={`trait-tag ${t.primary ? 'primary' : ''}`}>{t.text}</span>
                  ))}
                </div>
              </div>
            </div>

            {/* Preview CTA */}
            <button className="btn-preview-style" onClick={() => setShowPreview(true)}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"><path d="M12 2L13.5 8.5L20 10L13.5 11.5L12 18L10.5 11.5L4 10L10.5 8.5L12 2Z"/></svg>
              让 AI 用我的风格写一段
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
            </button>
          </>
        ) : (
          <div className="style-empty-hero">
            <div className="empty-hero-icon">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2"><path d="M12 2L13.5 8.5L20 10L13.5 11.5L12 18L10.5 11.5L4 10L10.5 8.5L12 2Z"/></svg>
            </div>
            <h3>还没有训练过风格</h3>
            <p>上传 5-10 篇你的代表作品，AI 会提取你的写作风格特征</p>
            {training ? (
              <div className="training-spinner">
                <div className="spinner"></div>
                <span>AI 正在分析你的写作风格…</span>
              </div>
            ) : (
              <button className="btn-start-train" onClick={handleTrain} disabled={sources.length === 0}>
                {sources.length === 0 ? '先在下方添加素材' : `开始训练（${sources.length} 篇素材）`}
              </button>
            )}
          </div>
        )}

        {training && profile && (
          <div className="training-overlay">
            <div className="spinner"></div>
            <span>AI 正在重新分析你的写作风格…</span>
          </div>
        )}
      </div>

      {/* ── Sources Section ── */}
      <div className="style-sources-wrap">
        <div className="sources-header">
          <div className="sources-header-left">
            <h3 className="sources-title">训练素材库</h3>
            <span className="sources-count">{sources.length} 篇 · {totalWords.toLocaleString()} 字</span>
          </div>
          <button className="btn-add-source" onClick={() => setShowAdd(true)}>
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            添加素材
          </button>
        </div>

        {loading ? (
          <div className="sources-loading">加载中…</div>
        ) : sources.length === 0 ? (
          <div className="sources-empty">
            <div className="empty-icon-wrap">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>
            </div>
            <p className="empty-title">还没有素材</p>
            <p className="empty-desc">建议添加 5–10 篇你自己写的代表作品，AI 抽取风格更准确</p>
          </div>
        ) : (
          <div className="sources-list">
            {sources.map(s => (
              <div key={s.id} className="source-card">
                <div className={`source-type-badge type-${s.contentType}`}>
                  {typeIcon(s.contentType)}
                </div>
                <div className="source-body">
                  <div className="source-title">{s.title || '未命名素材'}</div>
                  <div className="source-preview">{s.preview}</div>
                </div>
                <div className="source-meta">
                  <span className="source-words">{(s.wordCount || 0).toLocaleString()} 字</span>
                  <button className="source-del" onClick={() => handleDelete(s.id)} title="删除">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6M14 11v6"/><path d="M9 6V4h6v2"/></svg>
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* ── Modals ── */}
      {showAdd && (
        <AddSourceModal onDone={handleAddDone} onClose={() => setShowAdd(false)} />
      )}
      {showPreview && (
        <PreviewModal profile={profile} onClose={() => setShowPreview(false)} />
      )}
    </div>
  )
}

function typeIcon(type) {
  switch (type) {
    case 'xhs':  return '小'
    case 'gzh':  return '公'
    case 'link': return '🔗'
    default:     return '文'
  }
}

/** 从任意粘贴文本中提取第一个 http(s) URL */
function extractUrlFromPaste(text) {
  if (!text) return ''
  const m = text.match(/https?:\/\/[^\s一-龥[\]【】「」（）()]+/)
  if (!m) return text.trim()
  return m[0].replace(/[,，。.!！?？]+$/, '')
}

function detectPlatform(url) {
  if (!url) return null
  const lower = url.toLowerCase()
  if (lower.includes('xiaohongshu') || lower.includes('xhslink')) return 'xhs'
  if (lower.includes('mp.weixin') || lower.includes('weixin.qq')) return 'gzh'
  if (lower.includes('douyin') || lower.includes('iesdouyin') || lower.includes('v.douyin')) return 'douyin'
  if (lower.startsWith('http')) return 'link'
  return null
}

const PLATFORM_LABEL = { xhs: '小红书', gzh: '公众号', douyin: '抖音', link: '网页链接' }
const PLATFORM_COLOR = { xhs: '#ff2442', gzh: '#07c160', douyin: '#000', link: 'var(--leaf)' }

// ─── Add Source Modal ────────────────────────────────────────────────────────
function AddSourceModal({ onDone, onClose }) {
  const [tab, setTab]           = useState('text')
  const [title, setTitle]       = useState('')
  const [text, setText]         = useState('')
  const [rawPaste, setRawPaste] = useState('')   // 原始粘贴内容（可能含乱七八糟的文字）
  const [cleanUrl, setCleanUrl] = useState('')   // 提取出的干净 URL
  const [loading, setLoading]   = useState(false)
  const [error, setError]       = useState('')

  const platform = detectPlatform(cleanUrl)
  const canSubmit = !loading && ((tab === 'text' && text.trim()) || (tab === 'link' && cleanUrl))

  const handlePasteChange = (val) => {
    setRawPaste(val)
    const extracted = extractUrlFromPaste(val)
    setCleanUrl(extracted)
  }

  const handleSubmit = async () => {
    setLoading(true)
    setError('')
    try {
      const payload = tab === 'link'
        ? { type: 'link', title, url: rawPaste }   // 传原始文本，后端自己提取
        : { type: 'text', title, rawText: text }
      const source = await addStyleSource(payload)
      onDone(source)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-sheet" onClick={e => e.stopPropagation()}>
        <div className="modal-top">
          <h3>添加训练素材</h3>
          <button className="modal-close-btn" onClick={onClose}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>

        <div className="modal-tabs">
          <button className={`mtab ${tab === 'text' ? 'active' : ''}`} onClick={() => setTab('text')}>粘贴文字</button>
          <button className={`mtab ${tab === 'link' ? 'active' : ''}`} onClick={() => setTab('link')}>
            添加链接
            <span className="mtab-badge">自动提取</span>
          </button>
        </div>

        <div className="modal-field">
          <label>标题（选填）</label>
          <input
            className="modal-input"
            value={title}
            placeholder="例如：那篇关于宝妈医疗险的小红书"
            onChange={e => setTitle(e.target.value)}
          />
        </div>

        {tab === 'text' ? (
          <div className="modal-field">
            <label>粘贴你写过的内容 <span className="field-hint">越完整越好，至少 200 字</span></label>
            <textarea
              className="modal-textarea"
              value={text}
              rows={8}
              placeholder="把你写过的代表性文章粘贴在这里…"
              onChange={e => setText(e.target.value)}
            />
            <div className="word-hint">{text.length} 字</div>
          </div>
        ) : (
          <div className="modal-field">
            <label>
              直接粘贴分享内容
              <span className="field-hint">支持小红书 / 公众号，可连表情文字一起粘贴</span>
            </label>
            <textarea
              className="modal-textarea modal-textarea-link"
              value={rawPaste}
              rows={3}
              placeholder={'直接粘贴分享文本即可，例如：\n24 【一篇教你车险三家怎么买】 😆 3e3X... https://www.xiaohongshu.com/...'}
              onChange={e => handlePasteChange(e.target.value)}
            />
            {/* 已识别到的 URL 预览 */}
            {cleanUrl && (
              <div className="url-parsed-preview">
                {platform && (
                  <span className="url-platform-badge" style={{ background: PLATFORM_COLOR[platform] }}>
                    {PLATFORM_LABEL[platform]}
                  </span>
                )}
                <span className="url-parsed-text">{cleanUrl}</span>
              </div>
            )}
            {rawPaste && !cleanUrl && (
              <p className="field-tip field-tip-warn">未识别到有效链接，请检查粘贴内容</p>
            )}
            {!rawPaste && (
              <p className="field-tip">系统自动从分享文本中提取链接，如提取失败请改用「粘贴文字」</p>
            )}
          </div>
        )}

        {error && <div className="modal-error">{error}</div>}

        <div className="modal-footer">
          <button className="btn-ghost" onClick={onClose}>取消</button>
          <button className="btn-solid" disabled={!canSubmit} onClick={handleSubmit}>
            {loading ? '处理中…' : '添加到素材库'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── Preview Modal ───────────────────────────────────────────────────────────
function PreviewModal({ profile, onClose }) {
  const [topic, setTopic]     = useState(PREVIEW_TOPICS[0])
  const [custom, setCustom]   = useState('')
  const [result, setResult]   = useState('')
  const [loading, setLoading] = useState(false)

  const generate = async (t) => {
    setLoading(true)
    setResult('')
    try {
      const res = await previewStyle(t || topic)
      setResult(res.content || '')
    } catch (e) {
      setResult('生成失败: ' + e.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { generate(topic) }, [])

  const effectiveTopic = custom.trim() || topic

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-sheet preview-sheet" onClick={e => e.stopPropagation()}>
        <div className="modal-top">
          <div>
            <h3>风格预览</h3>
            <p className="preview-subtitle">AI 正在用你的风格写作：<b>{profile?.signature}</b></p>
          </div>
          <button className="modal-close-btn" onClick={onClose}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>

        <div className="preview-topic-bar">
          <span className="preview-topic-label">主题：</span>
          <div className="preview-chips">
            {PREVIEW_TOPICS.map(t => (
              <button
                key={t}
                className={`pchip ${topic === t && !custom ? 'active' : ''}`}
                onClick={() => { setTopic(t); setCustom(''); generate(t) }}
              >{t}</button>
            ))}
          </div>
          <input
            className="preview-custom-input"
            placeholder="自定义主题…"
            value={custom}
            onChange={e => setCustom(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && custom.trim() && generate(custom.trim())}
          />
          <button className="btn-regen" onClick={() => generate(effectiveTopic)} disabled={loading}>
            {loading ? '…' : '换一个'}
          </button>
        </div>

        <div className="preview-body">
          {loading ? (
            <div className="preview-loading">
              <div className="spinner"></div>
              <span>AI 正在用你的风格写作…</span>
            </div>
          ) : (
            <div className="preview-content">
              <ReactMarkdown remarkPlugins={[remarkGfm]}>{result}</ReactMarkdown>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
