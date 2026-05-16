import { useState, useEffect } from 'react'
import {
  rewriteWechat,
  extractAutoNote,
  generateGzhImage,
  generateGzhBatchImages,
  regenGzhOneImage,
  fetchStyleProfile,
} from '../api'
import { copyToClipboard, stripMarkdown } from '../utils/markdown'
import { proxyImageUrl } from '../utils/imageProxy'
import ConfirmModal, { useConfirmModal } from '../components/ConfirmModal'
import './XhsCreatePage.css'
import './GzhRewritePage.css'

/* ── helpers ──────────────────────────────────────────────── */
function extractUrlFromText(text) {
  if (!text) return ''
  const m = text.match(/https?:\/\/[^\s<>"{}|\\^`\[\]]+/)
  return m ? m[0] : ''
}

const REWRITE_MODES = [
  { id: 'mp_rewrite', label: '深度改写' },
  { id: 'rewrite',    label: '常规改写' },
  { id: 'synonym',    label: '同义改写' },
]

const WORD_COUNT_OPTIONS = ['1500', '2500', '3000']

const GZH_RATIO_OPTIONS = [
  { value: '21:9',  label: '影院超宽 21:9' },
  { value: '16:9',  label: '横版 16:9' },
  { value: '4:3',   label: '横版 4:3' },
  { value: '1:1',   label: '方形 1:1' },
  { value: '3:4',   label: '竖版 3:4' },
]

const TITLE_STYLE_LABELS = ['深度型', '案例型', '反问型', '数据型', '故事型']

function ratioAspectStyle(ratio) {
  if (!ratio) return { aspectRatio: '2.35 / 1' }
  const [w, h] = ratio.split(':').map(Number)
  return { aspectRatio: `${w} / ${h}` }
}

/* ── Component ────────────────────────────────────────────── */
export default function GzhRewritePage({
  topicPrefill, onPrefillConsumed,
  contentPrefill, onContentPrefillConsumed,
  onNavigate, onNavigateWithContentPrefill,
}) {
  const ST = {
    step: 1,
    url: '',
    extracted: null,
    extracting: false,
    requirements: '',
    useStyle: false,
    styleProfile: null,
    audience: [],
    products: [],
    wordCount: '',
    customWordCount: '',
    imageMode: '',
    mode: '',
    rewriting: false,
    result: null,
    error: '',
    /* step 2 state */
    selectedTitle: 0,
    editedBody: '',
    /* step 3 state */
    imgMode: null,
    imgRatio: '2.35:1',
    imgCount: 4,
    coverImage: null,
    batchImages: [],
    imageLoading: false,
    batchImgLoading: false,
    imageError: '',
    regenIndex: -1,
    lightboxUrl: null,
  }

  const [step, setStep] = useState(ST.step)
  const [url, setUrl] = useState(ST.url)
  const [extracted, setExtracted] = useState(ST.extracted)
  const [extracting, setExtracting] = useState(ST.extracting)
  const [requirements, setRequirements] = useState(ST.requirements)
  const [useStyle, setUseStyle] = useState(ST.useStyle)
  const [styleProfile, setStyleProfile] = useState(ST.styleProfile)
  const [audience, setAudience] = useState(ST.audience)
  const [products, setProducts] = useState(ST.products)
  const [wordCount, setWordCount] = useState(ST.wordCount)
  const [customWordCount, setCustomWordCount] = useState(ST.customWordCount)
  const [imageMode, setImageMode] = useState(ST.imageMode)
  const [mode, setMode] = useState(ST.mode)
  const [rewriting, setRewriting] = useState(ST.rewriting)
  const [result, setResult] = useState(ST.result)
  const [error, setError] = useState(ST.error)
  const [selectedTitle, setSelectedTitle] = useState(ST.selectedTitle)
  const [editedBody, setEditedBody] = useState(ST.editedBody)
  const [imgMode, setImgMode] = useState(ST.imgMode)
  const [imgRatio, setImgRatio] = useState(ST.imgRatio)
  const [imgCount, setImgCount] = useState(ST.imgCount)
  const [coverImage, setCoverImage] = useState(ST.coverImage)
  const [batchImages, setBatchImages] = useState(ST.batchImages)
  const [imageLoading, setImageLoading] = useState(ST.imageLoading)
  const [batchImgLoading, setBatchImgLoading] = useState(ST.batchImgLoading)
  const [imageError, setImageError] = useState(ST.imageError)
  const [regenIndex, setRegenIndex] = useState(ST.regenIndex)
  const [lightboxUrl, setLightboxUrl] = useState(ST.lightboxUrl)
  const { confirm, props: confirmProps } = useConfirmModal()

  const goStep = (n) => {
    if (n < 1 || n > 3 || n === step) return
    const doGo = () => setStep(n)
    // Step 3 → 1/2：有配图结果时确认
    if (step === 3 && n < 3 && (coverImage || (batchImages && batchImages.length > 0))) {
      confirm({
        title: '返回会丢失配图',
        message: '当前生成的配图结果将会丢失，且不可恢复。确定要返回吗？',
        confirmText: '确认返回',
        onConfirm: doGo,
      })
      return
    }
    // Step 2 → 1：有改写结果时确认
    if (step === 2 && n === 1 && result) {
      confirm({
        title: '返回会丢失改写结果',
        message: '当前生成的改写结果将会丢失，需要重新仿写。确定要返回吗？',
        confirmText: '确认返回',
        onConfirm: doGo,
      })
      return
    }
    doGo()
  }

  const resetState = () => {
    setStep(ST.step); setUrl(ST.url); setExtracted(ST.extracted)
    setExtracting(ST.extracting); setRequirements(ST.requirements)
    setUseStyle(ST.useStyle); setAudience(ST.audience); setProducts(ST.products)
    setWordCount(ST.wordCount); setCustomWordCount(ST.customWordCount); setImageMode(ST.imageMode); setMode(ST.mode)
    setRewriting(ST.rewriting); setResult(ST.result); setError(ST.error)
    setSelectedTitle(ST.selectedTitle); setEditedBody(ST.editedBody)
    setImgMode(ST.imgMode); setImgRatio(ST.imgRatio); setImgCount(ST.imgCount)
    setCoverImage(ST.coverImage); setBatchImages(ST.batchImages)
    setImageLoading(ST.imageLoading); setBatchImgLoading(ST.batchImgLoading)
    setImageError(ST.imageError); setRegenIndex(ST.regenIndex)
    setLightboxUrl(ST.lightboxUrl)
  }

  /* ── prefill ──────────────────────────────────────────── */
  useEffect(() => {
    if (topicPrefill) {
      setRequirements(topicPrefill.topic || '')
      onPrefillConsumed?.()
    }
  }, [topicPrefill])

  useEffect(() => {
    if (contentPrefill) {
      if (contentPrefill.step != null) setStep(contentPrefill.step)
      if (contentPrefill.url) setUrl(contentPrefill.url)
      if (contentPrefill.extracted) setExtracted(contentPrefill.extracted)
      if (contentPrefill.requirements) setRequirements(contentPrefill.requirements)
      if (contentPrefill.useStyle != null) setUseStyle(contentPrefill.useStyle)
      if (contentPrefill.audience) setAudience(contentPrefill.audience)
      if (contentPrefill.products) setProducts(contentPrefill.products)
      if (contentPrefill.mode) setMode(contentPrefill.mode)
      if (contentPrefill.result) {
        setResult(contentPrefill.result)
        setEditedBody(contentPrefill.result?.rewritten?.content || '')
        if (contentPrefill.step == null) setStep(2)
      }
      onContentPrefillConsumed?.()
    }
  }, [contentPrefill])

  /* ── load style profile ──────────────────────────────── */
  useEffect(() => {
    fetchStyleProfile().then(p => { if (p) setStyleProfile(p) }).catch(() => {})
  }, [])

  /* ── handlers ────────────────────────────────────────── */
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

  const buildRequirements = () => {
    let parts = [requirements.trim()]
    if (useStyle && styleProfile) parts.push('请套用我训练好的个人风格')
    if (audience.length) parts.push('目标人群: ' + audience.join('、'))
    if (products.length) parts.push('涉及险种: ' + products.join('、'))
    const wc = wordCount === 'custom' ? customWordCount : wordCount
    if (wc) parts.push('目标字数: ' + wc + ' 字')
    return parts.filter(Boolean).join('\n')
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
      setSelectedTitle(0)
      setEditedBody(r?.rewritten?.content || '')
      setStep(2)
    } catch (err) {
      setError(err.message)
    } finally {
      setRewriting(false)
    }
  }

  const toggle = (arr, setArr, v) =>
    setArr(arr.includes(v) ? arr.filter(x => x !== v) : [...arr, v])

  /* ── image handlers ──────────────────────────────────── */
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

  const handleGenerateCover = async () => {
    setImageLoading(true)
    setImageError('')
    try {
      const selectedTitleText = (result?.rewritten?.titles?.[selectedTitle] || '').replace(/^\d+[.、]\s*/, '')
      const r = await generateGzhImage({
        topic: selectedTitleText || editedBody.slice(0, 100),
        imageRatio: '2.35:1',
        imageProvider: 'hiapi',
      })
      setCoverImage(r?.imageUrl || r?.url || '')
    } catch (err) {
      setImageError(err.message)
    } finally {
      setImageLoading(false)
    }
  }

  const handleBatchGenerate = async () => {
    setBatchImgLoading(true)
    setImageError('')
    try {
      const r = await generateGzhBatchImages(editedBody, imgCount, '2.35:1', 'hiapi')
      setBatchImages(r?.images || r || [])
    } catch (err) {
      setImageError(err.message)
    } finally {
      setBatchImgLoading(false)
    }
  }

  const handleRegenOne = async (index, description, isCover) => {
    setRegenIndex(index)
    try {
      const r = await regenGzhOneImage(editedBody, description, '2.35:1', 'hiapi', isCover)
      const newUrl = r?.imageUrl || r?.url || ''
      if (isCover) {
        setCoverImage(newUrl)
      } else {
        setBatchImages(prev => prev.map((item, i) => i === index ? { ...item, imageUrl: newUrl } : item))
      }
    } catch (err) {
      setImageError(err.message)
    } finally {
      setRegenIndex(-1)
    }
  }

  const titles = result?.rewritten?.titles || []
  const showCoverSection = imageMode !== 'none'
  const showBodyImages = imageMode === 'full'

  /* ── render ───────────────────────────────────────────── */
  return (
    <div className="gz-rewrite-page">
      {/* Header + Steps card */}
      <div className="page-head">
        <div className="page-title-row">
          <div className="page-title">
            <span className="platform-tag gzh">公</span>
            <h2>公众号仿写</h2>
          </div>
          <button className="btn-ghost reset-btn" onClick={resetState}>重新开始</button>
        </div>
        <div className="steps">
        {[1, 2, 3].map(s => (
          <div key={s} className={`step ${step > s ? 'done' : ''} ${step === s ? 'active' : ''}`} onClick={() => step >= s && goStep(s)}>
            <div className="dot">{step > s ? '✓' : s}</div>
            <div className="step-label">{s === 1 ? '提供原文与改写要求' : s === 2 ? '改写结果' : '配图'}</div>
          </div>
        ))}
      </div>
      </div>

      {/* ═══════ STEP 1 ═══════ */}
      {step === 1 && (
        <div>
          {/* Card 1: 链接抓取 */}
          <div className="gz-section-card">
            <div className="gz-sc-head">
              <h3><span className="gz-num-tag">1</span>粘贴公众号文章链接</h3>
              <span className="gz-desc">AI 会读完原文的标题、正文、封面与配图</span>
            </div>
            <div className="gz-sc-body">
              <div className="gz-link-row">
                <input className="gz-text-field" value={url}
                  placeholder="粘贴 mp.weixin.qq.com/s 链接（支持粘贴带文字的分享内容）"
                  onChange={handleUrlChange} />
                <button className={`gz-btn-fetch ${extracted ? 'gz-success' : ''}`}
                  onClick={handleExtract} disabled={!url.trim() || extracting}>
                  {extracting ? '提取中…' : extracted ? '已抓取' : '抓取原文'}
                </button>
              </div>

              {extracted && (
                <>
                  <div className="gz-fetch-status">
                    <div className="gz-check-circle">✓</div>
                    <span>原文抓取成功 · 标题、{extracted.content?.length || 0} 字正文、封面已识别</span>
                  </div>

                  <div className="gz-source-preview">
                    <div className="gz-source-preview-head">
                      <span className="gz-sp-label">— Source · 原文</span>
                      <span className="gz-sp-stats">
                        <span>📝 <b>{extracted.content?.length || 0} 字</b></span>
                        {extracted.imageUrls?.length > 0 && <span>🖼 <b>{extracted.imageUrls.length} 张图</b></span>}
                      </span>
                    </div>

                    <div className="gz-source-body-row">
                      {/* 左侧：封面 + 正文图片，竖向滚动 */}
                      <div className="gz-source-images-col">
                        {extracted.cover && (
                          <div className="gz-source-img-thumb gz-source-cover-thumb"
                            onClick={() => setLightboxUrl(extracted.cover)}>
                            <img src={proxyImageUrl(extracted.cover)} alt="封面"
                              onError={e => { e.currentTarget.style.display = 'none' }} />
                          </div>
                        )}
                        {extracted.imageUrls?.map((imgUrl, i) => (
                          <div key={i} className="gz-source-img-thumb"
                            onClick={() => setLightboxUrl(imgUrl)}>
                            <img src={proxyImageUrl(imgUrl)} alt={`图片${i + 1}`}
                              onError={e => { e.currentTarget.style.display = 'none' }} />
                          </div>
                        ))}
                      </div>

                      {/* 右侧：标题 + 正文 */}
                      <div className="gz-source-info">
                        <div className="gz-source-title">{extracted.title}</div>
                        {extracted.content && (
                          <div className="gz-source-body">
                            {extracted.content}
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                </>
              )}
            </div>
          </div>

          {/* Card 2: 改写要求 */}
          <div className="gz-section-card">
            <div className="gz-sc-head">
              <h3><span className="gz-num-tag">2</span>告诉 AI 你想怎么改</h3>
              <span className="gz-desc">写得越具体，改写结果越贴近你想要的</span>
            </div>
            <div className="gz-sc-body">
              <div className="gz-form-row">
                <div className="gz-label">改写方向 <span className="gz-req">*</span></div>
                <div>
                  <textarea className="gz-text-field" style={{ minHeight: 120 }}
                    placeholder="（必填）告诉 AI 你想怎么改，例如：把语气更年轻化，强化数据点，删掉口播感"
                    value={requirements} onChange={e => setRequirements(e.target.value)} />
                </div>
              </div>

              <div className="gz-form-row">
                <div className="gz-label">配图</div>
                <div>
                  <div className="gz-chip-group">
                    {[
                      { v: 'none',  l: '不生成图片' },
                      { v: 'cover', l: '只生成封面' },
                      { v: 'full',  l: '生成封面 + 正文配图' },
                    ].map(o => (
                      <span key={o.v} className={`gz-chip ${imageMode === o.v ? 'gz-selected' : ''}`}
                        onClick={() => setImageMode(o.v)}>
                        {o.l}
                      </span>
                    ))}
                  </div>
                </div>
              </div>

              <div className="gz-form-row">
                <div className="gz-label">改写后字数 <span className="gz-req">*</span></div>
                <div>
                  <div className="gz-chip-group" style={{ alignItems: 'center' }}>
                    {WORD_COUNT_OPTIONS.map(w => (
                      <span key={w} className={`gz-chip ${wordCount === w ? 'gz-selected' : ''}`}
                        onClick={() => { setWordCount(w); setCustomWordCount('') }}>
                        {parseInt(w).toLocaleString()} 字
                      </span>
                    ))}
                    <span className={`gz-chip ${wordCount === 'custom' ? 'gz-selected' : ''}`}
                      onClick={() => setWordCount('custom')}>
                      自定义
                    </span>
                    {wordCount === 'custom' && (
                      <input type="text" inputMode="numeric" className="gz-text-field" style={{ width: 100, padding: '6px 10px', fontSize: 13, WebkitAppearance: 'none', MozAppearance: 'textfield' }}
                        placeholder="字数" value={customWordCount}
                        onChange={e => setCustomWordCount(e.target.value)} />
                    )}
                  </div>
                </div>
              </div>

              <div className="gz-form-row">
                <div className="gz-label">改写模式</div>
                <div>
                  <div className="gz-chip-group">
                    {REWRITE_MODES.map(m => (
                      <span key={m.id} className={`gz-chip ${mode === m.id ? 'gz-selected' : ''}`}
                        onClick={() => setMode(m.id)}>{m.label}</span>
                    ))}
                  </div>
                </div>
              </div>

              <div className="gz-form-row">
                <div className="gz-label">目标人群</div>
                <div>
                  <div className="gz-chip-group">
                    {['中产', '宝妈', '高净值', '银发族', '单身青年', '创业者'].map(a => (
                      <span key={a} className={`gz-chip ${audience.includes(a) ? 'gz-selected' : ''}`}
                        onClick={() => toggle(audience, setAudience, a)}>{a}</span>
                    ))}
                  </div>
                </div>
              </div>

              <div className="gz-form-row">
                <div className="gz-label">险种</div>
                <div>
                  <div className="gz-chip-group">
                    {['医疗险', '重疾险', '寿险', '意外险', '年金险'].map(p => (
                      <span key={p} className={`gz-chip ${products.includes(p) ? 'gz-selected' : ''}`}
                        onClick={() => toggle(products, setProducts, p)}>{p}</span>
                    ))}
                  </div>
                </div>
              </div>

              {error && <div className="gz-error">{error}</div>}
            </div>
          </div>
        </div>
      )}

      {/* ═══════ STEP 2 ═══════ */}
      {step === 2 && result && (
        <div>
          {/* 摘要条 */}
          <div className="gz-rewrite-summary">
            <div className="gz-rs-left">
              <div className="gz-rs-ico">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>
              </div>
              <div className="gz-rs-info">
                <div>仿写完成 · {useStyle && styleProfile ? `套用风格 · ` : ''}<b>{editedBody.length} 字</b>长文{audience.length > 0 ? ` · 面向 <b>${audience.join('、')}</b>` : ''}</div>
                <div className="gz-rs-meta">
                  {titles.length} 个新标题 · {imageMode === 'full' ? '1 张封面 + 正文配图' : imageMode === 'cover' ? '1 张封面' : '不含图片'}
                </div>
              </div>
            </div>
            <button className="gz-btn-tool" onClick={() => goStep(1)}>
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"><polyline points="15 18 9 12 15 6"/></svg>
              <span>返回修改</span>
            </button>
          </div>

          {/* 标题候选 */}
          {titles.length > 0 && (
            <div className="gz-section-card">
              <div className="gz-sc-head">
                <h3>{titles.length} 个新标题候选</h3>
                <span className="gz-desc">挑一个最贴近你想表达的</span>
              </div>
              <div className="gz-sc-body">
                <div className="gz-title-cards">
                  {titles.map((t, i) => {
                    const text = typeof t === 'string' ? t.replace(/^\d+[.、]\s*/, '') : String(t)
                    return (
                      <div key={i}
                        className={`gz-title-card ${selectedTitle === i ? 'gz-tc-selected' : ''}`}
                        onClick={() => setSelectedTitle(i)}>
                        <span className="gz-tc-label">{TITLE_STYLE_LABELS[i] || `方案${i + 1}`}</span>
                        <span className="gz-tc-text">{text}</span>
                        <div className="gz-tc-check">✓</div>
                      </div>
                    )
                  })}
                </div>
              </div>
            </div>
          )}

          {/* 正文 */}
          <div className="gz-section-card">
            <div className="gz-sc-head">
              <h3>改写后的正文</h3>
              <span className="gz-desc">可直接编辑 · {editedBody.length} 字</span>
            </div>
            <div className="gz-sc-body">
              <div className="gz-body-editor" contentEditable suppressContentEditableWarning
                onBlur={e => setEditedBody(e.currentTarget.textContent || '')}
                dangerouslySetInnerHTML={{ __html: editedBody.replace(/\n/g, '<br/>') }} />
            </div>
          </div>

          {/* 封面 & 配图选择提示 */}
          {imageMode !== 'none' && (
            <div style={{ display: 'flex', gap: 10, marginBottom: 16 }}>
              <button className="gz-btn-primary-large" onClick={() => setStep(3)}>
                <span>下一步：配图</span>
                <span className="gz-arr">→</span>
              </button>
            </div>
          )}

          {imageMode === 'none' && (
            <div className="gz-done-section">
              <div className="gz-done-left">
                <div className="gz-done-icon">✓</div>
                <div>
                  <h4>仿写完成</h4>
                  <div className="gz-done-desc">{titles.length} 个标题 · {editedBody.length} 字长文 · 不含图片</div>
                </div>
              </div>
              <div className="gz-done-right">
                <button className="gz-btn-done" onClick={() => copyToClipboard(stripMarkdown(editedBody))}>
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                  <span>复制全文</span>
                </button>
                <button className="gz-btn-done gz-primary" onClick={() => copyToClipboard(stripMarkdown(editedBody))}>
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/></svg>
                  <span>保存为草稿</span>
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ═══════ STEP 3 ═══════ */}
      {step === 3 && (
        <div>
          {/* 返回按钮 */}
          <div style={{ marginBottom: 16 }}>
            <button className="gz-btn-back" onClick={() => goStep(2)}>
              ← 回到改写结果
            </button>
          </div>

          {/* ── 封面区域 ── */}
          {showCoverSection && (
            <div className="gz-cover-section">
              <div className="gz-cover-head">
                <h3><span className="gz-star-tag">★ 封面</span>新封面</h3>
                <span className="gz-desc">公众号封面 2.35:1 横版 · AI 根据改写内容生成</span>
              </div>
              <div className="gz-cover-body">
                {/* 原封面 */}
                {extracted?.cover && (
                  <div className="gz-cover-pair gz-original">
                    <div className="gz-cover-pair-head">
                      <span>— ORIGINAL · 原封面</span>
                      <span style={{ fontFamily: 'var(--mono)', fontSize: 10, color: 'var(--ink-3)' }}>2.35:1</span>
                    </div>
                    <div className="gz-cover-pair-img">
                      <img src={proxyImageUrl(extracted.cover)} alt="原封面"
                        onError={e => { e.currentTarget.style.display = 'none' }} />
                    </div>
                  </div>
                )}

                {/* 新封面 */}
                {coverImage ? (
                  <div className="gz-cover-pair gz-new">
                    <div className="gz-cover-pair-head">
                      <span>— NEW · 新封面</span>
                      <span style={{ fontFamily: 'var(--mono)', fontSize: 10, color: 'var(--clay-deep)' }}>2.35:1</span>
                    </div>
                    <div className="gz-cover-pair-img">
                      <img src={coverImage} alt="新封面" />
                    </div>
                    <div className="gz-cover-pair-actions">
                      <button className="gz-cover-pair-action" onClick={() => handleRegenOne(0, '', true)} disabled={regenIndex === 0}>
                        <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
                        重新生成
                      </button>
                      <button className="gz-cover-pair-action" onClick={() => handleDownload(coverImage)}>下载</button>
                    </div>
                  </div>
                ) : imageLoading ? (
                  <div className="gz-skeleton" style={{ aspectRatio: '2.35 / 1', borderRadius: 'var(--r-md)' }} />
                ) : (
                  <button className="gz-btn-primary-large" onClick={handleGenerateCover} style={{ width: '100%' }}>
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/></svg>
                    <span>生成封面</span>
                  </button>
                )}
              </div>
            </div>
          )}

          {/* ── 正文配图区域 ── */}
          {showBodyImages && (
            <div className="gz-section-card">
              <div className="gz-sc-head">
                <h3>正文配图</h3>
                <span className="gz-desc">配合改写后的内容 · 2.35:1 横版</span>
              </div>
              <div className="gz-sc-body">
                {batchImages.length > 0 ? (
                  <div className="gz-images-grid">
                    {batchImages.map((item, i) => (
                      <div key={i} className="gz-image-card">
                        {i === 0 && <span className="gz-img-cover-badge">★ 封面</span>}
                        <span className="gz-img-number">{i + 1} / {batchImages.length}</span>
                        {item.imageUrl ? (
                          <>
                            <img src={item.imageUrl} alt={`配图${i + 1}`} />
                            <div className="gz-img-overlay">
                              <button onClick={(e) => { e.stopPropagation(); setLightboxUrl(item.imageUrl) }}>
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7"/></svg>
                                放大
                              </button>
                              <button onClick={(e) => { e.stopPropagation(); handleRegenOne(i, item.description || '', i === 0) }}
                                disabled={regenIndex === i}>
                                {regenIndex === i ? '…' : (<><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>重做</>)}
                              </button>
                              <button onClick={(e) => { e.stopPropagation(); handleDownload(item.imageUrl) }}>
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                                下载
                              </button>
                            </div>
                          </>
                        ) : (
                          <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
                            生成失败
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                ) : batchImgLoading ? (
                  <div className="gz-images-grid">
                    {Array.from({ length: imgCount }).map((_, i) => (
                      <div key={i} className="gz-skeleton" style={{ aspectRatio: '2.35 / 1' }} />
                    ))}
                  </div>
                ) : (
                  <div>
                    {/* 张数选择 */}
                    <div className="gz-form-row">
                      <div className="gz-label">张数</div>
                      <div>
                        <div className="gz-chip-group">
                          {[1, 2, 3, 4].map(n => (
                            <span key={n} className={`gz-chip ${imgCount === n ? 'gz-selected' : ''}`}
                              onClick={() => setImgCount(n)}>
                              {n === 1 ? '仅封面' : `封面 + ${n - 1} 正文`}
                            </span>
                          ))}
                        </div>
                      </div>
                    </div>
                    <button className="gz-btn-primary-large" onClick={handleBatchGenerate}
                      disabled={batchImgLoading || !editedBody.trim()} style={{ width: '100%', marginTop: 12 }}>
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
                      <span>AI 智能拆解，生成 {imgCount} 张</span>
                    </button>
                  </div>
                )}
              </div>
            </div>
          )}

          {imageError && <div className="gz-error">{imageError}</div>}

          {/* 完成区 */}
          {(coverImage || batchImages.length > 0) && (
            <div className="gz-done-section">
              <div className="gz-done-left">
                <div className="gz-done-icon">✓</div>
                <div>
                  <h4>仿写完成</h4>
                  <div className="gz-done-desc">
                    {titles.length} 个标题 · {editedBody.length} 字长文 · {batchImages.length || (coverImage ? 1 : 0)} 张配图（均 2.35:1）
                  </div>
                </div>
              </div>
              <div className="gz-done-right">
                <button className="gz-btn-done" onClick={() => copyToClipboard(stripMarkdown(editedBody))}>
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                  <span>复制全文</span>
                </button>
                <button className="gz-btn-done" onClick={async () => {
                  const imgs = batchImages.length > 0 ? batchImages : (coverImage ? [{ imageUrl: coverImage }] : [])
                  for (const img of imgs) {
                    if (!img.imageUrl) continue
                    await handleDownload(img.imageUrl)
                    await new Promise(r => setTimeout(r, 300))
                  }
                }}>
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                  <span>下载全部图片</span>
                </button>
                <button className="gz-btn-done gz-primary" onClick={() => copyToClipboard(stripMarkdown(editedBody))}>
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
        <div className="gz-lightbox-overlay" onClick={() => setLightboxUrl(null)}>
          <div className="gz-lightbox-box" onClick={e => e.stopPropagation()}>
            <img src={proxyImageUrl(lightboxUrl)} alt="预览" />
            <div className="gz-lightbox-actions">
              <button className="gz-lightbox-btn" onClick={() => handleDownload(lightboxUrl)}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                下载图片
              </button>
              <button className="gz-lightbox-btn gz-close" onClick={() => setLightboxUrl(null)}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                关闭
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ═══════ Actions Bar ═══════ */}
      <div className="gz-actions-bar">
        <div className="gz-ab-left">
          {step > 1 && (
            <button className="gz-btn-back" onClick={() => goStep(step - 1)}>
              ← {step === 2 ? '返回修改' : '回到改写结果'}
            </button>
          )}
        </div>
        <div className="gz-ab-right">
          <span className="gz-ab-meta">本步骤消耗 <b>{step === 1 ? 10 : 5}</b> 积分</span>
          {step === 1 && (
            <button className="gz-btn-primary-large"
              disabled={!url.trim() || !requirements.trim() || rewriting}
              onClick={handleRewrite}>
              <span>{rewriting ? '改写中…' : '开始改写'}</span>
              <span className="gz-arr">→</span>
            </button>
          )}
          {step === 2 && imageMode !== 'none' && (
            <button className="gz-btn-primary-large" onClick={() => setStep(3)}>
              <span>下一步：配图</span>
              <span className="gz-arr">→</span>
            </button>
          )}
        </div>
      </div>

      <ConfirmModal {...confirmProps} />
    </div>
  )
}
