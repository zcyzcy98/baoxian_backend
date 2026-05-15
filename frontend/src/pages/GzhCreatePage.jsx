import { useState, useEffect } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {
  callAgent,
  generateGzhImage,
  generateGzhBatchImages,
  regenGzhOneImage,
  parseRefMaterial,
  parseRefUrl,
  fetchStyleProfile,
} from '../api'
import './XhsCreatePage.css'
import './GzhCreatePage.css'

const GZH_COUNT_OPTIONS = [1, 2, 3, 4]
const GZH_RATIO_OPTIONS = [
  { value: '21:9',   label: '影院超宽 21:9' },
  { value: '16:9',   label: '横版 16:9' },
  { value: '4:3',    label: '横版 4:3' },
  { value: '1:1',    label: '方形 1:1' },
  { value: '3:4',    label: '竖版 3:4' },
]

function CopyBtn({ text, label = '复制全文' }) {
  const [copied, setCopied] = useState(false)
  const handleCopy = () => {
    const t = text || ''
    const plain = t
      .replace(/^#{1,6}\s/gm, '')
      .replace(/\*\*(.+?)\*\*/g, '$1')
      .replace(/__(.+?)__/g, '$1')
      .replace(/\*(.+?)\*/g, '$1')
      .replace(/_(.+?)_/g, '$1')
      .replace(/~~(.+?)~~/g, '$1')
      .replace(/`(.+?)`/g, '$1')
      .replace(/\[(.+?)\]\(.+?\)/g, '$1')
      .replace(/!\[.*?\]\(.+?\)/g, '')
      .replace(/^>\s/gm, '')
      .replace(/[\|\\]/g, '')
      .replace(/\n{3,}/g, '\n\n')
      .trim()
    navigator.clipboard?.writeText(plain).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    })
  }
  return (
    <button className="btn-copy-text" onClick={handleCopy}>
      {copied ? (
        <><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round"><polyline points="20 6 9 17 4 12"/></svg><span>已复制</span></>
      ) : (
        <><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg><span>{label}</span></>
      )}
    </button>
  )
}

function RefMaterialModal({ show, onClose, onAdd }) {
  const [tab, setTab] = useState('file')
  const [rawPaste, setRawPaste] = useState('')
  const [cleanUrl, setCleanUrl] = useState('')
  const [extracting, setExtracting] = useState(false)
  const [error, setError] = useState('')

  const detectPlatform = (url) => {
    if (!url) return ''
    const l = url.toLowerCase()
    if (l.includes('xiaohongshu') || l.includes('xhslink')) return 'xhs'
    if (l.includes('weixin') || l.includes('mp.weixin')) return 'gzh'
    if (l.includes('douyin') || l.includes('iesdouyin')) return 'douyin'
    return ''
  }
  const PLATFORM_LABEL = { xhs: '小红书', gzh: '公众号', douyin: '抖音' }
  const PLATFORM_COLOR = { xhs: '#FF2442', gzh: '#07C160', douyin: '#111' }

  const platform = detectPlatform(cleanUrl)

  const handlePasteChange = (text) => {
    setRawPaste(text)
    setError('')
    const m = text.match(/https?:\/\/[^\s一-龥\[\]【】「」（）()]+/)
    setCleanUrl(m ? m[0].replace(/[,，。.!！?？]+$/, '') : '')
  }

  const handleSubmit = async () => {
    if (!cleanUrl || extracting) return
    setExtracting(true)
    setError('')
    try {
      const data = await parseRefUrl(cleanUrl)
      onAdd({
        name: (data.fileName || cleanUrl).length > 60 ? (data.fileName || cleanUrl).slice(0, 60) + '...' : (data.fileName || cleanUrl),
        type: data.fileType || 'link',
        label: PLATFORM_LABEL[data.fileType] || '链接',
        text: data.extractedText || '参考链接: ' + cleanUrl,
      })
      setRawPaste(''); setCleanUrl(''); onClose()
    } catch (e) {
      setError(e.message)
    } finally {
      setExtracting(false)
    }
  }

  const handleFilePick = () => {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = '.pdf,.docx,.jpg,.jpeg,.png,.gif,.webp'
    input.onchange = async (e) => {
      const file = e.target.files?.[0]
      if (!file) return
      const isImage = file.type?.startsWith('image/')
      if (isImage) {
        onAdd({ name: file.name, type: 'img', text: '参考图片: ' + file.name })
        onClose()
        return
      }
      try {
        const result = await parseRefMaterial(file)
        onAdd({
          name: result.fileName || file.name,
          type: result.fileType?.includes('pdf') ? 'pdf' : 'doc',
          text: result.extractedText || '',
        })
        onClose()
      } catch (e) {
        setError(e.message)
      }
    }
    input.click()
  }

  if (!show) return null
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-sheet" onClick={e => e.stopPropagation()}>
        <div className="modal-top">
          <h3>添加参考资料</h3>
          <button className="modal-close-btn" onClick={onClose}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>

        <div className="modal-tabs">
          <button className={`mtab ${tab === 'file' ? 'active' : ''}`} onClick={() => setTab('file')}>
            上传文件
            <span className="mtab-badge">PDF/图片</span>
          </button>
          <button className={`mtab ${tab === 'link' ? 'active' : ''}`} onClick={() => setTab('link')}>
            粘贴链接
            <span className="mtab-badge">自动提取</span>
          </button>
        </div>

        {tab === 'link' ? (
          <div className="modal-field">
            <label>直接粘贴分享内容 <span className="field-hint">支持公众号 / 小红书 / 抖音，可连表情文字一起粘贴</span></label>
            <textarea
              className="modal-textarea modal-textarea-link"
              value={rawPaste}
              rows={3}
              placeholder="直接粘贴分享文本即可"
              onChange={e => handlePasteChange(e.target.value)}
            />
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
              <p className="field-tip">系统自动从分享文本中提取链接并解析内容</p>
            )}
            {error && <p className="field-tip field-tip-warn">{error}</p>}
          </div>
        ) : (
          <div className="modal-field">
            <label>上传文件 <span className="field-hint">支持 PDF / DOCX / 图片</span></label>
            <div className="file-drop-zone" onClick={handleFilePick}>
              <div className="file-drop-icon">
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                  <polyline points="17 8 12 3 7 8"/>
                  <line x1="12" y1="3" x2="12" y2="15"/>
                </svg>
              </div>
              <p className="file-drop-text">点击选择文件</p>
              <p className="file-drop-hint">PDF / DOCX / JPG / PNG，建议 20MB 以内</p>
            </div>
            {error && <p className="field-tip field-tip-warn" style={{ marginTop: 8 }}>{error}</p>}
          </div>
        )}

        <div className="modal-footer">
          <button className="btn-ghost" onClick={onClose}>取消</button>
          {tab === 'link' && (
            <button className="btn-solid" onClick={handleSubmit} disabled={!cleanUrl || extracting}>
              {extracting ? '解析中…' : '添加链接'}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

export default function GzhCreatePage({
  topicPrefill, onPrefillConsumed,
  contentPrefill, onContentPrefillConsumed,
  onNavigate, onNavigateWithContentPrefill,
}) {
  const ST = {
    step: 1,
    loading: false,
    topic: '',
    direction: '',
    wordCount: '',
    customWordCount: '',
    insuranceTypes: [],
    audiences: [],
    styleOption: '',
    titles: [],
    selectedTitle: 0,
    bodyContent: '',
    viewMode: 'preview',
    titlesLoaded: false,
    refMaterials: [],
    styleProfile: null,
    // 配图相关
    imgMode: null,            // 'single' | 'batch'
    imgRatio: '16:9',         // 用户当前选择
    resultRatio: null,        // 已生成结果对应的比例（避免生成后用户切换 chip 导致展示错位）
    imgCount: 2,              // 封面 + N 张正文，默认封面+1
    imageLoading: false,
    coverImage: null,         // 单张封面 url
    batchImgLoading: false,
    batchImgResults: [],
    batchImgError: '',
    regenLoadingIndex: -1,
    lightboxUrl: null,
    showRefModal: false,
  }
  const [step, setStep] = useState(ST.step)
  const [loading, setLoading] = useState(ST.loading)
  const [topic, setTopic] = useState(ST.topic)
  const [direction, setDirection] = useState(ST.direction)
  const [wordCount, setWordCount] = useState(ST.wordCount)
  const [customWordCount, setCustomWordCount] = useState(ST.customWordCount)
  const [insuranceTypes, setInsuranceTypes] = useState(ST.insuranceTypes)
  const [audiences, setAudiences] = useState(ST.audiences)
  const [styleOption, setStyleOption] = useState(ST.styleOption)
  const [titles, setTitles] = useState(ST.titles)
  const [selectedTitle, setSelectedTitle] = useState(ST.selectedTitle)
  const [bodyContent, setBodyContent] = useState(ST.bodyContent)
  const [viewMode, setViewMode] = useState(ST.viewMode)
  const [titlesLoaded, setTitlesLoaded] = useState(ST.titlesLoaded)
  const [refMaterials, setRefMaterials] = useState(ST.refMaterials)
  const [styleProfile, setStyleProfile] = useState(ST.styleProfile)
  const [imgMode, setImgMode] = useState(ST.imgMode)
  const [imgRatio, setImgRatio] = useState(ST.imgRatio)
  const [resultRatio, setResultRatio] = useState(ST.resultRatio)
  const [imgCount, setImgCount] = useState(ST.imgCount)
  const [imageLoading, setImageLoading] = useState(ST.imageLoading)
  const [coverImage, setCoverImage] = useState(ST.coverImage)
  const [batchImgLoading, setBatchImgLoading] = useState(ST.batchImgLoading)
  const [batchImgResults, setBatchImgResults] = useState(ST.batchImgResults)
  const [batchImgError, setBatchImgError] = useState(ST.batchImgError)
  const [regenLoadingIndex, setRegenLoadingIndex] = useState(ST.regenLoadingIndex)
  const [lightboxUrl, setLightboxUrl] = useState(ST.lightboxUrl)
  const [showRefModal, setShowRefModal] = useState(ST.showRefModal)

  const resetState = () => {
    setStep(ST.step); setLoading(ST.loading); setTopic(ST.topic); setDirection(ST.direction)
    setWordCount(ST.wordCount); setCustomWordCount(ST.customWordCount); setInsuranceTypes(ST.insuranceTypes); setAudiences(ST.audiences)
    setStyleOption(ST.styleOption); setTitles(ST.titles); setSelectedTitle(ST.selectedTitle)
    setBodyContent(ST.bodyContent); setViewMode(ST.viewMode); setTitlesLoaded(ST.titlesLoaded)
    setRefMaterials(ST.refMaterials); setStyleProfile(ST.styleProfile)
    setImgMode(ST.imgMode); setImgRatio(ST.imgRatio); setResultRatio(ST.resultRatio); setImgCount(ST.imgCount)
    setImageLoading(ST.imageLoading); setCoverImage(ST.coverImage)
    setBatchImgLoading(ST.batchImgLoading); setBatchImgResults(ST.batchImgResults); setBatchImgError(ST.batchImgError)
    setRegenLoadingIndex(ST.regenLoadingIndex); setLightboxUrl(ST.lightboxUrl); setShowRefModal(ST.showRefModal)
  }

  useEffect(() => {
    if (topicPrefill) {
      setTopic(topicPrefill.topic || '')
      setDirection(topicPrefill.angle || '')
      onPrefillConsumed?.()
    }
  }, [topicPrefill])

  useEffect(() => {
    if (contentPrefill) {
      if (contentPrefill.step != null) setStep(contentPrefill.step)
      if (contentPrefill.topic) setTopic(contentPrefill.topic)
      if (contentPrefill.direction) setDirection(contentPrefill.direction)
      if (contentPrefill.wordCount) setWordCount(contentPrefill.wordCount)
      if (contentPrefill.insuranceTypes) setInsuranceTypes(contentPrefill.insuranceTypes)
      if (contentPrefill.audiences) setAudiences(contentPrefill.audiences)
      if (contentPrefill.styleOption) setStyleOption(contentPrefill.styleOption)
      if (contentPrefill.titles) { setTitles(contentPrefill.titles); setTitlesLoaded(true) }
      if (contentPrefill.selectedTitle != null) setSelectedTitle(contentPrefill.selectedTitle)
      if (contentPrefill.bodyContent) { setBodyContent(contentPrefill.bodyContent); setViewMode('preview') }
      if (contentPrefill.viewMode) setViewMode(contentPrefill.viewMode)
      onContentPrefillConsumed?.()
    }
  }, [contentPrefill])

  useEffect(() => {
    if (styleOption === 'personal' && !styleProfile) {
      fetchStyleProfile().then(data => setStyleProfile(data)).catch(() => setStyleProfile(null))
    }
    if (styleOption !== 'personal') setStyleProfile(null)
  }, [styleOption])

  const wordCountOptions = ['1500', '2500', '3000']
  const insuranceOptions = ['医疗险', '重疾险', '寿险', '意外险', '年金险', '储蓄险', '少儿险', '团险']
  const audienceOptions = ['中产', '宝妈', '高净值', '银发族', '单身青年', '创业者', '全职主妇']

  const STYLE_MAP = {
    personal: '温暖克制，擅讲案例',
    professional: '严谨专业，引经据典，数据驱动',
    warm: '亲切温暖，生活化，像跟朋友聊天',
    sharp: '犀利直击，痛点先行，节奏快',
  }

  const buildStyleParam = () => {
    const parts = []
    if (direction.trim()) parts.push('方向: ' + direction.trim())
    if (insuranceTypes.length) parts.push('涉及险种: ' + insuranceTypes.join('、'))
    if (audiences.length) parts.push('目标人群: ' + audiences.join('、'))
    if (styleOption && STYLE_MAP[styleOption]) {
      if (styleOption === 'personal' && styleProfile) {
        const p = styleProfile.profile || styleProfile
        parts.push('[个人风格档案]')
        if (p.signature) parts.push('签名: ' + p.signature)
        if (p.traits) {
          const traitsText = Array.isArray(p.traits)
            ? p.traits.map(t => typeof t === 'string' ? t : (t.name || t.label || t.description || '')).filter(Boolean).join('；')
            : String(p.traits)
          if (traitsText) parts.push('特征: ' + traitsText)
        }
      } else {
        parts.push('风格: ' + STYLE_MAP[styleOption])
      }
    }
    return parts.join('\n') || undefined
  }

  const stripMd = (t) => {
    if (!t) return ''
    return t
      .replace(/^#{1,6}\s/gm, '').replace(/\*\*(.+?)\*\*/g, '$1').replace(/__(.+?)__/g, '$1')
      .replace(/\*(.+?)\*/g, '$1').replace(/_(.+?)_/g, '$1').replace(/~~(.+?)~~/g, '$1')
      .replace(/`(.+?)`/g, '$1').replace(/\[(.+?)\]\(.+?\)/g, '$1').replace(/!\[.*?\]\(.+?\)/g, '')
      .replace(/^>\s/gm, '').replace(/[\|\\]/g, '').replace(/\n{3,}/g, '\n\n').trim()
  }

  const toggleChip = (list, setList, value) => {
    setList(prev => prev.includes(value) ? prev.filter(v => v !== value) : [...prev, value])
  }

  const handleGenerate = async () => {
    if (!topic.trim()) return
    setLoading(true)
    try {
      const refText = refMaterials.map(r => r.text).filter(Boolean).join('\n---\n')
      const res = await callAgent('gzh-title', {
        topic,
        style: buildStyleParam(),
        references: refText || undefined,
      })
      const lines = (res.content || '').split('\n').filter(l => l.trim())
      setTitles(lines.slice(0, 5))
      setTitlesLoaded(true)
      setBodyContent('')
      setStep(2)
    } catch (e) {
      alert('生成失败: ' + e.message)
    } finally {
      setLoading(false)
    }
  }

  const handleGenerateBody = async () => {
    setLoading(true)
    try {
      const selectedTitleText = titles[selectedTitle] || ''
      const refText = refMaterials.map(r => r.text).filter(Boolean).join('\n---\n')
      const res = await callAgent('gzh-text', {
        topic: selectedTitleText,
        style: buildStyleParam(),
        wordCount: parseInt(wordCount === 'custom' ? customWordCount : wordCount),
        references: refText || undefined,
      })
      setBodyContent(res.content || '')
    } catch (e) {
      alert('生成失败: ' + e.message)
    } finally {
      setLoading(false)
    }
  }

  const handleRemoveRef = (idx) => {
    setRefMaterials(prev => prev.filter((_, i) => i !== idx))
  }

  // ─── 配图 ────────────────────────────────────────────────────
  const handleGenerateCover = async (useSeedream = false) => {
    setImageLoading(true)
    try {
      const selectedTitleText = titles[selectedTitle] || ''
      const res = await generateGzhImage({
        topic: selectedTitleText,
        imageRatio: imgRatio,
        imageProvider: useSeedream ? 'seedream' : 'hiapi',
      })
      setCoverImage(res.imageUrl || null)
    } catch (e) {
      alert('封面生成失败: ' + e.message)
    } finally {
      setImageLoading(false)
    }
  }

  const handleBatchGenerateImages = async () => {
    if (!bodyContent.trim() || batchImgLoading) return
    setBatchImgLoading(true)
    setBatchImgError('')
    try {
      const data = await generateGzhBatchImages(bodyContent, imgCount, imgRatio, 'hiapi')
      setBatchImgResults(data)
      setCoverImage(null)
    } catch (e) {
      setBatchImgError(e.message)
    } finally {
      setBatchImgLoading(false)
    }
  }

  const handleRegenOneImage = async (itemIndex, itemDesc, isCover) => {
    setRegenLoadingIndex(itemIndex)
    try {
      const res = await regenGzhOneImage(bodyContent, itemDesc, imgRatio, 'hiapi', isCover)
      setBatchImgResults(prev => prev.map((item, i) => {
        if (i !== itemIndex) return item
        return { ...item, imageUrl: res.imageUrl, error: undefined }
      }))
    } catch (e) {
      setBatchImgResults(prev => prev.map((item, i) => {
        if (i !== itemIndex) return item
        return { ...item, error: e.message }
      }))
    } finally {
      setRegenLoadingIndex(-1)
    }
  }

  const goStep = (n) => { if (n >= 1 && n <= 3) setStep(n) }

  const handleDownload = async (url) => {
    try {
      const res = await fetch(url)
      const blob = await res.blob()
      const a = document.createElement('a')
      a.href = URL.createObjectURL(blob)
      a.download = `公众号配图_${Date.now()}.png`
      a.click()
      URL.revokeObjectURL(a.href)
    } catch {
      window.open(url, '_blank')
    }
  }

  // aspect-ratio for inline styles (CSS class .ratio-2.35-1 会被 CSS 解析器拆开，所以走 inline)
  const ratioAspectStyle = (r) => {
    const [w, h] = (r || '2.35:1').split(':').map(Number)
    return { aspectRatio: `${w} / ${h}` }
  }

  return (
    <div className="xhs-create-page">
      <div className="page-head">
        <div className="page-title-row">
          <div className="page-title">
            <span className="platform-tag gzh">公</span>
            <h2>公众号创作</h2>
            {topic && (
              <span className="from-topic">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M12 2 L13.5 8.5 L20 10 L13.5 11.5 L12 18 L10.5 11.5 L4 10 L10.5 8.5 Z"/></svg>
                来自选题：<b>《{topic}》</b>
              </span>
            )}
          </div>
          <button className="btn-ghost reset-btn" onClick={resetState} title="清除所有内容重新开始">
            重新开始
          </button>
        </div>
        <div className="steps">
          <div className={`step ${step > 1 ? 'done' : ''} ${step === 1 ? 'active' : ''}`} onClick={() => goStep(1)}>
            <div className="dot">{step > 1 ? '✓' : '1'}</div>
            <div className="step-label">提需求</div>
          </div>
          <div className={`step ${step > 2 ? 'done' : ''} ${step === 2 ? 'active' : ''}`} onClick={() => step >= 2 && goStep(2)}>
            <div className="dot">{step > 2 ? '✓' : '2'}</div>
            <div className="step-label">改文案</div>
          </div>
          <div className={`step ${step === 3 ? 'active' : ''}`} onClick={() => step >= 3 && goStep(3)}>
            <div className="dot">3</div>
            <div className="step-label">配图</div>
          </div>
        </div>
      </div>

      {/* ─── Step 1: 提需求 ──────────────────────────────────────── */}
      {step === 1 && (
        <div className="step-pane">
          <div className="section-card">
            <div className="sc-head">
              <h3>告诉 AI 你想写什么</h3>
              <span className="desc">填写主题，AI 生成 5 个公众号标题候选</span>
            </div>
            <div className="sc-body">
              <div className="form-row">
                <div className="label">选题主题 <span className="req">*</span></div>
                <div className="control">
                  <input type="text" className="text-field" value={topic} onChange={e => setTopic(e.target.value)}
                    placeholder="例如：医保改革落地，30+ 群体到底要不要重新规划？" />
                </div>
              </div>

              <div className="form-row">
                <div className="label">主要内容方向</div>
                <div className="control">
                  <textarea className="text-field" value={direction} onChange={e => setDirection(e.target.value)}
                    placeholder="（选填）告诉 AI 你想强调的角度" />
                </div>
              </div>

              <div className="form-row">
                <div className="label">参考材料</div>
                <div className="control">
                  <button className="btn-add-ref-material" onClick={() => setShowRefModal(true)}>
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                    <span>上传参考资料</span>
                  </button>
                  <span className="ref-hint" style={{ marginLeft: 12 }}>链接 / PDF / DOCX / 图片</span>

                  {refMaterials.length > 0 && (
                    <div className="refs-list">
                      {refMaterials.map((ref, i) => (
                        <div key={i} className="ref-item">
                          <div className={`ref-ico ${ref.type}`}>
                            {ref.type === 'pdf' ? 'PDF' : ref.type === 'doc' ? 'DOC' : ref.type === 'img' ? '图' : ref.type === 'xhs' ? '红' : ref.type === 'gzh' ? '公' : ref.type === 'douyin' ? '抖' : '🔗'}
                          </div>
                          <div className="ref-info">
                            <span className="ref-name">{ref.name}</span>
                            {ref.label && <span className="ref-tag">{ref.label}</span>}
                          </div>
                          <button className="ref-remove" onClick={() => handleRemoveRef(i)}>×</button>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>

              <div className="form-row">
                <div className="label">目标字数 <span className="req">*</span></div>
                <div className="control">
                  <div className="chip-group" style={{ alignItems: 'center' }}>
                    {wordCountOptions.map(opt => (
                      <span key={opt} className={`chip ${wordCount === opt ? 'selected' : ''}`} onClick={() => { setWordCount(opt); setCustomWordCount('') }}>
                        {parseInt(opt).toLocaleString()} 字
                      </span>
                    ))}
                    <span className={`chip ${wordCount === 'custom' ? 'selected' : ''}`} onClick={() => setWordCount('custom')}>
                      自定义
                    </span>
                    {wordCount === 'custom' && (
                      <input type="text" inputMode="numeric" className="form-input" style={{ width: 100, padding: '6px 10px', fontSize: 13, WebkitAppearance: 'none', MozAppearance: 'textfield' }}
                        placeholder="字数" value={customWordCount}
                        onChange={e => setCustomWordCount(e.target.value)} />
                    )}
                  </div>
                </div>
              </div>

              <div className="form-row">
                <div className="label">险种 <span className="req">*</span></div>
                <div className="control">
                  <div className="chip-group">
                    {insuranceOptions.map(opt => (
                      <span key={opt} className={`chip ${insuranceTypes.includes(opt) ? 'selected' : ''}`} onClick={() => toggleChip(insuranceTypes, setInsuranceTypes, opt)}>{opt}</span>
                    ))}
                  </div>
                </div>
              </div>

              <div className="form-row">
                <div className="label">目标人群 <span className="req">*</span></div>
                <div className="control">
                  <div className="chip-group">
                    {audienceOptions.map(opt => (
                      <span key={opt} className={`chip ${audiences.includes(opt) ? 'selected' : ''}`} onClick={() => toggleChip(audiences, setAudiences, opt)}>{opt}</span>
                    ))}
                  </div>
                </div>
              </div>

              <div className="form-row">
                <div className="label">写作风格 <span className="req">*</span></div>
                <div className="control">
                  <div className="style-options">
                    <div className={`style-option ${styleOption === 'personal' ? 'selected' : ''}`} onClick={() => setStyleOption('personal')}>
                      <div className="radio"></div>
                      <div className="info">
                        <strong>我的个人风格</strong>
                        <span>{styleProfile?.profile?.sourceCount ? `基于 ${styleProfile.profile.sourceCount} 篇素材训练` : '上传素材后训练你的专属风格'}</span>
                      </div>
                    </div>
                    <div className={`style-option ${styleOption === 'professional' ? 'selected' : ''}`} onClick={() => setStyleOption('professional')}>
                      <div className="radio"></div>
                      <div className="info"><strong>通用专业风格</strong><span>严谨、引经据典、数据驱动</span></div>
                    </div>
                    <div className={`style-option ${styleOption === 'warm' ? 'selected' : ''}`} onClick={() => setStyleOption('warm')}>
                      <div className="radio"></div>
                      <div className="info"><strong>通用温暖风格</strong><span>亲切、生活化、像跟朋友聊天</span></div>
                    </div>
                    <div className={`style-option ${styleOption === 'sharp' ? 'selected' : ''}`} onClick={() => setStyleOption('sharp')}>
                      <div className="radio"></div>
                      <div className="info"><strong>通用犀利风格</strong><span>痛点直击、节奏快、容易爆</span></div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ─── Step 2: 改文案 ──────────────────────────────────────── */}
      {step === 2 && (
        <div className="step-pane">
          <div className="edit-grid">
            <div className="edit-main">
              <div className="section-card">
                <div className="sc-head">
                  <h3>标题候选</h3>
                  <span className="desc">挑一个最适合公众号的</span>
                </div>
                <div className="sc-body">
                  <div className="title-cards">
                    {titles.map((title, i) => (
                      <div key={i} className={`title-card ${selectedTitle === i ? 'selected' : ''}`} onClick={() => setSelectedTitle(i)}>
                        <span className="title-text">
                          <ReactMarkdown remarkPlugins={[remarkGfm]}>
                            {title.replace(/^\d+[.、]\s*/, '')}
                          </ReactMarkdown>
                        </span>
                        <div className="check">✓</div>
                      </div>
                    ))}
                  </div>
                  <div style={{ marginTop: 16 }}>
                    <button className="btn-primary" onClick={handleGenerateBody} disabled={loading || !titles.length}>
                      {loading ? '生成中…' : '根据这个标题生成正文'}
                    </button>
                  </div>
                </div>
              </div>

              {bodyContent && (
                <div className="section-card">
                  <div className="sc-head">
                    <h3>正文内容</h3>
                    <div className="sc-head-actions">
                      <div className="view-toggle">
                        <span className={`toggle-btn ${viewMode === 'preview' ? 'active' : ''}`} onClick={() => setViewMode('preview')}>预览</span>
                        <span className={`toggle-btn ${viewMode === 'edit' ? 'active' : ''}`} onClick={() => setViewMode('edit')}>编辑</span>
                      </div>
                      <CopyBtn text={bodyContent} />
                    </div>
                  </div>
                  <div className="sc-body">
                    {viewMode === 'edit' ? (
                      <textarea
                        className="body-editor-textarea"
                        value={bodyContent}
                        onChange={e => setBodyContent(e.target.value)}
                        placeholder="在这里编辑内容..."
                        style={{ minHeight: 520 }}
                      />
                    ) : (
                      <div className="body-editor markdown-body" style={{ minHeight: 520, lineHeight: 2, fontSize: '15.5px' }}>
                        <ReactMarkdown remarkPlugins={[remarkGfm]}>{bodyContent}</ReactMarkdown>
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>

            <div className="side-panel">
              {bodyContent && (
                <div className="word-meter">
                  <div className="wm-row">
                    <span className="wm-num">{bodyContent.length}</span>
                    <span className="wm-target">目标 <b>{parseInt(wordCount === 'custom' ? customWordCount : wordCount).toLocaleString()}</b> 字</span>
                  </div>
                  <div className="wm-track">
                    <div className="wm-fill" style={{ width: `${Math.min(100, (bodyContent.length / parseInt(wordCount === 'custom' ? customWordCount : wordCount)) * 100)}%` }}></div>
                  </div>
                </div>
              )}

              <div className="side-card">
                <div className="side-card-head">— Info · 写作信息</div>
                <div className="side-card-body">
                  <div className="info-row"><span className="k">写作风格</span><span className="v">{styleOption === 'personal' ? '我的个人风格' : styleOption === 'professional' ? '通用专业风格' : styleOption === 'warm' ? '通用温暖风格' : styleOption === 'sharp' ? '通用犀利风格' : '默认'}</span></div>
                  <div className="info-row"><span className="k">险种</span><span className="v">{insuranceTypes.join(', ')}</span></div>
                  <div className="info-row"><span className="k">目标人群</span><span className="v">{audiences.join(', ')}</span></div>
                  <div className="info-row"><span className="k">目标字数</span><span className="v serif">{parseInt(wordCount === 'custom' ? customWordCount : wordCount).toLocaleString()}</span></div>
                  {bodyContent && <div className="info-row"><span className="k">实际字数</span><span className="v serif">{bodyContent.length}</span></div>}
                </div>
              </div>

              {!bodyContent ? (
                <button className="regen-btn" onClick={handleGenerate}>
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
                  <span>重新生成标题</span>
                  <span className="credits-tip">−1 积分</span>
                </button>
              ) : (
                <button className="regen-btn" onClick={handleGenerateBody}>
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
                  <span>重新生成正文</span>
                  <span className="credits-tip">−5 积分</span>
                </button>
              )}
            </div>
          </div>
        </div>
      )}

      {/* ─── Step 3: 配图 ──────────────────────────────────────── */}
      {step === 3 && (
        <div className="step-pane">
          <div className="step-3-layout">
            {/* 模式选择 */}
            {!imgMode && (
              <div className="img-mode-select">
                <h3 style={{ textAlign: 'center', marginBottom: 24, fontFamily: 'var(--serif)' }}>选择配图方式</h3>
                <div className="img-mode-cards">
                  <div className="img-mode-card" onClick={() => setImgMode('single')}>
                    <div className="mode-icon">
                      <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/></svg>
                    </div>
                    <h4>单张封面</h4>
                    <p>AI 根据标题生成一张杂志海报风的公众号封面图</p>
                  </div>
                  <div className="img-mode-card" onClick={() => setImgMode('batch')}>
                    <div className="mode-icon">
                      <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
                    </div>
                    <h4>封面 + 正文配图</h4>
                    <p>封面 + 1-3 张正文图，AI 根据文章内容拆解灵活配图</p>
                  </div>
                </div>
              </div>
            )}

            {/* 单张封面 - 配置区 */}
            {imgMode === 'single' && !(imageLoading || coverImage) && (
              <div className="section-card">
                <div className="sc-head">
                  <button className="btn-back-sm" onClick={() => setImgMode(null)}>← 返回</button>
                  <h3>单张封面</h3>
                </div>
                <div className="sc-body">
                  <div className="form-row" style={{ marginBottom: 16 }}>
                    <div className="label">比例</div>
                    <div className="control">
                      <div className="chip-group">
                        {GZH_RATIO_OPTIONS.map(opt => (
                          <span key={opt.value} className={`chip ${imgRatio === opt.value ? 'selected' : ''}`} onClick={() => setImgRatio(opt.value)}>{opt.label}</span>
                        ))}
                      </div>
                    </div>
                  </div>
                  <div className="generate-image-buttons" style={{ display: 'flex', gap: 10, flexDirection: 'column', marginTop: 16 }}>
                    <button className="btn-primary btn-large" onClick={() => handleGenerateCover(false)} disabled={imageLoading}>
                      {imageLoading ? '生成中…' : '使用 HiAPI 生成封面'}
                    </button>
                    <button className="btn-primary btn-large" style={{ background: '#6366F1' }} onClick={() => handleGenerateCover(true)} disabled={imageLoading}>
                      {imageLoading ? '生成中…' : '使用 Seedream 生成封面'}
                    </button>
                  </div>
                </div>
              </div>
            )}

            {/* 批量 - 配置区 */}
            {imgMode === 'batch' && !(batchImgLoading || batchImgResults.length > 0) && (
              <div className="section-card">
                <div className="sc-head">
                  <button className="btn-back-sm" onClick={() => setImgMode(null)}>← 返回</button>
                  <h3>封面 + 正文配图</h3>
                </div>
                <div className="sc-body">
                  <div className="form-row" style={{ marginBottom: 12 }}>
                    <div className="label">张数</div>
                    <div className="control">
                      <div className="chip-group">
                        {GZH_COUNT_OPTIONS.map(n => (
                          <span key={n} className={`chip ${imgCount === n ? 'selected' : ''}`} onClick={() => setImgCount(n)}>
                            {n === 1 ? '仅封面' : `封面 + ${n - 1} 正文`}
                          </span>
                        ))}
                      </div>
                    </div>
                  </div>
                  <div className="form-row" style={{ marginBottom: 16 }}>
                    <div className="label">比例</div>
                    <div className="control">
                      <div className="chip-group">
                        {GZH_RATIO_OPTIONS.map(opt => (
                          <span key={opt.value} className={`chip ${imgRatio === opt.value ? 'selected' : ''}`} onClick={() => setImgRatio(opt.value)}>{opt.label}</span>
                        ))}
                      </div>
                    </div>
                  </div>
                  {batchImgError && <div className="kb-error" style={{ marginBottom: 12 }}>{batchImgError}</div>}
                  <button className="btn-primary btn-large" style={{ width: '100%', background: '#07C160' }}
                    onClick={handleBatchGenerateImages} disabled={batchImgLoading || !bodyContent.trim()}>
                    {batchImgLoading ? '制图耗时较长，请耐心等待…' : `AI 智能拆解，生成 ${imgCount} 张`}
                  </button>
                </div>
              </div>
            )}

            {/* 结果 / 加载区 */}
            {(imageLoading || batchImgLoading || coverImage || batchImgResults.length > 0) && (
              <div className="section-card">
                <div className="sc-head">
                  <button className="btn-back-sm" onClick={() => {
                    if (batchImgLoading || imageLoading) return
                    setImgMode(null); setCoverImage(null); setBatchImgResults([])
                  }}>← 返回</button>
                  <h3>{imgMode === 'batch' ? '配图结果' : '封面结果'}</h3>
                  <span className="desc">{batchImgLoading ? '制图耗时较长，请耐心等待…' : imageLoading ? '生成中…' : ''}</span>
                </div>
                <div className="sc-body">
                  {/* 骨架 */}
                  {(batchImgLoading || imageLoading) && batchImgResults.length === 0 && !coverImage && (
                    <div className="batch-image-grid" style={{ display: 'grid', gridTemplateColumns: imgMode === 'batch' && imgCount > 1 ? 'repeat(2, 1fr)' : '1fr', gap: 16, maxWidth: imgMode === 'batch' ? '100%' : 600 }}>
                      {Array.from({ length: imgMode === 'batch' ? imgCount : 1 }).map((_, i) => (
                        <div key={`skel-${i}`} className="batch-image-item skeleton-item">
                          <div className="batch-thumb skeleton-thumb" style={ratioAspectStyle(imgRatio)} />
                        </div>
                      ))}
                    </div>
                  )}

                  {/* 批量结果 */}
                  {batchImgResults.length > 0 && (
                    <div className="batch-image-grid" style={{ display: 'grid', gridTemplateColumns: batchImgResults.length === 1 ? '1fr' : 'repeat(2, 1fr)', gap: 16, maxWidth: batchImgResults.length === 1 ? 720 : '100%', margin: batchImgResults.length === 1 ? '0 auto' : undefined }}>
                      {batchImgResults.map((item) => (
                        <div key={item.index} className={`batch-image-item ${batchImgLoading ? 'regenerating' : ''}`}>
                          {item.imageUrl ? (
                            <div className="batch-thumb-wrap gzh-thumb-wrap" style={{ position: 'relative', ...ratioAspectStyle(imgRatio) }}>
                              {item.index === 1 && <span className="cover-badge">★ 封面</span>}
                              <a href={item.imageUrl} target="_blank" rel="noreferrer" onClick={(e) => { e.preventDefault(); setLightboxUrl(item.imageUrl) }} style={{ display: 'block', width: '100%', height: '100%' }}>
                                <img src={item.imageUrl} alt={`配图${item.index}`} className="gzh-thumb-img" />
                              </a>
                              <div className="image-hover-info">
                                <div className="hover-actions">
                                  <button className="ov-btn" onClick={(e) => { e.stopPropagation(); setLightboxUrl(item.imageUrl) }}><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7"/></svg><span>放大</span></button>
                                  <button className="ov-btn" onClick={(e) => { e.stopPropagation(); handleRegenOneImage(item.index - 1, item.description || '', item.index === 1) }} disabled={regenLoadingIndex === item.index - 1}>
                                    {regenLoadingIndex === item.index - 1 ? '…' : (<><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg><span>重做</span></>)}
                                  </button>
                                  <button className="ov-btn" onClick={(e) => { e.stopPropagation(); handleDownload(item.imageUrl) }}><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg><span>下载</span></button>
                                </div>
                              </div>
                            </div>
                          ) : (
                            <div className="batch-thumb-error gzh-thumb-error" style={ratioAspectStyle(imgRatio)}><span>生成失败</span>{item.error && <small>{item.error}</small>}</div>
                          )}
                        </div>
                      ))}
                    </div>
                  )}

                  {/* 单张封面结果 */}
                  {!batchImgLoading && batchImgResults.length === 0 && coverImage && (
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: 16, maxWidth: 720, margin: '0 auto' }}>
                      <div className="batch-thumb-wrap gzh-thumb-wrap" style={{ position: 'relative', ...ratioAspectStyle(imgRatio) }}>
                        <a href={coverImage} target="_blank" rel="noreferrer" onClick={(e) => { e.preventDefault(); setLightboxUrl(coverImage) }} style={{ display: 'block', width: '100%', height: '100%' }}>
                          <img src={coverImage} alt="封面" className="gzh-thumb-img" />
                        </a>
                        <div className="image-hover-info">
                          <div className="hover-actions">
                            <button className="ov-btn" onClick={(e) => { e.stopPropagation(); setLightboxUrl(coverImage) }}>
                              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7"/></svg><span>放大</span>
                            </button>
                            <button className="ov-btn" onClick={(e) => { e.stopPropagation(); handleGenerateCover(false) }} disabled={imageLoading}>
                              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg><span>重做</span>
                            </button>
                            <button className="ov-btn" onClick={(e) => { e.stopPropagation(); handleDownload(coverImage) }}>
                              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg><span>下载</span>
                            </button>
                          </div>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>

          {/* 完成区 */}
          {(coverImage || batchImgResults.length > 0) && (
            <div className="done-section">
              <div className="l">
                <div className="done-icon">✓</div>
                <div>
                  <h4>这篇公众号已就绪</h4>
                  <div className="desc">{titles.length} 个标题候选 · {bodyContent.length} 字正文 · {batchImgResults.length || (coverImage ? 1 : 0)} 张配图</div>
                </div>
              </div>
              <div className="r">
                <button className="btn-done" onClick={() => navigator.clipboard?.writeText(stripMd(bodyContent))}>复制全文</button>
                <button className="btn-done primary" onClick={async () => {
                  const selectedTitleText = (titles[selectedTitle] || '').replace(/^\d+[.、]\s*/, '')
                  const plainBody = stripMd(bodyContent)
                  const txtContent = selectedTitleText + '\n\n' + plainBody
                  const blob = new Blob([txtContent], { type: 'text/plain;charset=utf-8' })
                  const a = document.createElement('a')
                  a.href = URL.createObjectURL(blob)
                  a.download = (selectedTitleText || '公众号文章').slice(0, 20) + '.txt'
                  a.click()
                  URL.revokeObjectURL(a.href)
                  const imgs = batchImgResults.length > 0
                    ? batchImgResults
                    : (coverImage ? [{ index: 1, imageUrl: coverImage }] : [])
                  const nameMap = { 1: '封面', 2: '正文图一', 3: '正文图二', 4: '正文图三' }
                  for (const img of imgs) {
                    if (!img.imageUrl) continue
                    try {
                      const res = await fetch(img.imageUrl)
                      const imgBlob = await res.blob()
                      const imgA = document.createElement('a')
                      imgA.href = URL.createObjectURL(imgBlob)
                      imgA.download = (nameMap[img.index] || `图${img.index}`) + '.png'
                      imgA.click()
                      URL.revokeObjectURL(imgA.href)
                      await new Promise(r => setTimeout(r, 300))
                    } catch {}
                  }
                }}>一键打包下载</button>
              </div>
            </div>
          )}
        </div>
      )}

      <RefMaterialModal show={showRefModal} onClose={() => setShowRefModal(false)}
        onAdd={(ref) => { setRefMaterials(prev => [...prev, ref]) }} />

      {lightboxUrl && (
        <div className="lightbox-overlay" onClick={() => setLightboxUrl(null)}>
          <div className="lightbox-box" onClick={e => e.stopPropagation()}>
            <img src={lightboxUrl} alt="预览" className="lightbox-img" />
            <div className="lightbox-actions">
              <button className="lightbox-btn" onClick={() => handleDownload(lightboxUrl)}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                下载图片
              </button>
              <button className="lightbox-btn close" onClick={() => setLightboxUrl(null)}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                关闭
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="actions-bar">
        <div className="l">
          {step > 1 && (
            <button className="btn-back" onClick={() => goStep(step - 1)}>
              ← {step === 2 ? '重做需求' : '回到改文案'}
            </button>
          )}
        </div>
        <div className="r">
          <span className="meta">本步骤消耗 <b>{step === 1 ? 1 : step === 2 && !bodyContent ? 5 : 5}</b> 积分</span>
          {step === 1 && (
            <button className="btn-primary-large" onClick={handleGenerate} disabled={loading || !topic.trim()}>
              <span>{loading ? '生成中…' : '生成标题候选'}</span>
              <span className="arr">→</span>
            </button>
          )}
          {step === 2 && (
            <button className="btn-primary-large" onClick={bodyContent ? () => setStep(3) : undefined} disabled={(!bodyContent && step === 2) || loading}>
              <span>{!bodyContent ? '请先生成正文' : '下一步：配图'}</span>
              {bodyContent && <span className="arr">→</span>}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
