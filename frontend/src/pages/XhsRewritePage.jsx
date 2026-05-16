import { useState, useEffect } from 'react'
import { extractAutoNote, rewriteXhsNote, generateXhsBatchImages } from '../api'
import { proxyImageUrl } from '../utils/imageProxy'
import ConfirmModal, { useConfirmModal } from '../components/ConfirmModal'
import './XhsRewritePage.css'

const RATIO_OPTIONS = [
  { value: '3:4', label: '竖版 3:4' },
  { value: '1:1', label: '方形 1:1' },
]

const XHS_MODES = [
  { id: 'copy', label: '复制原文案' },
  { id: 'synonym', label: '同义改写' },
  { id: 'rewrite', label: '深度改写' },
  { id: 'mp_style', label: '转公众号风格' },
]

function extractUrlFromText(text) {
  if (!text) return ''
  const m = text.match(/https?:\/\/[^\s<>"{}|\\^`\[\]]+/)
  return m ? m[0] : ''
}

export default function XhsRewritePage({
  contentPrefill, onContentPrefillConsumed,
  onNavigate, onNavigateWithContentPrefill,
}) {
  const ST = {
    step: 1,
    url: '',
    extracting: false,
    extractedNote: null,
    mode: '',
    requirements: '',
    audience: [],
    products: [],
    rewriting: false,
    result: '',
    viewMode: 'preview',
    imgCount: 3,
    imgRatio: '3:4',
    imgLoading: false,
    imgResults: [],
    imgError: '',
    lightboxUrl: null,
    error: '',
  }

  const [step, setStep] = useState(ST.step)
  const [url, setUrl] = useState(ST.url)
  const [extracting, setExtracting] = useState(ST.extracting)
  const [extractedNote, setExtractedNote] = useState(ST.extractedNote)
  const [mode, setMode] = useState(ST.mode)
  const [requirements, setRequirements] = useState(ST.requirements)
  const [audience, setAudience] = useState(ST.audience)
  const [products, setProducts] = useState(ST.products)
  const [rewriting, setRewriting] = useState(ST.rewriting)
  const [result, setResult] = useState(ST.result)
  const [viewMode, setViewMode] = useState(ST.viewMode)
  const [imgCount, setImgCount] = useState(ST.imgCount)
  const [imgRatio, setImgRatio] = useState(ST.imgRatio)
  const [imgLoading, setImgLoading] = useState(ST.imgLoading)
  const [imgResults, setImgResults] = useState(ST.imgResults)
  const [imgError, setImgError] = useState(ST.imgError)
  const [lightboxUrl, setLightboxUrl] = useState(ST.lightboxUrl)
  const [error, setError] = useState(ST.error)
  const { confirm, props: confirmProps } = useConfirmModal()

  const goStep = (n) => {
    if (n < 1 || n > 3 || n === step) return
    const doGo = () => setStep(n)
    if (n < step) {
      const willLose = []
      if (n < 2 && step >= 2 && result) willLose.push('仿写结果')
      if (n < 3 && step >= 3 && imgResults.length > 0) willLose.push('已生成的配图')
      if (willLose.length > 0) {
        confirm({
          title: '返回会丢失内容',
          message: `当前 ${willLose.join('、')} 将会丢失，且不可恢复。确定要返回吗？`,
          confirmText: '确认返回',
          onConfirm: doGo,
        })
        return
      }
    }
    doGo()
  }

  const resetState = () => {
    setStep(ST.step); setUrl(ST.url); setExtracting(ST.extracting)
    setExtractedNote(ST.extractedNote); setMode(ST.mode)
    setRequirements(ST.requirements); setAudience(ST.audience); setProducts(ST.products)
    setRewriting(ST.rewriting); setResult(ST.result); setViewMode(ST.viewMode)
    setImgCount(ST.imgCount); setImgRatio(ST.imgRatio)
    setImgLoading(ST.imgLoading); setImgResults(ST.imgResults); setImgError(ST.imgError)
    setLightboxUrl(ST.lightboxUrl); setError(ST.error)
  }

  useEffect(() => {
    if (contentPrefill) {
      if (contentPrefill.step != null) setStep(contentPrefill.step)
      if (contentPrefill.url) setUrl(contentPrefill.url)
      if (contentPrefill.extractedNote) setExtractedNote(contentPrefill.extractedNote)
      if (contentPrefill.mode) setMode(contentPrefill.mode)
      if (contentPrefill.requirements) setRequirements(contentPrefill.requirements)
      if (contentPrefill.result) { setResult(contentPrefill.result); if (contentPrefill.step == null) setStep(2) }
      if (contentPrefill.viewMode) setViewMode(contentPrefill.viewMode)
      onContentPrefillConsumed?.()
    }
  }, [contentPrefill])

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
      const data = await extractAutoNote(url.trim())
      setExtractedNote(data?.note || data)
    } catch (e) {
      setError(e.message)
    } finally {
      setExtracting(false)
    }
  }

  const toggle = (arr, setArr, v) =>
    setArr(arr.includes(v) ? arr.filter(x => x !== v) : [...arr, v])

  const buildRequirements = () => {
    let parts = [requirements.trim()]
    if (audience.length) parts.push('目标人群: ' + audience.join('、'))
    if (products.length) parts.push('涉及险种: ' + products.join('、'))
    return parts.filter(Boolean).join('\n')
  }

  const handleRewrite = async () => {
    if (rewriting) return
    setError('')
    setRewriting(true)
    try {
      const res = await rewriteXhsNote({
        originalContent: extractedNote?.content || extractedNote?.title || '',
        mode,
        requirements: buildRequirements(),
        model: 'chat',
      })
      setResult(res.content || res.rewrittenContent || '')
      setStep(2)
    } catch (e) {
      setError(e.message)
    } finally {
      setRewriting(false)
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

  const handleDownload = async (imageUrl) => {
    try {
      const res = await fetch(imageUrl)
      const blob = await res.blob()
      const a = document.createElement('a')
      a.href = URL.createObjectURL(blob)
      a.download = '配图.png'
      a.click()
      URL.revokeObjectURL(a.href)
    } catch {}
  }

  return (
    <div className="xhs-rewrite-page">
      {/* Header + Steps */}
      <div className="page-head">
        <div className="page-title-row">
          <div className="page-title">
            <span className="platform-tag xhs">仿</span>
            <h2>小红书仿写</h2>
          </div>
          <button className="btn-ghost reset-btn" onClick={resetState}>重新开始</button>
        </div>
        <div className="steps">
          {[1, 2, 3].map(s => (
            <div key={s} className={`step ${step > s ? 'done' : ''} ${step === s ? 'active' : ''}`}
              onClick={() => step >= s && goStep(s)}>
              <div className="dot">{step > s ? '✓' : s}</div>
              <div className="step-label">{s === 1 ? '提供原文与仿写要求' : s === 2 ? '仿写结果' : '配图'}</div>
            </div>
          ))}
        </div>
      </div>

      {/* ═══════ STEP 1 ═══════ */}
      {step === 1 && (
        <div>
          {/* Card 1: 链接抓取 */}
          <div className="xhs-section-card">
            <div className="xhs-sc-head">
              <h3><span className="xhs-num-tag">1</span>粘贴笔记链接</h3>
              <span className="xhs-desc">AI 会读完原文的标题、正文与配图</span>
            </div>
            <div className="xhs-sc-body">
              <div className="xhs-link-row">
                <input className="xhs-text-field" value={url}
                  placeholder="粘贴 xhslink.cn 或 mp.weixin.qq.com 链接（支持粘贴带文字的分享内容）"
                  onChange={handleUrlChange} />
                <button className={`xhs-btn-fetch ${extractedNote ? 'xhs-success' : ''}`}
                  onClick={handleExtract} disabled={!url.trim() || extracting}>
                  {extracting ? '提取中…' : extractedNote ? '已抓取' : '抓取原文'}
                </button>
              </div>

              {extractedNote && (
                <>
                  <div className="xhs-fetch-status">
                    <div className="xhs-check-circle">✓</div>
                    <span>原文抓取成功 · 标题、{extractedNote.content?.length || 0} 字正文、封面已识别</span>
                  </div>

                  <div className="xhs-source-preview">
                    <div className="xhs-source-preview-head">
                      <span className="xhs-sp-label">— Source · 原文</span>
                      <span className="xhs-sp-stats">
                        <span>📝 <b>{extractedNote.content?.length || 0} 字</b></span>
                        {extractedNote.imageUrls?.length > 0 && <span>🖼 <b>{extractedNote.imageUrls.length} 张图</b></span>}
                      </span>
                    </div>

                    <div className="xhs-source-body-row">
                      {/* 左侧：所有图片 */}
                      {extractedNote.imageUrls?.length > 0 && (
                        <div className="xhs-source-images-col">
                          {extractedNote.imageUrls.map((imgUrl, i) => (
                            <div key={i} className="xhs-source-img-thumb"
                              onClick={() => setLightboxUrl(imgUrl)}>
                              <img src={proxyImageUrl(imgUrl)} alt={`图片${i + 1}`}
                                onError={e => { e.currentTarget.style.display = 'none' }} />
                            </div>
                          ))}
                        </div>
                      )}

                      {/* 右侧：标题 + 正文 */}
                      <div className="xhs-source-info">
                        <div className="xhs-source-title">{extractedNote.title}</div>
                        {extractedNote.content && (
                          <div className="xhs-source-body">
                            {extractedNote.content}
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                </>
              )}
            </div>
          </div>

          {/* Card 2: 仿写要求 */}
          <div className="xhs-section-card">
            <div className="xhs-sc-head">
              <h3><span className="xhs-num-tag">2</span>告诉 AI 你想怎么改</h3>
              <span className="xhs-desc">写得越具体，仿写结果越贴近你想要的</span>
            </div>
            <div className="xhs-sc-body">
              <div className="xhs-form-row">
                <div className="xhs-label">仿写方向 <span className="xhs-req">*</span></div>
                <div>
                  <textarea className="xhs-text-field" style={{ minHeight: 120 }}
                    placeholder="（必填）告诉 AI 你想怎么改，例如：把语气更年轻化，强化数据点，删掉口播感"
                    value={requirements} onChange={e => setRequirements(e.target.value)} />
                </div>
              </div>

              <div className="xhs-form-row">
                <div className="xhs-label">改写模式</div>
                <div>
                  <div className="xhs-chip-group">
                    {XHS_MODES.map(m => (
                      <span key={m.id} className={`xhs-chip ${mode === m.id ? 'xhs-selected' : ''}`}
                        onClick={() => setMode(m.id)}>{m.label}</span>
                    ))}
                  </div>
                </div>
              </div>

              <div className="xhs-form-row">
                <div className="xhs-label">目标人群</div>
                <div>
                  <div className="xhs-chip-group">
                    {['中产', '宝妈', '高净值', '银发族', '单身青年', '创业者'].map(a => (
                      <span key={a} className={`xhs-chip ${audience.includes(a) ? 'xhs-selected' : ''}`}
                        onClick={() => toggle(audience, setAudience, a)}>{a}</span>
                    ))}
                  </div>
                </div>
              </div>

              <div className="xhs-form-row">
                <div className="xhs-label">险种</div>
                <div>
                  <div className="xhs-chip-group">
                    {['医疗险', '重疾险', '寿险', '意外险', '年金险'].map(p => (
                      <span key={p} className={`xhs-chip ${products.includes(p) ? 'xhs-selected' : ''}`}
                        onClick={() => toggle(products, setProducts, p)}>{p}</span>
                    ))}
                  </div>
                </div>
              </div>

              {error && <div className="xhs-error">{error}</div>}
            </div>
          </div>
        </div>
      )}

      {/* ═══════ STEP 2 ═══════ */}
      {step === 2 && result && (
        <div>
          {/* 摘要条 */}
          <div className="xhs-rewrite-summary">
            <div className="xhs-rs-left">
              <div className="xhs-rs-ico">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>
              </div>
              <div className="xhs-rs-info">
                <div>仿写完成 · <b>{result.length} 字</b>小红书文案{audience.length > 0 ? ` · 面向 <b>${audience.join('、')}</b>` : ''}</div>
                <div className="xhs-rs-meta">
                  {mode === 'copy' ? '复制原文案' : mode === 'synonym' ? '同义改写' : mode === 'rewrite' ? '深度改写' : mode === 'mp_style' ? '转公众号风格' : '自定义'} · {imgCount} 张配图待生成
                </div>
              </div>
            </div>
            <button className="xhs-btn-tool" onClick={() => goStep(1)}>
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"><polyline points="15 18 9 12 15 6"/></svg>
              <span>返回修改</span>
            </button>
          </div>

          {/* 正文 */}
          <div className="xhs-section-card">
            <div className="xhs-sc-head">
              <h3>仿写后的正文</h3>
              <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                <div className="view-toggle">
                  <span className={`toggle-btn ${viewMode === 'preview' ? 'active' : ''}`} onClick={() => setViewMode('preview')}>预览</span>
                  <span className={`toggle-btn ${viewMode === 'edit' ? 'active' : ''}`} onClick={() => setViewMode('edit')}>编辑</span>
                </div>
                <span className="xhs-desc">{result.length} 字</span>
              </div>
            </div>
            <div className="xhs-sc-body">
              {viewMode === 'edit' ? (
                <textarea className="body-editor-textarea" value={result}
                  onChange={e => setResult(e.target.value)} placeholder="在这里编辑内容..." />
              ) : (
                <div className="xhs-body-editor" contentEditable suppressContentEditableWarning
                  onBlur={e => setResult(e.currentTarget.textContent || '')}
                  dangerouslySetInnerHTML={{ __html: result.replace(/\n/g, '<br/>') }} />
              )}
            </div>
          </div>

          {/* 完成区 + 配图入口 */}
          <div style={{ display: 'flex', gap: 10, marginBottom: 16 }}>
            <button className="xhs-btn-primary-large" onClick={() => setStep(3)}>
              <span>下一步：配图</span>
              <span className="xhs-arr">→</span>
            </button>
          </div>
        </div>
      )}

      {/* ═══════ STEP 3 ═══════ */}
      {step === 3 && (
        <div>
          {/* 返回按钮 */}
          <div style={{ marginBottom: 16 }}>
            <button className="xhs-btn-back" onClick={() => goStep(2)}>← 回到仿写结果</button>
          </div>

          {/* 配图面板 */}
          <div className="xhs-section-card">
            <div className="xhs-sc-head">
              <h3>生成配图</h3>
              <span className="xhs-desc">AI 自动拆解文章，为每个核心段落生成配图</span>
            </div>
            <div className="xhs-sc-body">
              {imgResults.length > 0 ? (
                <div className="batch-image-grid" style={{ marginTop: 0 }}>
                  {imgResults.map((item) => (
                    <div key={item.index} className="batch-image-item">
                      {item.imageUrl ? (
                        <div className="batch-thumb-wrap">
                          <img src={item.imageUrl} alt={`配图${item.index}`} className="batch-thumb" />
                          <div className="image-hover-info">
                            <div className="hover-actions">
                              <button className="ov-btn" onClick={() => setLightboxUrl(item.imageUrl)}>
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7"/></svg>
                                放大
                              </button>
                              <button className="ov-btn" onClick={() => handleDownload(item.imageUrl)}>
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                                下载
                              </button>
                            </div>
                          </div>
                        </div>
                      ) : (
                        <div className="batch-thumb batch-thumb-error">
                          <span>生成失败</span>
                          {item.error && <small>{item.error}</small>}
                        </div>
                      )}
                      {item.description && <p className="batch-image-desc">{item.description}</p>}
                    </div>
                  ))}
                </div>
              ) : imgLoading ? (
                <div className="batch-image-grid">
                  {Array.from({ length: imgCount }).map((_, i) => (
                    <div key={i} className="xhs-skeleton" style={{ aspectRatio: imgRatio === '1:1' ? '1 / 1' : '3 / 4' }} />
                  ))}
                </div>
              ) : (
                <div>
                  <div className="xhs-form-row">
                    <div className="xhs-label">张数</div>
                    <div>
                      <div className="xhs-chip-group">
                        {[1, 3, 6, 9].map(n => (
                          <span key={n} className={`xhs-chip ${imgCount === n ? 'xhs-selected' : ''}`}
                            onClick={() => setImgCount(n)}>{n} 张</span>
                        ))}
                      </div>
                    </div>
                  </div>
                  <div className="xhs-form-row">
                    <div className="xhs-label">比例</div>
                    <div>
                      <div className="xhs-chip-group">
                        {RATIO_OPTIONS.map(o => (
                          <span key={o.value} className={`xhs-chip ${imgRatio === o.value ? 'xhs-selected' : ''}`}
                            onClick={() => setImgRatio(o.value)}>{o.label}</span>
                        ))}
                      </div>
                    </div>
                  </div>
                  <button className="xhs-btn-primary-large" onClick={handleGenerateImages}
                    disabled={imgLoading || !result.trim()} style={{ width: '100%', marginTop: 12 }}>
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
                    <span>AI 智能拆解，生成 {imgCount} 张</span>
                  </button>
                </div>
              )}
            </div>
          </div>

          {imgError && <div className="xhs-error">{imgError}</div>}

          {/* 完成区 */}
          {imgResults.length > 0 && (
            <div className="xhs-done-section">
              <div className="xhs-done-left">
                <div className="xhs-done-icon">✓</div>
                <div>
                  <h4>仿写完成</h4>
                  <div className="xhs-done-desc">{result.length} 字小红书文案 · {imgResults.filter(i => i.imageUrl).length} 张配图</div>
                </div>
              </div>
              <div className="xhs-done-right">
                <button className="xhs-btn-done" onClick={() => navigator.clipboard?.writeText(result)}>
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                  <span>复制全文</span>
                </button>
                <button className="xhs-btn-done" onClick={async () => {
                  for (const img of imgResults) {
                    if (!img.imageUrl) continue
                    await handleDownload(img.imageUrl)
                    await new Promise(r => setTimeout(r, 300))
                  }
                }}>
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                  <span>下载全部图片</span>
                </button>
                <button className="xhs-btn-done xhs-primary" onClick={() => navigator.clipboard?.writeText(result)}>
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/></svg>
                  <span>保存为草稿</span>
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ═══════ Lightbox ═══════ */}
      {lightboxUrl && (
        <div className="xhs-lightbox-overlay" onClick={() => setLightboxUrl(null)}>
          <div className="xhs-lightbox-box" onClick={e => e.stopPropagation()}>
            <img src={lightboxUrl} alt="预览" />
            <div className="xhs-lightbox-actions">
              <button className="xhs-lightbox-btn" onClick={() => handleDownload(lightboxUrl)}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                下载图片
              </button>
              <button className="xhs-lightbox-btn xhs-close" onClick={() => setLightboxUrl(null)}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                关闭
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ═══════ Actions Bar ═══════ */}
      <div className="xhs-actions-bar">
        <div className="xhs-ab-left">
          {step > 1 && (
            <button className="xhs-btn-back" onClick={() => goStep(step - 1)}>
              ← {step === 2 ? '返回修改' : '回到仿写结果'}
            </button>
          )}
        </div>
        <div className="xhs-ab-right">
          <span className="xhs-ab-meta">本步骤消耗 <b>{step === 1 ? 10 : 5}</b> 积分</span>
          {step === 1 && (
            <button className="xhs-btn-primary-large"
              disabled={!extractedNote || !requirements.trim() || rewriting}
              onClick={handleRewrite}>
              <span>{rewriting ? '仿写中…' : '开始仿写'}</span>
              <span className="xhs-arr">→</span>
            </button>
          )}
          {step === 2 && (
            <button className="xhs-btn-primary-large" onClick={() => setStep(3)}>
              <span>下一步：配图</span>
              <span className="xhs-arr">→</span>
            </button>
          )}
        </div>
      </div>

      <ConfirmModal {...confirmProps} />
    </div>
  )
}
