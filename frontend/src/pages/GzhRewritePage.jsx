import { useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { rewriteWechat, extractAutoNote } from '../api'
import { copyToClipboard, stripMarkdown } from '../utils/markdown'
import { proxyImageUrl } from '../utils/imageProxy'
import './XhsRewritePage.css'

// 从文本中提取链接
function extractUrlFromText(text) {
  if (!text) return ''
  // 匹配常见的 URL 模式
  const urlMatch = text.match(/https?:\/\/[^\s<>"{}|\\^`\[\]]+/)
  return urlMatch ? urlMatch[0] : ''
}

const REWRITE_MODES = [
  { id: 'mp_rewrite', label: '深度改写' },
  { id: 'rewrite',    label: '常规改写' },
  { id: 'synonym',    label: '同义改写' },
  { id: 'mp_style',   label: '转公众号风格' },
]

const STEPS = [
  { id: 1, label: '提供原文 + 改写要求' },
  { id: 2, label: '改写结果' },
  { id: 3, label: '配图 (4 张)' },
]

export default function GzhRewritePage({ topicPrefill, onPrefillConsumed }) {
  const [step, setStep] = useState(1)
  const [url, setUrl] = useState('')
  const [extracted, setExtracted] = useState(null)
  const [extracting, setExtracting] = useState(false)
  const [requirements, setRequirements] = useState('')
  const [useStyle, setUseStyle] = useState(false)
  const [audience, setAudience] = useState([])
  const [products, setProducts] = useState([])
  const [mode, setMode] = useState('')
  const [rewriting, setRewriting] = useState(false)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')

  const handleUrlChange = (e) => {
    const inputText = e.target.value
    const extractedUrl = extractUrlFromText(inputText)
    setUrl(extractedUrl || inputText)
  }

  const handleExtract = async () => {
    if (!url.trim() || extracting) return
    setError('')
    setExtracting(true)
    try {
      const r = await extractAutoNote(url.trim())
      setExtracted(r?.note || r)
    } catch (err) {
      setError(err.message)
    } finally {
      setExtracting(false)
    }
  }

  const handleRewrite = async () => {
    if (rewriting) return
    setError('')
    setRewriting(true)
    try {
      const payload = url.trim()
        ? { url: url.trim(), mode, requirements: buildRequirements() }
        : {
            title: extracted?.title || '',
            content: extracted?.content || '',
            mode,
            requirements: buildRequirements(),
          }
      const r = await rewriteWechat(payload)
      setResult(r)
      setStep(2)
    } catch (err) {
      setError(err.message)
    } finally {
      setRewriting(false)
    }
  }

  const buildRequirements = () => {
    let parts = [requirements.trim()]
    if (useStyle) parts.push('请套用我训练好的个人风格 (从 localStorage 读取)')
    if (audience.length) parts.push('目标人群: ' + audience.join('、'))
    if (products.length) parts.push('涉及险种: ' + products.join('、'))
    return parts.filter(Boolean).join('\n')
  }

  const toggle = (arr, setArr, v) => setArr(arr.includes(v) ? arr.filter((x) => x !== v) : [...arr, v])

  return (
    <div className="rewrite-page">
      <header className="rewrite-header">
        <div>
          <h2><span className="platform-tag gzh">公众号</span> 仿写</h2>
          <p className="page-sub">提取一篇公众号文章 → 按要求改写 → 配图</p>
        </div>
      </header>

      <div className="step-bar">
        {STEPS.map((s) => (
          <div key={s.id} className={'step-item' + (step >= s.id ? ' is-done' : '') + (step === s.id ? ' is-active' : '')}>
            <div className="step-num">{s.id}</div>
            <div className="step-label">{s.label}</div>
          </div>
        ))}
      </div>

      {step === 1 && (
        <section className="rewrite-card">
          <div className="form-field">
            <label className="form-label">公众号文章链接 <span className="required">*</span></label>
            <input className="form-input" value={url}
              placeholder="粘贴 mp.weixin.qq.com/s 链接（支持粘贴带文字的分享内容）"
              onChange={handleUrlChange} />
            <button className="btn-ghost" onClick={handleExtract} disabled={!url.trim() || extracting}>
              {extracting ? '提取中…' : '先提取原文 (可选)'}
            </button>
          </div>

          {extracted && (
            <div className="extracted-preview">
              {extracted.cover && (
                <a href={extracted.cover} target="_blank" rel="noreferrer">
                  <img src={proxyImageUrl(extracted.cover)} alt="封面图" className="ext-cover"
                    onError={(e) => { e.currentTarget.style.display = 'none' }} />
                </a>
              )}
              <div className="ext-title">{extracted.title}</div>
              <div className="ext-body">{(extracted.content || '').slice(0, 400)}…</div>
              {extracted.imageUrls?.length > 0 && (
                <div className="extracted-images">
                  <div className="extracted-images-label">正文图片 ({extracted.imageUrls.length} 张)</div>
                  <div className="extracted-images-row">
                    {extracted.imageUrls.map((url, i) => (
                      <a key={i} href={url} target="_blank" rel="noreferrer" className="extracted-img-link">
                        <img src={proxyImageUrl(url)} alt={`图片${i + 1}`} className="extracted-thumb"
                          onError={(e) => { e.currentTarget.style.display = 'none' }} />
                      </a>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          <div className="form-field">
            <label className="form-label">想怎么改 <span className="required">*</span></label>
            <textarea className="form-input" rows={3}
              placeholder="例如: 把语气更年轻化, 强化数据点, 删掉口播感"
              value={requirements} onChange={(e) => setRequirements(e.target.value)} />
          </div>

          <div className="form-field">
            <label className="form-checkbox">
              <input type="checkbox" checked={useStyle} onChange={(e) => setUseStyle(e.target.checked)} />
              套用我的个人风格 (来自"个人风格"页训练的素材)
            </label>
          </div>

          <div className="form-field">
            <label className="form-label">改写模式</label>
            <div className="chip-row">
              {REWRITE_MODES.map((m) => (
                <button key={m.id}
                  className={'chip' + (mode === m.id ? ' is-active' : '')}
                  onClick={() => setMode(m.id)}>{m.label}</button>
              ))}
            </div>
          </div>

          <div className="form-field">
            <label className="form-label">目标人群 (可选)</label>
            <div className="chip-row">
              {['宝妈', '上班族', '中产家庭', '老人', '00后'].map((a) => (
                <button key={a} className={'chip' + (audience.includes(a) ? ' is-active' : '')}
                  onClick={() => toggle(audience, setAudience, a)}>{a}</button>
              ))}
            </div>
          </div>

          <div className="form-field">
            <label className="form-label">涉及险种 (可选)</label>
            <div className="chip-row">
              {['重疾险', '医疗险', '意外险', '寿险', '年金险'].map((p) => (
                <button key={p} className={'chip' + (products.includes(p) ? ' is-active' : '')}
                  onClick={() => toggle(products, setProducts, p)}>{p}</button>
              ))}
            </div>
          </div>

          {error && <div className="kb-error">{error}</div>}

          <div className="step-actions">
            <button className="btn-primary"
              disabled={!url.trim() || !requirements.trim() || rewriting}
              onClick={handleRewrite}>
              {rewriting ? '改写中…' : '开始改写 (10 积分)'}
            </button>
          </div>
        </section>
      )}

      {step === 2 && result && (
        <section className="rewrite-card">
          <div className="result-head">
            <h3>改写结果</h3>
            <div className="result-actions">
              <button className="btn-ghost" onClick={() => setStep(1)}>返回上一步</button>
              <button className="btn-ghost" onClick={() => copyToClipboard(stripMarkdown(result?.rewritten?.content || ''))}>复制纯文本</button>
              <button className="btn-primary" onClick={() => setStep(3)}>下一步: 配图</button>
            </div>
          </div>

          <div className="rewrite-grid">
            <div className="rewrite-col">
              <div className="col-head">原文 (节选)</div>
              <div className="rewrite-content">
                {(result?.original?.content || '').slice(0, 1500)}
              </div>
            </div>
            <div className="rewrite-col">
              <div className="col-head">改写后</div>
              <div className="rewrite-content">
                <ReactMarkdown remarkPlugins={[remarkGfm]}>
                  {result?.rewritten?.content || ''}
                </ReactMarkdown>
              </div>
            </div>
          </div>
        </section>
      )}

      {step === 3 && (
        <section className="rewrite-card">
          <div className="result-head">
            <h3>配图 (4 张)</h3>
            <button className="btn-ghost" onClick={() => setStep(2)}>← 返回改写结果</button>
          </div>
          <div className="image-grid">
            {[1, 2, 3, 4].map((i) => (
              <div key={i} className="image-placeholder">
                <div className="img-num">{i}</div>
                <div className="img-tip">配图待生成</div>
              </div>
            ))}
          </div>
          <p className="kb-empty" style={{ padding: 20, fontSize: 12 }}>
            ⚠️ 公众号配图功能尚未对接, 后续会接 /api/agents/image 生成
          </p>
        </section>
      )}
    </div>
  )
}
