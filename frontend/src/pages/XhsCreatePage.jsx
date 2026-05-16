import { useState, useEffect } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { callAgent, generateImage, generateSeedreamImage, fetchImageTemplates, generateXhsBatchImages, regenOneImage, parseRefMaterial, parseRefUrl, fetchStyleProfile } from '../api'
import ConfirmModal, { useConfirmModal } from '../components/ConfirmModal'
import './XhsCreatePage.css'

const COUNT_OPTIONS = [1, 3, 6, 9]
const RATIO_OPTIONS = [
  { value: '3:4', label: '竖版 3:4' },
  { value: '1:1', label: '方形 1:1' },
  { value: '9:16', label: '全屏 9:16' },
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
            <label>直接粘贴分享内容 <span className="field-hint">支持小红书 / 抖音 / 公众号，可连表情文字一起粘贴</span></label>
            <textarea
              className="modal-textarea modal-textarea-link"
              value={rawPaste}
              rows={3}
              placeholder="直接粘贴分享文本即可，例如：\n24 【一篇教你车险三家怎么买】 😆 https://www.xiaohongshu.com/..."
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

export default function XhsCreatePage({
  topicPrefill, onPrefillConsumed,
  contentPrefill, onContentPrefillConsumed,
  onNavigate, onNavigateWithContentPrefill,
}) {
  const ST = {
    step: 1,
    loading: false,
    topic: '',
    direction: '',
    insuranceTypes: [],
    audiences: [],
    styleOption: '',
    titles: [],
    selectedTitle: 0,
    bodyContent: '',
    viewMode: 'preview',
    titlesLoaded: false,
    images: [],
    imageLoading: false,
    imageTemplates: [],
    selectedTemplate: null,
    useAIFree: false,
    lightboxUrl: null,
    batchImgCount: 3,
    batchImgRatio: '3:4',
    batchImgLoading: false,
    batchImgResults: [],
    batchImgError: '',
    refMaterials: [],
    citations: [],
    regenLoadingIndex: -1,
    styleProfile: null,
    imgMode: null,
    showRefModal: false,
  }
  const [step, setStep] = useState(ST.step)
  const [loading, setLoading] = useState(ST.loading)
  const [topic, setTopic] = useState(ST.topic)
  const [direction, setDirection] = useState(ST.direction)
  const [insuranceTypes, setInsuranceTypes] = useState(ST.insuranceTypes)
  const [audiences, setAudiences] = useState(ST.audiences)
  const [styleOption, setStyleOption] = useState(ST.styleOption)
  const [titles, setTitles] = useState(ST.titles)
  const [selectedTitle, setSelectedTitle] = useState(ST.selectedTitle)
  const [bodyContent, setBodyContent] = useState(ST.bodyContent)
  const [viewMode, setViewMode] = useState(ST.viewMode)
  const [titlesLoaded, setTitlesLoaded] = useState(ST.titlesLoaded)
  const [images, setImages] = useState(ST.images)
  const [imageLoading, setImageLoading] = useState(ST.imageLoading)
  const [imageTemplates, setImageTemplates] = useState(ST.imageTemplates)
  const [selectedTemplate, setSelectedTemplate] = useState(ST.selectedTemplate)
  const [useAIFree, setUseAIFree] = useState(ST.useAIFree)
  const [lightboxUrl, setLightboxUrl] = useState(ST.lightboxUrl)
  const [batchImgCount, setBatchImgCount] = useState(ST.batchImgCount)
  const [batchImgRatio, setBatchImgRatio] = useState(ST.batchImgRatio)
  const [batchImgLoading, setBatchImgLoading] = useState(ST.batchImgLoading)
  const [batchImgResults, setBatchImgResults] = useState(ST.batchImgResults)
  const [batchImgError, setBatchImgError] = useState(ST.batchImgError)
  const [refMaterials, setRefMaterials] = useState(ST.refMaterials)
  const [citations, setCitations] = useState(ST.citations)
  const [regenLoadingIndex, setRegenLoadingIndex] = useState(ST.regenLoadingIndex)
  const [styleProfile, setStyleProfile] = useState(ST.styleProfile)
  const [imgMode, setImgMode] = useState(ST.imgMode)
  const [showRefModal, setShowRefModal] = useState(ST.showRefModal)
  const { confirm, props: confirmProps } = useConfirmModal()

  const resetState = () => {
    setStep(ST.step)
    setLoading(ST.loading)
    setTopic(ST.topic)
    setDirection(ST.direction)
    setInsuranceTypes(ST.insuranceTypes)
    setAudiences(ST.audiences)
    setStyleOption(ST.styleOption)
    setTitles(ST.titles)
    setSelectedTitle(ST.selectedTitle)
    setBodyContent(ST.bodyContent)
    setViewMode(ST.viewMode)
    setImages(ST.images)
    setImageLoading(ST.imageLoading)
    setImageTemplates(ST.imageTemplates)
    setSelectedTemplate(ST.selectedTemplate)
    setUseAIFree(ST.useAIFree)
    setLightboxUrl(ST.lightboxUrl)
    setBatchImgCount(ST.batchImgCount)
    setBatchImgRatio(ST.batchImgRatio)
    setBatchImgLoading(ST.batchImgLoading)
    setBatchImgResults(ST.batchImgResults)
    setBatchImgError(ST.batchImgError)
    setRefMaterials(ST.refMaterials)
    setCitations(ST.citations)
    setRegenLoadingIndex(ST.regenLoadingIndex)
    setStyleProfile(ST.styleProfile)
    setImgMode(ST.imgMode)
    setShowRefModal(ST.showRefModal)
    setTitlesLoaded(ST.titlesLoaded)
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
      if (contentPrefill.insuranceTypes) setInsuranceTypes(contentPrefill.insuranceTypes)
      if (contentPrefill.audiences) setAudiences(contentPrefill.audiences)
      if (contentPrefill.styleOption) setStyleOption(contentPrefill.styleOption)
      if (contentPrefill.titles) { setTitles(contentPrefill.titles); setTitlesLoaded(true) }
      if (contentPrefill.selectedTitle != null) setSelectedTitle(contentPrefill.selectedTitle)
      if (contentPrefill.bodyContent) { setBodyContent(contentPrefill.bodyContent); setViewMode('preview') }
      if (contentPrefill.images) setImages(contentPrefill.images)
      if (contentPrefill.viewMode) setViewMode(contentPrefill.viewMode)
      onContentPrefillConsumed?.()
    }
  }, [contentPrefill])

  // 获取个人风格档案
  useEffect(() => {
    if (styleOption === 'personal' && !styleProfile) {
      fetchStyleProfile().then(data => {
        setStyleProfile(data)
      }).catch(err => {
        console.warn('[风格档案] 获取失败，使用默认:', err.message)
        setStyleProfile(null)
      })
    }
    if (styleOption !== 'personal') setStyleProfile(null)
  }, [styleOption])

  // 获取图片模板
  useEffect(() => {
    if (step === 3 && imageTemplates.length === 0) {
      fetchImageTemplates().then(data => {
        console.log('[模板数据]', data)
        setImageTemplates(Array.isArray(data) ? data : data?.templates || [])
      }).catch(err => {
        console.error('[获取模板失败]', err)
      })
    }
  }, [step])

  const insuranceOptions = ['医疗险', '重疾险', '寿险', '意外险', '年金险', '储蓄险', '少儿险', '团险']
  const audienceOptions = ['中产', '宝妈', '高净值', '银发族', '单身青年', '创业者', '全职主妇']

  const STYLE_MAP = {
    personal: '温暖克制，擅讲案例，像朋友聊天',
    professional: '严谨专业，引经据典，数据驱动',
    warm: '亲切温暖，生活化，像跟朋友聊天',
    sharp: '犀利直击，痛点先行，节奏快',
  }

  const buildStyleParam = () => {
    const parts = []
    if (direction.trim()) parts.push('方向: ' + direction.trim())
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
        if (p.radar) {
          const radar = p.radar
          const radarText = Object.entries(radar)
            .map(([k, v]) => k + ':' + v)
            .join('，')
          if (radarText) parts.push('维度: ' + radarText)
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
  }

  const toggleChip = (list, setList, value) => {
    setList(prev => prev.includes(value) ? prev.filter(v => v !== value) : [...prev, value])
  }

  const handleGenerate = async () => {
    if (!topic.trim()) return
    setLoading(true)
    try {
      // 只生成标题
      const refText = refMaterials.map(r => r.text).filter(Boolean).join('\n---\n')
      const titleRes = await callAgent('title', { topic, style: buildStyleParam(), references: refText || undefined }, 'rag-xhs')
      const titleContent = titleRes.content || ''
      if (titleRes.citations) setCitations(titleRes.citations)
      
      // 直接按换行分割取前5个
      const lines = titleContent.split('\n').filter(l => l.trim())
      const parsedTitles = lines.slice(0, 5)

      setTitles(parsedTitles.length > 0 ? parsedTitles : ['标题生成中…'])
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
      const bodyRes = await callAgent('text', {
        topic: selectedTitleText,
        title: selectedTitleText,
        style: buildStyleParam(),
        references: refText || undefined,
      }, 'rag-xhs')

      setBodyContent(bodyRes.content || '')
      if (bodyRes.citations) setCitations(bodyRes.citations)
    } catch (e) {
      alert('生成失败: ' + e.message)
    } finally {
      setLoading(false)
    }
  }

  const handleRemoveRef = (idx) => {
    setRefMaterials(prev => prev.filter((_, i) => i !== idx))
  }

  const handleRegenOneImage = async (itemIndex, itemDesc) => {
    setRegenLoadingIndex(itemIndex)
    try {
      const res = await regenOneImage(bodyContent, itemDesc, batchImgRatio, 'hiapi')
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

  const handleGenerateImageHiAPI = async () => {
    setImageLoading(true)
    try {
      const selectedTitleText = titles[selectedTitle] || ''
      const selectedTemplateObj = selectedTemplate != null ? imageTemplates[selectedTemplate] : null
      const res = await generateImage({ 
        topic: selectedTitleText,
        templateId: selectedTemplateObj?.id,
        style: useAIFree ? 'ai-free' : undefined
      })
      setImages(res.imageUrl ? [res.imageUrl] : [])
    } catch (e) {
      alert('图片生成失败: ' + e.message)
    } finally {
      setImageLoading(false)
    }
  }

  const handleBatchGenerateImages = async () => {
    if (!bodyContent.trim() || batchImgLoading) return
    setBatchImgLoading(true)
    setBatchImgError('')
    try {
      const data = await generateXhsBatchImages(bodyContent, batchImgCount, batchImgRatio, 'hiapi')
      setBatchImgResults(data)
      setImages([])
    } catch (e) {
      setBatchImgError(e.message)
    } finally {
      setBatchImgLoading(false)
    }
  }

  const handleGenerateImageSeedream = async () => {
    setImageLoading(true)
    try {
      const selectedTitleText = titles[selectedTitle] || ''
      const selectedTemplateObj = selectedTemplate != null ? imageTemplates[selectedTemplate] : null
      const res = await generateSeedreamImage({ 
        topic: selectedTitleText,
        templateId: selectedTemplateObj?.id,
        style: useAIFree ? 'ai-free' : undefined
      })
      setImages(res.imageUrl ? [res.imageUrl] : [])
    } catch (e) {
      alert('图片生成失败: ' + e.message)
    } finally {
      setImageLoading(false)
    }
  }

  const goStep = (n) => {
    if (n < 1 || n > 3 || n === step) return
    const doGo = () => setStep(n)
    if (n < step) {
      const willLose = []
      if (n < 2 && step >= 2 && (bodyContent || titles.length > 0)) willLose.push('标题候选与正文')
      if (n < 3 && step >= 3 && (images.length > 0 || batchImgResults.length > 0)) willLose.push('已生成的配图')
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

  const handleDownload = async (url) => {
    try {
      const res = await fetch(url)
      const blob = await res.blob()
      const a = document.createElement('a')
      a.href = URL.createObjectURL(blob)
      a.download = `配图_${Date.now()}.png`
      a.click()
      URL.revokeObjectURL(a.href)
    } catch {
      window.open(url, '_blank')
    }
  }

  return (
    <div className="xhs-create-page">
      {/* Page Header */}
      <div className="page-head">
        <div className="page-title-row">
          <div className="page-title">
            <span className="platform-tag xhs">小</span>
            <h2>小红书创作</h2>
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

      {/* Step 1: Requirements */}
      {step === 1 && (
        <div className="step-pane">
          <div className="section-card">
            <div className="sc-head">
              <h3>告诉 AI 你想写什么</h3>
                  <span className="desc">填写主题，AI 生成 5 个标题候选</span>
            </div>
            <div className="sc-body">
              <div className="form-row">
                <div className="label">选题主题 <span className="req">*</span></div>
                <div className="control">
                  <input type="text" className="text-field" value={topic} onChange={e => setTopic(e.target.value)} placeholder="例如：医保报销 60% 之后，剩下的谁来兜？" />
                </div>
              </div>

              <div className="form-row">
                <div className="label">主要内容方向</div>
                <div className="control">
                  <textarea className="text-field" value={direction} onChange={e => setDirection(e.target.value)} placeholder="（选填）告诉 AI 你想强调的角度" />
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
                      <div className="info">
                        <strong>通用专业风格</strong>
                        <span>严谨、引经据典、数据驱动</span>
                      </div>
                    </div>
                    <div className={`style-option ${styleOption === 'warm' ? 'selected' : ''}`} onClick={() => setStyleOption('warm')}>
                      <div className="radio"></div>
                      <div className="info">
                        <strong>通用温暖风格</strong>
                        <span>亲切、生活化、像跟朋友聊天</span>
                      </div>
                    </div>
                    <div className={`style-option ${styleOption === 'sharp' ? 'selected' : ''}`} onClick={() => setStyleOption('sharp')}>
                      <div className="radio"></div>
                      <div className="info">
                        <strong>通用犀利风格</strong>
                        <span>痛点直击、节奏快、容易爆</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Step 2: Edit Content */}
      {step === 2 && (
        <div className="step-pane">
          <div className="edit-grid">
            <div className="edit-main">
              {/* 第一部分：选择标题 */}
              <div className="section-card">
                <div className="sc-head">
                  <h3>标题候选</h3>
                  <span className="desc">挑一个最像爆款的</span>
                </div>
                <div className="sc-body">
                  <div className="title-cards">
                    {titles.map((title, i) => {
                      const typeMatch = title.match(/^\[(\S+)\]\s*/)
                      const typeLabel = typeMatch ? typeMatch[1] : ''
                      const cleanTitle = typeMatch ? title.substring(typeMatch[0].length) : title
                      return (
                        <div key={i} className={`title-card ${selectedTitle === i ? 'selected' : ''}`} onClick={() => setSelectedTitle(i)}>
                          {typeLabel && <span className="style-label">{typeLabel}</span>}
                          <span className="title-text">
                            <ReactMarkdown remarkPlugins={[remarkGfm]}>
                              {cleanTitle.replace(/^\d+[.、]\s*/, '')}
                            </ReactMarkdown>
                          </span>
                          <div className="check">✓</div>
                        </div>
                      )
                    })}
                  </div>
                  {/* 生成正文按钮 */}
                  <div className="title-gen-body-btn" style={{ marginTop: '16px' }}>
                    <button 
                      className="btn-primary" 
                      onClick={handleGenerateBody} 
                      disabled={loading || !titles.length}
                    >
                      {loading ? '生成中…' : '根据这个标题生成正文'}
                    </button>
                  </div>
                </div>
              </div>

              {/* 第二部分：编辑正文（只有在生成了正文之后才显示） */}
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
                      />
                    ) : (
                      <div className="body-editor markdown-body">
                        <ReactMarkdown remarkPlugins={[remarkGfm]}>
                          {bodyContent}
                        </ReactMarkdown>
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>

            <div className="side-panel">
              <div className="side-card">
                <div className="side-card-head">— Info · 写作信息</div>
                <div className="side-card-body">
                  <div className="info-row"><span className="k">写作风格</span><span className="v">{styleOption === 'personal' ? '我的个人风格' : styleOption === 'professional' ? '通用专业风格' : styleOption === 'warm' ? '通用温暖风格' : styleOption === 'sharp' ? '通用犀利风格' : '默认'}</span></div>
                  <div className="info-row"><span className="k">险种</span><span className="v">{insuranceTypes.join(', ')}</span></div>
                  <div className="info-row"><span className="k">目标人群</span><span className="v">{audiences.join(', ')}</span></div>
                  <div className="info-row"><span className="k">字数</span><span className="v serif">{bodyContent.length}</span></div>
                </div>
              </div>

              {citations.length > 0 && (
                <div className="side-card">
                  <div className="side-card-head">— Citations · 引用来源</div>
                  <div className="side-card-body">
                    <div className="cite-list">
                      {citations.map((cite, i) => (
                        <div key={i} className="cite-row">
                          <span className="num">[{cite.index}]</span>
                          <span>{cite.title || '爆款样本 ' + cite.index}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              )}

              {/* 如果没有正文，显示重新生成标题；否则显示重新生成正文 */}
              {!bodyContent ? (
                <button className="regen-btn" onClick={handleGenerate}>
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
                  <span>重新生成标题</span>
                  <span className="credits-tip">−3 积分</span>
                </button>
              ) : (
                <button className="regen-btn" onClick={handleGenerateBody}>
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
                  <span>重新生成正文</span>
                  <span className="credits-tip">−2 积分</span>
                </button>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Step 3: Images */}
      {step === 3 && (
        <div className="step-pane">
          <div className="step-3-layout">
            {/* ── 模式选择 ── */}
            {!imgMode && (
              <div className="img-mode-select">
                <h3 style={{ textAlign: 'center', marginBottom: 24, fontFamily: 'var(--serif)' }}>选择配图方式</h3>
                <div className="img-mode-cards">
                  <div className="img-mode-card" onClick={() => setImgMode('single')}>
                    <div className="mode-icon">
                      <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/></svg>
                    </div>
                    <h4>单张配图</h4>
                    <p>AI 根据标题生成一张精选配图，可选模板风格</p>
                  </div>
                  <div className="img-mode-card" onClick={() => setImgMode('batch')}>
                    <div className="mode-icon">
                      <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
                    </div>
                    <h4>批量配图</h4>
                    <p>根据正文内容智能拆解场景，批量生成 1-9 张配图</p>
                  </div>
                </div>
              </div>
            )}

            {/* ── 单张配置 ── */}
            {imgMode === 'single' && !(imageLoading || images.length > 0) && (
              <div className="section-card">
                <div className="sc-head">
                  <button className="btn-back-sm" onClick={() => setImgMode(null)}>← 返回</button>
                  <h3>单张配图</h3>
                </div>
                <div className="sc-body">
                  <div className="template-options-simple">
                    {imageTemplates.map((template, i) => (
                      <div key={template.id} className={`template-card-simple ${selectedTemplate === i ? 'selected' : ''}`}
                        onClick={() => { setSelectedTemplate(i); setUseAIFree(false) }}>
                        {template.thumbnail && (
                          <div className="template-thumb-wrap tpl-thumb-hover">
                            <img src={template.thumbnail} alt={template.name} className="template-preview-simple" />
                            <div className="tpl-hover-overlay">
                              <button className="ov-btn" onClick={e => { e.stopPropagation(); setLightboxUrl(template.thumbnail) }}>
                                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7"/></svg>
                                <span>放大</span>
                              </button>
                              <button className="ov-btn" onClick={e => { e.stopPropagation(); setSelectedTemplate(i); setUseAIFree(false) }}>
                                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><polyline points="20 6 9 17 4 12"/></svg>
                                <span>选择</span>
                              </button>
                            </div>
                          </div>
                        )}
                        <span className="template-label">{template.name || `模板 ${i + 1}`}</span>
                      </div>
                    ))}
                    <div className={`template-card-simple ${useAIFree ? 'selected' : ''}`}
                      onClick={() => { setUseAIFree(true); setSelectedTemplate(null) }}>
                      <div className="template-thumb-wrap tpl-thumb-hover">
                        <div className="template-preview-simple ai-free-placeholder">
                          <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2"><path d="M12 2L13.5 8.5L20 10L13.5 11.5L12 18L10.5 11.5L4 10L10.5 8.5L12 2Z"/></svg>
                        </div>
                        <div className="tpl-hover-overlay">
                          <button className="ov-btn" onClick={e => { e.stopPropagation(); setUseAIFree(true); setSelectedTemplate(null) }}>
                            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><polyline points="20 6 9 17 4 12"/></svg>
                            <span>选择</span>
                          </button>
                        </div>
                      </div>
                      <span className="template-label">自由发挥</span>
                    </div>
                  </div>
                  <div className="generate-image-buttons" style={{ display: 'flex', gap: 10, flexDirection: 'column', marginTop: 16 }}>
                    <button className="btn-primary btn-large" onClick={handleGenerateImageHiAPI}
                      disabled={(selectedTemplate == null && !useAIFree) || imageLoading}>
                      {imageLoading ? '生成中…' : '使用 HiAPI 生成'}
                    </button>
                    <button className="btn-primary btn-large" style={{ background: '#6366F1' }} onClick={handleGenerateImageSeedream}
                      disabled={(selectedTemplate == null && !useAIFree) || imageLoading}>
                      {imageLoading ? '生成中…' : '使用 Seedream 生成'}
                    </button>
                  </div>
                </div>
              </div>
            )}

            {/* ── 批量配置 ── */}
            {imgMode === 'batch' && !(batchImgLoading || batchImgResults.length > 0) && (
              <div className="section-card">
                <div className="sc-head">
                  <button className="btn-back-sm" onClick={() => setImgMode(null)}>← 返回</button>
                  <h3>批量配图</h3>
                </div>
                <div className="sc-body">
                  <div className="form-row" style={{ marginBottom: 12 }}>
                    <div className="label">张数</div>
                    <div className="control">
                      <div className="chip-group">
                        {COUNT_OPTIONS.map(n => (
                          <span key={n} className={`chip ${batchImgCount === n ? 'selected' : ''}`} onClick={() => setBatchImgCount(n)}>{n} 张</span>
                        ))}
                      </div>
                    </div>
                  </div>
                  <div className="form-row" style={{ marginBottom: 16 }}>
                    <div className="label">比例</div>
                    <div className="control">
                      <div className="chip-group">
                        {RATIO_OPTIONS.map(opt => (
                          <span key={opt.value} className={`chip ${batchImgRatio === opt.value ? 'selected' : ''}`} onClick={() => setBatchImgRatio(opt.value)}>{opt.label}</span>
                        ))}
                      </div>
                    </div>
                  </div>
                  {batchImgError && <div className="kb-error" style={{ marginBottom: 12 }}>{batchImgError}</div>}
                  <button className="btn-primary btn-large" style={{ width: '100%', background: '#e84545' }}
                    onClick={handleBatchGenerateImages} disabled={batchImgLoading || !bodyContent.trim()}>
                    {batchImgLoading ? '制图耗时较长，请耐心等待…' : `AI 智能拆解，批量生成 ${batchImgCount} 张`}
                  </button>
                </div>
              </div>
            )}

            {/* ── 加载 / 结果区 ── */}
            {(imageLoading || batchImgLoading || images.length > 0 || batchImgResults.length > 0) && (
              <div className="section-card">
                <div className="sc-head">
                  <button className="btn-back-sm" onClick={() => {
                    if (batchImgLoading || imageLoading) return
                    setImgMode(null)
                  }}>← 返回</button>
                  <h3>{imgMode === 'batch' ? '批量配图结果' : '配图结果'}</h3>
                  <span className="desc">{batchImgLoading ? '制图耗时较长，请耐心等待…' : imageLoading ? '生成中…' : ''}</span>
                </div>
                <div className="sc-body">
                  {/* 骨架加载 */}
                  {(batchImgLoading || imageLoading) && batchImgResults.length === 0 && images.length === 0 && (
                    <div className={`batch-image-grid count-${imgMode === 'batch' ? batchImgCount : 1} ratio-${batchImgRatio.replace(':', '-')}`}>
                      {Array.from({ length: imgMode === 'batch' ? batchImgCount : 1 }).map((_, i) => (
                        <div key={`skel-${i}`} className="batch-image-item skeleton-item">
                          <div className="batch-thumb skeleton-thumb" />
                        </div>
                      ))}
                    </div>
                  )}

                  {/* 批量结果 */}
                  {batchImgResults.length > 0 && (
                    <div className={`batch-image-grid count-${batchImgResults.length} ratio-${batchImgRatio.replace(':', '-')}`}>
                      {batchImgResults.map((item) => (
                        <div key={item.index} className={`batch-image-item ${batchImgLoading ? 'regenerating' : ''}`}>
                          {item.imageUrl ? (
                            <div className="batch-thumb-wrap" style={{ position: 'relative' }}>
                              {item.index === 1 && <span className="cover-badge">★ 首图</span>}
                              <a href={item.imageUrl} target="_blank" rel="noreferrer" onClick={(e) => { e.preventDefault(); setLightboxUrl(item.imageUrl) }}>
                                <img src={item.imageUrl} alt={`配图${item.index}`} className="batch-thumb" />
                              </a>
                              <div className="image-hover-info">
                                <div className="hover-actions">
                                  <button className="ov-btn" onClick={(e) => { e.stopPropagation(); setLightboxUrl(item.imageUrl) }}><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7"/></svg><span>放大</span></button>
                                  <button className="ov-btn" onClick={(e) => { e.stopPropagation(); handleRegenOneImage(item.index - 1, item.description || '') }} disabled={regenLoadingIndex === item.index - 1}>
                                    {regenLoadingIndex === item.index - 1 ? '…' : (<><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg><span>重做</span></>)}
                                  </button>
                                  <button className="ov-btn" onClick={(e) => { e.stopPropagation(); handleDownload(item.imageUrl) }}><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg><span>下载</span></button>
                                </div>
                              </div>
                              {batchImgLoading && <div className="regen-spinner" />}
                            </div>
                          ) : (
                            <div className="batch-thumb batch-thumb-error"><span>生成失败</span>{item.error && <small>{item.error}</small>}</div>
                          )}
                        </div>
                      ))}
                    </div>
                  )}

                  {/* 单张结果 */}
                  {!batchImgLoading && batchImgResults.length === 0 && images.length > 0 && (
                    <div className="image-grid">
                      {images.map((url, i) => (
                        <div key={i} className={`image-card ${i === 0 ? 'cover' : ''}`}>
                          <img src={url} alt={`配图 ${i + 1}`} />
                          <div className="image-card-actions">
                            <button className="img-action-btn" onClick={() => setLightboxUrl(url)} title="放大查看">
                              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7"/></svg>
                            </button>
                            <button className="img-action-btn" onClick={() => handleDownload(url)} title="下载图片">
                              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                            </button>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>

          {/* 底部完成区域 */}
          {(images.length > 0 || batchImgResults.length > 0) && (
            <div className="done-section">
              <div className="l">
                <div className="done-icon">✓</div>
                <div>
                  <h4>这条小红书已就绪</h4>
                  <div className="desc">{titles.length} 个标题候选 · {bodyContent.length} 字正文 · {batchImgResults.length || images.length} 张配图</div>
                </div>
              </div>
              <div className="r">
                <button className="btn-done" onClick={() => {
                  const plain = stripMd(bodyContent)
                  navigator.clipboard?.writeText(plain)
                }}>复制全文</button>
                <button className="btn-done primary" onClick={async () => {
                  const selectedTitleText = (titles[selectedTitle] || '').replace(/^\[\S+\]\s*/, '').replace(/^\d+[.、]\s*/, '')
                  const plainBody = stripMd(bodyContent)
                  const txtContent = selectedTitleText + '\n\n' + plainBody
                  // 下载 txt
                  const blob = new Blob([txtContent], { type: 'text/plain;charset=utf-8' })
                  const a = document.createElement('a')
                  a.href = URL.createObjectURL(blob)
                  a.download = (selectedTitleText || '小红书文案').slice(0, 20) + '.txt'
                  a.click()
                  URL.revokeObjectURL(a.href)
                  // 下载图片
                  const imgs = batchImgResults.length > 0 ? batchImgResults : images.map((url, i) => ({ index: i + 1, imageUrl: url }))
                  const nameMap = { 1: '首图', 2: '图一', 3: '图二', 4: '图三', 5: '图四', 6: '图五', 7: '图六', 8: '图七', 9: '图八' }
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

      {/* 参考资料弹窗 */}
      <RefMaterialModal show={showRefModal} onClose={() => setShowRefModal(false)}
        onAdd={(ref) => { setRefMaterials(prev => [...prev, ref]) }} />

      <ConfirmModal {...confirmProps} />

      {/* Lightbox */}
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

      {/* Footer Actions Bar */}
      <div className="actions-bar">
        <div className="l">
          {step > 1 && (
            <button className="btn-back" onClick={() => goStep(step - 1)}>
              ← {step === 2 ? '重做需求' : '回到改文案'}
            </button>
          )}
        </div>
        <div className="r">
          <span className="meta">本步骤消耗 <b>{step === 1 ? 3 : step === 2 && !bodyContent ? 2 : 5}</b> 积分</span>
          {step === 1 && (
            <button className="btn-primary-large" onClick={handleGenerate} disabled={loading || !topic.trim()}>
              <span>{loading ? '生成中…' : '生成标题候选'}</span>
              <span className="arr">→</span>
            </button>
          )}
          {step === 2 && (
            <button 
              className="btn-primary-large" 
              onClick={bodyContent ? () => setStep(3) : undefined} 
              disabled={(!bodyContent && step === 2) || loading}
            >
              <span>{!bodyContent ? '请先生成正文' : '下一步：配图'}</span>
              {bodyContent && <span className="arr">→</span>}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
