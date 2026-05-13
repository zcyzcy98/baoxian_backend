import { useState, useEffect } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { callAgent, generateImage, generateSeedreamImage, fetchImageTemplates, generateXhsBatchImages, regenOneImage, parseRefMaterial } from '../api'
import './XhsCreatePage.css'

const COUNT_OPTIONS = [1, 3, 6, 9]
const RATIO_OPTIONS = [
  { value: '3:4', label: '竖版 3:4' },
  { value: '1:1', label: '方形 1:1' },
]

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

  const toggleChip = (list, setList, value) => {
    setList(prev => prev.includes(value) ? prev.filter(v => v !== value) : [...prev, value])
  }

  const handleGenerate = async () => {
    if (!topic.trim()) return
    setLoading(true)
    try {
      // 只生成标题
      const refText = refMaterials.map(r => r.text).filter(Boolean).join('\n---\n')
      const titleRes = await callAgent('title', { topic, style: direction, references: refText || undefined }, 'rag-xhs')
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
        style: direction,
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

  const handleAddRef = (type) => {
    if (type === 'file') {
      const input = document.createElement('input')
      input.type = 'file'
      input.accept = '.pdf,.docx'
      input.onchange = async (e) => {
        const file = e.target.files?.[0]
        if (!file) return
        try {
          const result = await parseRefMaterial(file)
          setRefMaterials(prev => [...prev, {
            name: result.fileName || file.name,
            type: result.fileType?.includes('pdf') ? 'pdf' : 'doc',
            text: result.extractedText || '',
          }])
        } catch (err) {
          alert('解析参考材料失败: ' + err.message)
        }
      }
      input.click()
    } else if (type === 'link') {
      const url = prompt('粘贴参考链接:')
      if (!url) return
      setRefMaterials(prev => [...prev, {
        name: url.length > 50 ? url.substring(0, 50) + '...' : url,
        type: 'link',
        text: '参考链接: ' + url,
      }])
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
    setBatchImgResults([])
    setImages([])
    try {
      const data = await generateXhsBatchImages(bodyContent, batchImgCount, batchImgRatio, 'hiapi')
      setBatchImgResults(data)
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
    if (n >= 1 && n <= 3) setStep(n)
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
                  {refMaterials.length > 0 && (
                    <div className="refs-list">
                      {refMaterials.map((ref, i) => (
                        <div key={i} className="ref-item">
                          <div className={`ref-ico ${ref.type}`}>
                            {ref.type === 'pdf' ? 'PDF' : ref.type === 'doc' ? 'DOC' : '🔗'}
                          </div>
                          <span className="ref-name">{ref.name}</span>
                          <button className="ref-remove" onClick={() => handleRemoveRef(i)}>×</button>
                        </div>
                      ))}
                    </div>
                  )}
                  <div className="ref-actions">
                    <button className="btn-add-ref" onClick={() => handleAddRef('file')}>
                      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                      <span>添加文件</span>
                    </button>
                    <button className="btn-add-ref" onClick={() => handleAddRef('link')}>
                      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                      <span>添加链接</span>
                    </button>
                    <span className="ref-hint">支持 PDF / DOCX / 链接 · AI 会读完再写</span>
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
                        <strong>我的个人风格 <span className="ver-tag">V3</span></strong>
                        <span>基于 12 篇素材训练 · "温暖、克制、擅讲案例"</span>
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
                    <div className="view-toggle">
                      <span className={`toggle-btn ${viewMode === 'preview' ? 'active' : ''}`} onClick={() => setViewMode('preview')}>预览</span>
                      <span className={`toggle-btn ${viewMode === 'edit' ? 'active' : ''}`} onClick={() => setViewMode('edit')}>编辑</span>
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
                  <div className="info-row"><span className="k">写作风格</span><span className="v">林老师 V3</span></div>
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
            {/* 左侧：选择配图方式 */}
            <div className="section-card step-3-left">
              <div className="sc-head">
                <h3>选择配图方式</h3>
              </div>
              <div className="sc-body">
                {/* 模板 + AI 自由创作网格 */}
                <div className="template-options-simple">
                  {imageTemplates.map((template, i) => (
                    <div
                      key={template.id}
                      className={`template-card-simple ${selectedTemplate === i ? 'selected' : ''}`}
                      onClick={() => {
                        setSelectedTemplate(i)
                        setUseAIFree(false)
                      }}
                    >
                      {template.thumbnail && (
                        <div className="template-thumb-wrap">
                          <img src={template.thumbnail} alt={template.name} className="template-preview-simple" />
                          <button
                            className="thumb-zoom-btn"
                            onClick={e => { e.stopPropagation(); setLightboxUrl(template.thumbnail) }}
                            title="放大预览"
                          >
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7"/></svg>
                          </button>
                        </div>
                      )}
                      <span className="template-label">{template.name || `模板 ${i + 1}`}</span>
                    </div>
                  ))}
                  {/* AI自由创作选项 */}
                  <div
                    className={`template-card-simple ${useAIFree ? 'selected' : ''}`}
                    onClick={() => {
                      setUseAIFree(true)
                      setSelectedTemplate(null)
                    }}
                  >
                    <div className="ai-free-icon-simple">
                      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                        <path d="M12 2L13.5 8.5L20 10L13.5 11.5L12 18L10.5 11.5L4 10L10.5 8.5L12 2Z" />
                      </svg>
                      <span>AI 创作</span>
                    </div>
                    <span className="template-label">自由发挥</span>
                  </div>
                </div>

                {/* 两个生图按钮 */}
                <div className="generate-image-buttons" style={{ display: 'flex', gap: '10px', flexDirection: 'column' }}>
                  <button
                    className="btn-primary btn-large"
                    onClick={handleGenerateImageHiAPI}
                    disabled={(!selectedTemplate && !useAIFree) || imageLoading}
                  >
                    {imageLoading ? '生成中…' : '使用 HiAPI 生成'}
                  </button>
                  <button
                    className="btn-primary btn-large"
                    style={{ background: '#6366F1' }}
                    onClick={handleGenerateImageSeedream}
                    disabled={(!selectedTemplate && !useAIFree) || imageLoading}
                  >
                    {imageLoading ? '生成中…' : '使用 Seedream 生成'}
                  </button>
                </div>

                {/* 批量生成配图 */}
                <div className="batch-img-section">
                  <div className="batch-img-divider">
                    <span>或批量生成配图</span>
                  </div>
                  <div className="form-row" style={{ marginBottom: 8 }}>
                    <div className="label" style={{ fontSize: 13, color: 'var(--ink-3)' }}>张数</div>
                    <div className="control">
                      <div className="chip-group">
                        {COUNT_OPTIONS.map(n => (
                          <span key={n} className={`chip ${batchImgCount === n ? 'selected' : ''}`} onClick={() => setBatchImgCount(n)}>{n} 张</span>
                        ))}
                      </div>
                    </div>
                  </div>
                  <div className="form-row" style={{ marginBottom: 12 }}>
                    <div className="label" style={{ fontSize: 13, color: 'var(--ink-3)' }}>比例</div>
                    <div className="control">
                      <div className="chip-group">
                        {RATIO_OPTIONS.map(opt => (
                          <span key={opt.value} className={`chip ${batchImgRatio === opt.value ? 'selected' : ''}`} onClick={() => setBatchImgRatio(opt.value)}>{opt.label}</span>
                        ))}
                      </div>
                    </div>
                  </div>
                  {batchImgError && <div className="kb-error" style={{ marginBottom: 8 }}>{batchImgError}</div>}
                  <button
                    className="btn-primary btn-large"
                    style={{ width: '100%', background: '#e84545' }}
                    onClick={handleBatchGenerateImages}
                    disabled={batchImgLoading || !bodyContent.trim()}
                  >
                    {batchImgLoading
                      ? `生成中… (并行生成 ${batchImgCount} 张，约 30 秒)`
                      : `AI 智能拆解，批量生成 ${batchImgCount} 张`}
                  </button>
                </div>
              </div>
            </div>

            {/* 右侧：展示生成的图片 */}
            <div className="section-card step-3-right">
              <div className="sc-head">
                <h3>生成结果</h3>
              </div>
              <div className="sc-body">
                {batchImgResults.length > 0 ? (
                  <div className="batch-image-grid">
                    {batchImgResults.map((item) => (
                      <div key={item.index} className="batch-image-item">
                        {item.imageUrl ? (
                          <div style={{ position: 'relative' }}>
                            <a href={item.imageUrl} target="_blank" rel="noreferrer">
                              <img src={item.imageUrl} alt={`配图${item.index}`} className="batch-thumb" />
                            </a>
                            <div className="image-card-actions">
                              <button className="img-action-btn" onClick={() => setLightboxUrl(item.imageUrl)} title="放大查看">
                                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7"/></svg>
                              </button>
                              <button className="img-action-btn" onClick={() => handleDownload(item.imageUrl)} title="下载图片">
                                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                              </button>
                              <button
                                className="img-action-btn regen"
                                onClick={() => handleRegenOneImage(item.index - 1, item.description || '')}
                                disabled={regenLoadingIndex === item.index - 1}
                                title="重新生成此图"
                              >
                                {regenLoadingIndex === item.index - 1 ? (
                                  <span style={{fontSize:'11px'}}>⏳</span>
                                ) : (
                                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
                                )}
                              </button>
                            </div>
                          </div>
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
                ) : images.length > 0 ? (
                  <div className="image-grid">
                    {images.map((url, i) => (
                      <div key={i} className={`image-card ${i === 0 ? 'cover' : ''}`}>
                        <img src={url} alt={`配图 ${i + 1}`} />
                        <div className="image-card-actions">
                          <button
                            className="img-action-btn"
                            onClick={() => setLightboxUrl(url)}
                            title="放大查看"
                          >
                            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7"/></svg>
                          </button>
                          <button
                            className="img-action-btn"
                            onClick={() => handleDownload(url)}
                            title="下载图片"
                          >
                            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="no-images">
                    <p>请先在左侧选择配图方式并生成图片</p>
                  </div>
                )}
              </div>
            </div>
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
                <button className="btn-done" onClick={() => navigator.clipboard?.writeText(bodyContent)}>复制全部</button>
                <button className="btn-done primary">保存为草稿</button>
              </div>
            </div>
          )}
        </div>
      )}

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
