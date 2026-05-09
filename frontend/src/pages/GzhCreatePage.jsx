import { useState, useEffect } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { callAgent } from '../api'
import './XhsCreatePage.css'

export default function GzhCreatePage({ topicPrefill, onPrefillConsumed }) {
  const [step, setStep] = useState(1)
  const [loading, setLoading] = useState(false)

  // Step 1 form
  const [topic, setTopic] = useState('')
  const [direction, setDirection] = useState('')
  const [wordCount, setWordCount] = useState('')
  const [insuranceTypes, setInsuranceTypes] = useState([])
  const [audiences, setAudiences] = useState([])
  const [styleOption, setStyleOption] = useState('')

  // Step 2
  const [titles, setTitles] = useState([])
  const [selectedTitle, setSelectedTitle] = useState(0)
  const [bodyContent, setBodyContent] = useState('')
  const [viewMode, setViewMode] = useState('preview')

  useEffect(() => {
    if (topicPrefill) {
      setTopic(topicPrefill.topic || '')
      setDirection(topicPrefill.angle || '')
      onPrefillConsumed?.()
    }
  }, [topicPrefill])

  const wordCountOptions = ['1500', '2000', '2500', '3000', '4000']
  const insuranceOptions = ['医疗险', '重疾险', '寿险', '意外险', '年金险', '储蓄险', '少儿险', '团险']
  const audienceOptions = ['中产', '宝妈', '高净值', '银发族', '单身青年', '创业者', '全职主妇']

  const toggleChip = (list, setList, value) => {
    setList(prev => prev.includes(value) ? prev.filter(v => v !== value) : [...prev, value])
  }

  const handleGenerate = async () => {
    if (!topic.trim()) return
    setLoading(true)
    try {
      const res = await callAgent('gzh-title', { topic, style: direction })
      const lines = (res.content || '').split('\n').filter(l => l.trim())
      setTitles(lines.slice(0, 5))
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
      const res = await callAgent('gzh-text', {
        topic: selectedTitleText,
        style: direction,
        wordCount: parseInt(wordCount),
      })
      setBodyContent(res.content || '')
    } catch (e) {
      alert('生成失败: ' + e.message)
    } finally {
      setLoading(false)
    }
  }

  const goStep = (n) => { if (n >= 1 && n <= 2) setStep(n) }

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
        </div>
        <div className="steps">
          <div className={`step ${step > 1 ? 'done' : ''} ${step === 1 ? 'active' : ''}`} onClick={() => goStep(1)}>
            <div className="dot">{step > 1 ? '✓' : '1'}</div>
            <div className="step-label">提需求</div>
          </div>
          <div className={`step ${step === 2 ? 'active' : ''}`} onClick={() => step >= 2 && goStep(2)}>
            <div className="dot">2</div>
            <div className="step-label">改文案</div>
          </div>
        </div>
      </div>

      {/* Step 1 */}
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
                  <input
                    type="text"
                    className="text-field"
                    value={topic}
                    onChange={e => setTopic(e.target.value)}
                    placeholder="例如：医保改革落地，30+ 群体到底要不要重新规划？"
                  />
                </div>
              </div>

              <div className="form-row">
                <div className="label">主要内容方向</div>
                <div className="control">
                  <textarea
                    className="text-field"
                    value={direction}
                    onChange={e => setDirection(e.target.value)}
                    placeholder="（选填）告诉 AI 你想强调的角度"
                  />
                </div>
              </div>

              <div className="form-row">
                <div className="label">目标字数 <span className="req">*</span></div>
                <div className="control">
                  <div className="chip-group">
                    {wordCountOptions.map(opt => (
                      <span
                        key={opt}
                        className={`chip ${wordCount === opt ? 'selected' : ''}`}
                        onClick={() => setWordCount(opt)}
                      >
                        {parseInt(opt).toLocaleString()} 字
                      </span>
                    ))}
                  </div>
                  <div style={{ fontSize: '11px', color: 'var(--ink-3)', marginTop: 8 }}>公众号长文建议 1,500–4,000 字</div>
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
                    {[
                      ['personal',     '我的个人风格',   '基于 12 篇素材训练 · "温暖、克制、擅讲案例"'],
                      ['professional', '通用专业风格',   '严谨、引经据典、数据驱动'],
                      ['warm',         '通用温暖风格',   '亲切、生活化、像跟朋友聊天'],
                      ['sharp',        '通用犀利风格',   '痛点直击、节奏快、容易爆'],
                    ].map(([key, name, desc]) => (
                      <div key={key} className={`style-option ${styleOption === key ? 'selected' : ''}`} onClick={() => setStyleOption(key)}>
                        <div className="radio"></div>
                        <div className="info">
                          <strong>{name} {key === 'personal' && <span className="ver-tag">V3</span>}</strong>
                          <span>{desc}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Step 2 */}
      {step === 2 && (
        <div className="step-pane">
          <div className="edit-grid">
            <div className="edit-main">
              {/* 标题选择 */}
              <div className="section-card">
                <div className="sc-head">
                  <h3>标题候选</h3>
                  <span className="desc">挑一个最适合公众号的</span>
                </div>
                <div className="sc-body">
                  <div className="title-cards">
                    {titles.map((title, i) => (
                      <div
                        key={i}
                        className={`title-card ${selectedTitle === i ? 'selected' : ''}`}
                        onClick={() => setSelectedTitle(i)}
                      >
                        <span className="title-text">
                          <ReactMarkdown remarkPlugins={[remarkGfm]}>
                            {title.replace(/^\d+[.、]\s*/, '')}
                          </ReactMarkdown>
                        </span>
                        <div className="check">✓</div>
                      </div>
                    ))}
                  </div>
                  <div style={{ marginTop: '16px' }}>
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

              {/* 正文编辑（生成后出现） */}
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
                        style={{ minHeight: 520 }}
                      />
                    ) : (
                      <div className="body-editor markdown-body" style={{ minHeight: 520, lineHeight: 2, fontSize: '15.5px' }}>
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
              {bodyContent && (
                <div className="word-meter">
                  <div className="wm-row">
                    <span className="wm-num">{bodyContent.length}</span>
                    <span className="wm-target">目标 <b>{parseInt(wordCount).toLocaleString()}</b> 字</span>
                  </div>
                  <div className="wm-track">
                    <div className="wm-fill" style={{ width: `${Math.min(100, (bodyContent.length / parseInt(wordCount)) * 100)}%` }}></div>
                  </div>
                </div>
              )}

              <div className="side-card">
                <div className="side-card-head">— Info · 写作信息</div>
                <div className="side-card-body">
                  <div className="info-row"><span className="k">写作风格</span><span className="v">林老师 V3</span></div>
                  <div className="info-row"><span className="k">险种</span><span className="v">{insuranceTypes.join(', ')}</span></div>
                  <div className="info-row"><span className="k">目标人群</span><span className="v">{audiences.join(', ')}</span></div>
                  <div className="info-row"><span className="k">目标字数</span><span className="v serif">{parseInt(wordCount).toLocaleString()}</span></div>
                  {bodyContent && <div className="info-row"><span className="k">实际字数</span><span className="v serif">{bodyContent.length}</span></div>}
                </div>
              </div>

              {!bodyContent ? (
                <button className="regen-btn" onClick={handleGenerate}>
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
                  <span>重新生成标题</span>
                  <span className="credits-tip">−3 积分</span>
                </button>
              ) : (
                <>
                  <button className="regen-btn" onClick={handleGenerateBody}>
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
                    <span>重新生成正文</span>
                    <span className="credits-tip">−8 积分</span>
                  </button>
                  <button className="regen-btn" onClick={() => navigator.clipboard?.writeText(bodyContent)} style={{ background: 'var(--clay)', marginTop: 0 }}>
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                    <span>复制全文</span>
                  </button>
                </>
              )}
            </div>
          </div>
        </div>
      )}

      <div className="actions-bar">
        <div className="l">
          {step > 1 && (
            <button className="btn-back" onClick={() => goStep(step - 1)}>
              ← {step === 2 ? '重做需求' : '返回上一步'}
            </button>
          )}
        </div>
        <div className="r">
          <span className="meta">本步骤消耗 <b>{step === 1 ? 3 : !bodyContent ? 8 : 8}</b> 积分</span>
          {step === 1 && (
            <button className="btn-primary-large" onClick={handleGenerate} disabled={loading || !topic.trim()}>
              <span>{loading ? '生成中…' : '生成标题候选'}</span>
              <span className="arr">→</span>
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
