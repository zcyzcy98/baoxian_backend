import { useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { extractAutoNote, rewriteXhsNote, generateXhsBatchImages } from '../api'
import './XhsCreatePage.css'

const COUNT_OPTIONS = [1, 3, 6, 9]
const RATIO_OPTIONS = [
  { value: '3:4', label: '竖版 3:4' },
  { value: '1:1', label: '方形 1:1' },
]

// 从文本中提取链接
function extractUrlFromText(text) {
  if (!text) return ''
  // 匹配常见的 URL 模式
  const urlMatch = text.match(/https?:\/\/[^\s<>"{}|\\^`\[\]]+/)
  return urlMatch ? urlMatch[0] : ''
}

export default function XhsRewritePage() {
  const [step, setStep] = useState(1)
  const [url, setUrl] = useState('')
  const [loading, setLoading] = useState(false)
  const [extractedNote, setExtractedNote] = useState(null)
  const [rewriteMode, setRewriteMode] = useState('synonym')
  const [requirements, setRequirements] = useState('')
  const [result, setResult] = useState('')
  const [viewMode, setViewMode] = useState('preview') // 'edit' or 'preview'

  // 配图
  const [imgCount, setImgCount] = useState(3)
  const [imgRatio, setImgRatio] = useState('3:4')
  const [imgLoading, setImgLoading] = useState(false)
  const [imgResults, setImgResults] = useState([])
  const [imgError, setImgError] = useState('')

  const handleUrlChange = (e) => {
    const inputText = e.target.value
    const extractedUrl = extractUrlFromText(inputText)
    setUrl(extractedUrl || inputText)
  }

  const handleExtract = async () => {
    if (!url.trim()) return
    setLoading(true)
    try {
      const data = await extractAutoNote(url)
      setExtractedNote(data.note || data)
      setStep(2)
    } catch (e) {
      alert('提取失败: ' + e.message)
    } finally {
      setLoading(false)
    }
  }

  const handleRewrite = async () => {
    if (!extractedNote) return
    setLoading(true)
    try {
      const res = await rewriteXhsNote({
        originalContent: extractedNote.content || extractedNote.title,
        mode: rewriteMode,
        requirements,
        model: 'chat',
      })
      setResult(res.content || res.rewrittenContent || '')
      setStep(3)
    } catch (e) {
      alert('仿写失败: ' + e.message)
    } finally {
      setLoading(false)
    }
  }

  const handleGenerateImages = async () => {
    if (!result.trim() || imgLoading) return
    setImgLoading(true)
    setImgError('')
    setImgResults([])
    try {
      const data = await generateXhsBatchImages(result, imgCount, imgRatio, 'hiapi')
      setImgResults(data)
    } catch (e) {
      setImgError(e.message)
    } finally {
      setImgLoading(false)
    }
  }

  const modes = [
    { id: 'copy', label: '复制原文案' },
    { id: 'synonym', label: '同义改写' },
    { id: 'rewrite', label: '深度改写' },
    { id: 'mp_style', label: '转公众号风格' },
  ]

  return (
    <div className="xhs-create-page">
      <div className="page-head">
        <div className="page-title-row">
          <div className="page-title">
            <span className="platform-tag xhs">仿</span>
            <h2>小红书仿写</h2>
          </div>
        </div>
        <div className="steps">
          <div className={`step ${step > 1 ? 'done' : ''} ${step === 1 ? 'active' : ''}`}>
            <div className="dot">{step > 1 ? '✓' : '1'}</div>
            <div className="step-label">提取原文</div>
          </div>
          <div className={`step ${step > 2 ? 'done' : ''} ${step === 2 ? 'active' : ''}`}>
            <div className="dot">{step > 2 ? '✓' : '2'}</div>
            <div className="step-label">选择模式</div>
          </div>
          <div className={`step ${step === 3 ? 'active' : ''}`}>
            <div className="dot">3</div>
            <div className="step-label">查看结果</div>
          </div>
        </div>
      </div>

      {step === 1 && (
        <div className="step-pane">
          <div className="section-card">
            <div className="sc-head">
              <h3>粘贴链接，AI 自动提取内容</h3>
              <span className="desc">支持小红书 / 公众号链接</span>
            </div>
            <div className="sc-body">
              <div className="form-row">
                <div className="label">笔记链接 <span className="req">*</span></div>
                <div className="control">
                  <input type="text" className="text-field" value={url} onChange={handleUrlChange} placeholder="粘贴 xhslink.cn 或 mp.weixin.qq.com 链接（支持粘贴带文字的分享内容）" />
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {step === 2 && (
        <div className="step-pane">
          {extractedNote && (
            <div className="section-card" style={{ marginBottom: 16 }}>
              <div className="sc-head"><h3>已提取内容</h3></div>
              <div className="sc-body">
                <h4 style={{ marginBottom: 8 }}>{extractedNote.title}</h4>
                <p style={{ fontSize: 13, color: 'var(--ink-3)', whiteSpace: 'pre-wrap', maxHeight: 200, overflow: 'auto' }}>{extractedNote.content?.slice(0, 500)}</p>
                {extractedNote.imageUrls?.length > 0 && (
                  <div className="extracted-images">
                    <div className="extracted-images-label">配图 ({extractedNote.imageUrls.length} 张)</div>
                    <div className="extracted-images-row">
                      {extractedNote.imageUrls.map((url, i) => (
                        <a key={i} href={url} target="_blank" rel="noreferrer" className="extracted-img-link">
                          <img src={url} alt={`图片${i + 1}`} className="extracted-thumb"
                            onError={(e) => { e.currentTarget.style.display = 'none' }} />
                        </a>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </div>
          )}
          <div className="section-card">
            <div className="sc-head"><h3>选择仿写模式</h3></div>
            <div className="sc-body">
              <div className="form-row">
                <div className="label">改写模式</div>
                <div className="control">
                  <div className="chip-group">
                    {modes.map(m => (
                      <span key={m.id} className={`chip ${rewriteMode === m.id ? 'selected' : ''}`} onClick={() => setRewriteMode(m.id)}>{m.label}</span>
                    ))}
                  </div>
                </div>
              </div>
              <div className="form-row">
                <div className="label">额外要求</div>
                <div className="control">
                  <textarea className="text-field" value={requirements} onChange={e => setRequirements(e.target.value)} placeholder="（选填）补充特殊要求" />
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {step === 3 && (
        <div className="step-pane">
          <div className="section-card">
            <div className="sc-head">
              <h3>仿写结果</h3>
              <div className="view-toggle">
                <span className={`toggle-btn ${viewMode === 'preview' ? 'active' : ''}`} onClick={() => setViewMode('preview')}>预览</span>
                <span className={`toggle-btn ${viewMode === 'edit' ? 'active' : ''}`} onClick={() => setViewMode('edit')}>编辑</span>
              </div>
            </div>
            <div className="sc-body">
              {viewMode === 'edit' ? (
                <textarea 
                  className="body-editor-textarea" 
                  value={result}
                  onChange={e => setResult(e.target.value)}
                  placeholder="在这里编辑内容..."
                />
              ) : (
                <div className="body-editor markdown-body">
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>
                    {result}
                  </ReactMarkdown>
                </div>
              )}
            </div>
          </div>
          <div className="done-section">
            <div className="l">
              <div className="done-icon">✓</div>
              <div><h4>仿写完成</h4><div className="desc">{result.length} 字</div></div>
            </div>
            <div className="r">
              <button className="btn-done" onClick={() => navigator.clipboard?.writeText(result)}>复制</button>
            </div>
          </div>

          {/* 配图面板 */}
          <div className="section-card" style={{ marginTop: 16 }}>
            <div className="sc-head">
              <h3>生成配图</h3>
              <span className="desc">AI 自动拆解文章，为每个核心段落生成配图</span>
            </div>
            <div className="sc-body">
              <div className="form-row">
                <div className="label">张数</div>
                <div className="control">
                  <div className="chip-group">
                    {COUNT_OPTIONS.map((n) => (
                      <span key={n}
                        className={`chip ${imgCount === n ? 'selected' : ''}`}
                        onClick={() => setImgCount(n)}>
                        {n} 张
                      </span>
                    ))}
                  </div>
                </div>
              </div>
              <div className="form-row">
                <div className="label">比例</div>
                <div className="control">
                  <div className="chip-group">
                    {RATIO_OPTIONS.map((opt) => (
                      <span key={opt.value}
                        className={`chip ${imgRatio === opt.value ? 'selected' : ''}`}
                        onClick={() => setImgRatio(opt.value)}>
                        {opt.label}
                      </span>
                    ))}
                  </div>
                </div>
              </div>

              {imgError && <div className="kb-error" style={{ marginTop: 8 }}>{imgError}</div>}

              <button
                className="btn-primary-large"
                style={{ marginTop: 12 }}
                onClick={handleGenerateImages}
                disabled={imgLoading || !result.trim()}>
                {imgLoading
                  ? `生成中… (并行生成 ${imgCount} 张，约 30 秒)`
                  : `生成 ${imgCount} 张配图`}
              </button>

              {imgResults.length > 0 && (
                <div className="batch-image-grid" style={{ marginTop: 16 }}>
                  {imgResults.map((item) => (
                    <div key={item.index} className="batch-image-item">
                      {item.imageUrl ? (
                        <a href={item.imageUrl} target="_blank" rel="noreferrer">
                          <img src={item.imageUrl} alt={`配图${item.index}`} className="batch-thumb" />
                        </a>
                      ) : (
                        <div className="batch-thumb batch-thumb-error">
                          <span>生成失败</span>
                          {item.error && <small>{item.error}</small>}
                        </div>
                      )}
                      {item.description && (
                        <p className="batch-image-desc">{item.description}</p>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      <div className="actions-bar">
        <div className="l">
          {step > 1 && <button className="btn-back" onClick={() => setStep(step - 1)}>← 返回</button>}
        </div>
        <div className="r">
          {step === 1 && (
            <button className="btn-primary-large" onClick={handleExtract} disabled={loading || !url.trim()}>
              <span>{loading ? '提取中…' : '提取内容'}</span><span className="arr">→</span>
            </button>
          )}
          {step === 2 && (
            <button className="btn-primary-large" onClick={handleRewrite} disabled={loading}>
              <span>{loading ? '仿写中…' : '开始仿写'}</span><span className="arr">→</span>
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
