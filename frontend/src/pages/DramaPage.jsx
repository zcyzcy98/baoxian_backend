import { useEffect, useRef, useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { callAgent, generateSeedanceVideo } from '../api'
import { copyToClipboard, stripMarkdown } from '../utils/markdown'
import './DramaPage.css'

async function uploadAvatar(file) {
  const form = new FormData()
  form.append('file', file)
  const res = await fetch('/api/avatar/upload', { method: 'POST', body: form })
  const data = await res.json()
  if (!res.ok || data.error) throw new Error(data.error || '上传失败')
  return data.url
}

const PLATFORMS = [
  { id: 'douyin', label: '抖音' },
  { id: 'xhs', label: '小红书' },
  { id: 'video', label: '视频号' },
]

// 从文本中提取链接
function extractUrlFromText(text) {
  if (!text) return ''
  const urlMatch = text.match(/https?:\/\/[^\s<>"{}|\\^`\[\]]+/)
  return urlMatch ? urlMatch[0] : ''
}

const STEPS_CREATE = [
  { id: 1, label: '选题与平台' },
  { id: 2, label: '剧本与分镜' },
  { id: 3, label: '视频生成' },
]

const STEPS_RIP = [
  { id: 1, label: '视频链接' },
  { id: 2, label: '爆款分析' },
  { id: 3, label: '仿写剧本' },
  { id: 4, label: '视频生成' },
]

/**
 * 视频创作页 - 同时支持:
 *   - create (空白创作): 输入主题 → 脚本 → 分镜 → Seedance 口播生成
 *   - rip (爆款仿写): 给一个视频链接 → 分析爆款原因 → 仿写剧本 → Seedance 口播生成
 */
export default function DramaPage({ topicPrefill, onPrefillConsumed, mode = 'create' }) {
  const [activeMode, setActiveMode] = useState(mode) // 'create' or 'rip'
  const [step, setStep] = useState(1)

  // 当mode参数变化时（比如从侧边栏切换），同步更新activeMode
  useEffect(() => {
    setActiveMode(mode)
    setStep(1)
  }, [mode])
  
  // 空白创作模式
  const [topic, setTopic] = useState('')
  const [direction, setDirection] = useState('')
  const [platform, setPlatform] = useState('douyin')
  const [duration, setDuration] = useState('60秒')
  
  // 爆款仿写模式
  const [refUrl, setRefUrl] = useState('')
  const [analysisResult, setAnalysisResult] = useState('')
  const [analyzing, setAnalyzing] = useState(false)
  
  // 通用
  const [scriptTab, setScriptTab] = useState('full')
  const [script, setScript] = useState('')
  const [storyboard, setStoryboard] = useState('')
  const [genLoading, setGenLoading] = useState(false)
  const [genError, setGenError] = useState('')

  const [videoLoading, setVideoLoading] = useState(false)
  const [seedanceSegments, setSeedanceSegments] = useState([])
  const [characterImageUrl, setCharacterImageUrl] = useState('')
  const [characterPreview, setCharacterPreview] = useState(null)
  const [characterUploading, setCharacterUploading] = useState(false)
  const [backgroundImageUrl, setBackgroundImageUrl] = useState('')
  const [backgroundPreview, setBackgroundPreview] = useState(null)
  const [backgroundUploading, setBackgroundUploading] = useState(false)
  const charFileRef = useRef(null)
  const bgFileRef = useRef(null)

  // 选题广场预填
  useEffect(() => {
    if (topicPrefill?.topic) {
      setTopic(topicPrefill.topic)
      if (topicPrefill.angle) setDirection(topicPrefill.angle)
      onPrefillConsumed?.()
    }
  }, [topicPrefill])

  // 处理链接输入
  const handleUrlChange = (e) => {
    const inputText = e.target.value
    const extractedUrl = extractUrlFromText(inputText)
    setRefUrl(extractedUrl || inputText)
  }

  // 分析爆款原因
  const handleAnalyzeVideo = async () => {
    if (!refUrl.trim()) return
    setAnalyzing(true)
    setGenError('')
    try {
      const r = await callAgent('video-to-script', {
        videoUrl: refUrl.trim(),
        outputFormat: 'analysis',
      })
      setAnalysisResult(r?.content || '')
      setStep(2)
    } catch (err) {
      setGenError(err.message)
    } finally {
      setAnalyzing(false)
    }
  }

  // 仿写剧本
  const handleRewriteScript = async () => {
    if (!analysisResult) return
    setGenLoading(true)
    setGenError('')
    try {
      const [sc, sb] = await Promise.all([
        callAgent('video-script', {
          topic: '爆款仿写',
          style: `参考以下视频的爆款原因进行仿写：${analysisResult.slice(0, 1000)}`,
          duration,
        }),
        callAgent('video-storyboard', {
          topic: '爆款仿写',
          style: `参考以下视频的爆款原因进行仿写：${analysisResult.slice(0, 1000)}`,
          duration,
        }),
      ])
      setScript(sc?.content || '')
      setStoryboard(sb?.content || '')
      setStep(3)
    } catch (err) {
      setGenError(err.message)
    } finally {
      setGenLoading(false)
    }
  }

  const handleGenScript = async () => {
    if (!topic.trim()) return
    setGenLoading(true)
    setGenError('')
    try {
      const [sc, sb] = await Promise.all([
        callAgent('video-script', { topic, style: direction, duration }),
        callAgent('video-storyboard', { topic, style: direction, duration }),
      ])
      setScript(sc?.content || '')
      setStoryboard(sb?.content || '')
      setStep(2)
    } catch (err) {
      setGenError(err.message)
    } finally {
      setGenLoading(false)
    }
  }

  const handleGenStoryboard = async () => {
    if (!script.trim()) return
    setGenLoading(true)
    setGenError('')
    try {
      const r = await callAgent('video-storyboard', { topic, style: direction, duration, script })
      setStoryboard(r?.content || '')
      setScriptTab('storyboard')
    } catch (err) {
      setGenError(err.message)
    } finally {
      setGenLoading(false)
    }
  }

  const handleUploadImage = async (type, file) => {
    if (!file) return
    const preview = URL.createObjectURL(file)
    if (type === 'character') {
      setCharacterPreview(preview)
      setCharacterUploading(true)
      try {
        const url = await uploadAvatar(file)
        setCharacterImageUrl(url)
      } catch (err) {
        alert('上传失败：' + err.message)
        setCharacterPreview(null)
        setCharacterImageUrl('')
      } finally {
        setCharacterUploading(false)
      }
    } else {
      setBackgroundPreview(preview)
      setBackgroundUploading(true)
      try {
        const url = await uploadAvatar(file)
        setBackgroundImageUrl(url)
      } catch (err) {
        alert('上传失败：' + err.message)
        setBackgroundPreview(null)
        setBackgroundImageUrl('')
      } finally {
        setBackgroundUploading(false)
      }
    }
  }

  const handleGenVideo = async () => {
    if (!script.trim()) return
    if (!characterImageUrl) {
      setGenError('请先上传人物照片（必须）')
      return
    }
    setVideoLoading(true)
    setGenError('')
    setSeedanceSegments([])
    try {
      const r = await generateSeedanceVideo({
        script,
        characterImageUrl,
        backgroundImageUrl: backgroundImageUrl || null,
        style: direction,
      })
      setSeedanceSegments(r?.segments || [])
    } catch (err) {
      setGenError(err.message)
    } finally {
      setVideoLoading(false)
    }
  }

  const currentSteps = activeMode === 'create' ? STEPS_CREATE : STEPS_RIP

  return (
    <div className="drama-page">
      <header className="drama-header">
        <div>
          <h2>{mode === 'rip' ? '视频仿做' : '视频创作'}</h2>
          <p className="page-sub">
            {mode === 'rip' ? '分析爆款视频，快速仿写剧本' : '支持空白创作和爆款仿写两种模式'}
          </p>
        </div>
        {mode !== 'rip' && (
          <div className="mode-tabs">
            <button
              className={'mode-tab' + (activeMode === 'create' ? ' is-active' : '')}
              onClick={() => { setActiveMode('create'); setStep(1) }}>
              空白创作
            </button>
            <button
              className={'mode-tab' + (activeMode === 'rip' ? ' is-active' : '')}
              onClick={() => { setActiveMode('rip'); setStep(1) }}>
              爆款仿写
            </button>
          </div>
        )}
      </header>

      <div className="step-bar">
        {currentSteps.map((s) => (
          <div key={s.id}
            className={'step-item' + (step >= s.id ? ' is-done' : '') + (step === s.id ? ' is-active' : '')}
            onClick={() => step > s.id && setStep(s.id)}>
            <div className="step-num">{s.id}</div>
            <div className="step-label">{s.label}</div>
          </div>
        ))}
      </div>

      {/* 空白创作模式 */}
      {activeMode === 'create' && (
        <>
          {step === 1 && (
            <section className="drama-card">
              <div className="form-field">
                <label className="form-label">视频主题 <span className="required">*</span></label>
                <input className="form-input" value={topic}
                  placeholder="例如: 给爸妈买保险的 3 个坑"
                  onChange={(e) => setTopic(e.target.value)} />
              </div>

              <div className="form-field">
                <label className="form-label">内容方向 / 风格</label>
                <textarea className="form-input" rows={2} value={direction}
                  placeholder="例如: 干货 + 故事化, 强调反转 + 数据"
                  onChange={(e) => setDirection(e.target.value)} />
              </div>

              <div className="form-row-2">
                <div className="form-field">
                  <label className="form-label">发布平台</label>
                  <div className="plat-cards">
                    {PLATFORMS.map((p) => (
                      <button key={p.id}
                        className={'plat-card' + (platform === p.id ? ' is-active' : '')}
                        onClick={() => setPlatform(p.id)}>
                        {p.label}
                      </button>
                    ))}
                  </div>
                </div>
                <div className="form-field">
                  <label className="form-label">时长</label>
                  <div className="chip-row">
                    {['30秒', '60秒', '90秒', '3分钟'].map((d) => (
                      <button key={d}
                        className={'chip' + (duration === d ? ' is-active' : '')}
                        onClick={() => setDuration(d)}>{d}</button>
                    ))}
                  </div>
                </div>
              </div>

              {genError && <div className="kb-error">{genError}</div>}

              <div className="step-actions">
                <button className="btn-primary" onClick={handleGenScript}
                  disabled={!topic.trim() || genLoading}>
                  {genLoading ? '生成中…' : '下一步: 生成剧本/分镜 (10 积分)'}
                </button>
              </div>
            </section>
          )}

          {step === 2 && (
            <section className="drama-card">
              <div className="result-head">
                <div className="script-tabs">
                  <button className={'tab' + (scriptTab === 'full' ? ' is-active' : '')}
                    onClick={() => setScriptTab('full')}>完整剧本</button>
                  <button className={'tab' + (scriptTab === 'storyboard' ? ' is-active' : '')}
                    onClick={() => setScriptTab('storyboard')}>拆解分镜</button>
                </div>
                <div className="result-actions">
                  <button className="btn-ghost" onClick={() => setStep(1)}>← 返回</button>
                  <button className="btn-ghost" onClick={() => copyToClipboard(stripMarkdown(scriptTab === 'full' ? script : storyboard))}>复制</button>
                  {scriptTab === 'full' && !storyboard && (
                    <button className="btn-ghost" onClick={handleGenStoryboard} disabled={genLoading}>
                      {genLoading ? '拆解中…' : '拆分镜'}
                    </button>
                  )}
                  <button className="btn-primary" onClick={() => setStep(3)} disabled={!script}>
                    下一步：配置视频 →
                  </button>
                </div>
              </div>

              {genError && <div className="kb-error">{genError}</div>}

              <div className="script-content">
                {scriptTab === 'full' ? (
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>{script || '(空)'}</ReactMarkdown>
                ) : (
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>{storyboard || '点上方"拆分镜"先生成分镜表'}</ReactMarkdown>
                )}
              </div>
            </section>
          )}

          {step === 3 && (
            <section className="drama-card">
              <div className="result-head">
                <h3>视频生成</h3>
                <div className="result-actions">
                  <button className="btn-ghost" onClick={() => setStep(2)}>← 返回</button>
                </div>
              </div>

              {!seedanceSegments.length && !videoLoading && (
                <VideoGenConfig
                  characterPreview={characterPreview} characterUploading={characterUploading}
                  backgroundPreview={backgroundPreview} backgroundUploading={backgroundUploading}
                  charFileRef={charFileRef} bgFileRef={bgFileRef}
                  onUpload={handleUploadImage}
                  onClearChar={() => { setCharacterPreview(null); setCharacterImageUrl(''); if (charFileRef.current) charFileRef.current.value = '' }}
                  onClearBg={() => { setBackgroundPreview(null); setBackgroundImageUrl(''); if (bgFileRef.current) bgFileRef.current.value = '' }}
                  onGenerate={handleGenVideo} videoLoading={videoLoading}
                  genError={genError}
                />
              )}

              {videoLoading && (
                <div className="video-loading">
                  <div className="spinner" />
                  <div>正在生成口播视频片段，请稍候…</div>
                  <p>DeepSeek 拆分脚本 → 逐段调用 AtlasCloud，可能需要 3-10 分钟</p>
                </div>
              )}

              {seedanceSegments.length > 0 && (
                <SeedanceResults segments={seedanceSegments}
                  onReset={() => { setSeedanceSegments([]); setGenError('') }} />
              )}
            </section>
          )}
        </>
      )}

      {/* 爆款仿写模式 */}
      {activeMode === 'rip' && (
        <>
          {step === 1 && (
            <section className="drama-card">
              <div className="form-field">
                <label className="form-label">参考视频链接 <span className="required">*</span></label>
                <input className="form-input" value={refUrl}
                  placeholder="粘贴小红书/抖音视频链接（支持粘贴带文字的分享内容）"
                  onChange={handleUrlChange} />
              </div>

              <div className="form-row-2">
                <div className="form-field">
                  <label className="form-label">发布平台</label>
                  <div className="plat-cards">
                    {PLATFORMS.map((p) => (
                      <button key={p.id}
                        className={'plat-card' + (platform === p.id ? ' is-active' : '')}
                        onClick={() => setPlatform(p.id)}>
                        {p.label}
                      </button>
                    ))}
                  </div>
                </div>
                <div className="form-field">
                  <label className="form-label">仿写时长</label>
                  <div className="chip-row">
                    {['30秒', '60秒', '90秒', '3分钟'].map((d) => (
                      <button key={d}
                        className={'chip' + (duration === d ? ' is-active' : '')}
                        onClick={() => setDuration(d)}>{d}</button>
                    ))}
                  </div>
                </div>
              </div>

              {genError && <div className="kb-error">{genError}</div>}

              <div className="step-actions">
                <button className="btn-primary" onClick={handleAnalyzeVideo}
                  disabled={!refUrl.trim() || analyzing}>
                  {analyzing ? '分析中…' : '分析爆款原因 (10 积分)'}
                </button>
              </div>
            </section>
          )}

          {step === 2 && (
            <section className="drama-card">
              <div className="result-head">
                <h3>爆款分析结果</h3>
                <div className="result-actions">
                  <button className="btn-ghost" onClick={() => setStep(1)}>← 返回</button>
                  <button className="btn-ghost" onClick={() => copyToClipboard(stripMarkdown(analysisResult))}>复制</button>
                  <button className="btn-primary" onClick={handleRewriteScript} disabled={genLoading}>
                    {genLoading ? '生成中…' : '下一步: 仿写剧本 (10 积分)'}
                  </button>
                </div>
              </div>

              {genError && <div className="kb-error">{genError}</div>}

              <div className="script-content">
                <ReactMarkdown remarkPlugins={[remarkGfm]}>{analysisResult || '(空)'}</ReactMarkdown>
              </div>
            </section>
          )}

          {step === 3 && (
            <section className="drama-card">
              <div className="result-head">
                <div className="script-tabs">
                  <button className={'tab' + (scriptTab === 'full' ? ' is-active' : '')}
                    onClick={() => setScriptTab('full')}>完整剧本</button>
                  <button className={'tab' + (scriptTab === 'storyboard' ? ' is-active' : '')}
                    onClick={() => setScriptTab('storyboard')}>拆解分镜</button>
                </div>
                <div className="result-actions">
                  <button className="btn-ghost" onClick={() => setStep(2)}>← 返回</button>
                  <button className="btn-ghost" onClick={() => copyToClipboard(stripMarkdown(scriptTab === 'full' ? script : storyboard))}>复制</button>
                  {scriptTab === 'full' && !storyboard && (
                    <button className="btn-ghost" onClick={handleGenStoryboard} disabled={genLoading}>
                      {genLoading ? '拆解中…' : '拆分镜'}
                    </button>
                  )}
                  <button className="btn-primary" onClick={() => setStep(4)} disabled={!script}>
                    下一步：配置视频 →
                  </button>
                </div>
              </div>

              {genError && <div className="kb-error">{genError}</div>}

              <div className="script-content">
                {scriptTab === 'full' ? (
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>{script || '(空)'}</ReactMarkdown>
                ) : (
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>{storyboard || '点上方"拆分镜"先生成分镜表'}</ReactMarkdown>
                )}
              </div>
            </section>
          )}

          {step === 4 && (
            <section className="drama-card">
              <div className="result-head">
                <h3>视频生成</h3>
                <div className="result-actions">
                  <button className="btn-ghost" onClick={() => setStep(3)}>← 返回</button>
                </div>
              </div>

              {!seedanceSegments.length && !videoLoading && (
                <VideoGenConfig
                  characterPreview={characterPreview} characterUploading={characterUploading}
                  backgroundPreview={backgroundPreview} backgroundUploading={backgroundUploading}
                  charFileRef={charFileRef} bgFileRef={bgFileRef}
                  onUpload={handleUploadImage}
                  onClearChar={() => { setCharacterPreview(null); setCharacterImageUrl(''); if (charFileRef.current) charFileRef.current.value = '' }}
                  onClearBg={() => { setBackgroundPreview(null); setBackgroundImageUrl(''); if (bgFileRef.current) bgFileRef.current.value = '' }}
                  onGenerate={handleGenVideo} videoLoading={videoLoading}
                  genError={genError}
                />
              )}

              {videoLoading && (
                <div className="video-loading">
                  <div className="spinner" />
                  <div>正在生成口播视频片段，请稍候…</div>
                  <p>DeepSeek 拆分脚本 → 逐段调用 AtlasCloud，可能需要 3-10 分钟</p>
                </div>
              )}

              {seedanceSegments.length > 0 && (
                <SeedanceResults segments={seedanceSegments}
                  onReset={() => { setSeedanceSegments([]); setGenError('') }} />
              )}
            </section>
          )}
        </>
      )}
    </div>
  )
}

function VideoGenConfig({
  characterPreview, characterUploading,
  backgroundPreview, backgroundUploading,
  charFileRef, bgFileRef,
  onUpload, onClearChar, onClearBg,
  onGenerate, videoLoading, genError,
}) {
  return (
    <div className="vgen-config">
      <div className="vgen-uploads">
        <UploadArea
          label="出镜人物"
          required
          hint="上传人物照片，AI 以该形象生成口播主播（必须）"
          preview={characterPreview}
          uploading={characterUploading}
          fileRef={charFileRef}
          onFile={(f) => onUpload('character', f)}
          onClear={onClearChar}
        />
        <UploadArea
          label="背景场景"
          hint="上传背景图片，AI 以该图片风格渲染背景（可选）"
          preview={backgroundPreview}
          uploading={backgroundUploading}
          fileRef={bgFileRef}
          onFile={(f) => onUpload('background', f)}
          onClear={onClearBg}
        />
      </div>

      {genError && <div className="kb-error">{genError}</div>}

      <div className="step-actions">
        <button className="btn-primary" onClick={onGenerate} disabled={videoLoading}>
          {videoLoading ? '生成中…' : '开始生成口播视频'}
        </button>
      </div>
    </div>
  )
}

function UploadArea({ label, required, hint, preview, uploading, fileRef, onFile, onClear }) {
  return (
    <div className="vgen-upload-area">
      <div className="vgen-upload-label">
        {label}
        {required
          ? <span className="required" style={{marginLeft:4}}>*</span>
          : <span className="vgen-optional">可选</span>}
      </div>
      <input
        ref={fileRef}
        type="file"
        accept="image/*"
        style={{ display: 'none' }}
        disabled={uploading}
        onChange={(e) => onFile(e.target.files?.[0])}
      />
      {preview ? (
        <div className="vgen-preview">
          <img src={preview} alt={label} className="vgen-thumb" />
          <div className="vgen-preview-info">
            {uploading
              ? <span className="vgen-status">上传中...</span>
              : <span className="vgen-status vgen-ok">✓ 已上传</span>
            }
            <button type="button" className="vgen-change-btn"
              onClick={() => fileRef.current?.click()} disabled={uploading}>
              重新选择
            </button>
            <button type="button" className="vgen-remove-btn" onClick={onClear} disabled={uploading}>
              移除
            </button>
          </div>
        </div>
      ) : (
        <button type="button" className="vgen-upload-btn"
          onClick={() => fileRef.current?.click()} disabled={uploading}>
          + 点击上传图片
        </button>
      )}
      <p className="vgen-hint">{hint}</p>
    </div>
  )
}

function SeedanceResults({ segments, onReset }) {
  return (
    <div className="seedance-results">
      <div className="seedance-header">
        <h4>口播视频片段（共 {segments.length} 段）</h4>
        <button className="btn-ghost" onClick={onReset}>重新生成</button>
      </div>
      {segments.map((seg) => (
        <div key={seg.index} className="seedance-segment">
          <div className="seedance-meta">
            <span className="seedance-index">第 {seg.index} 段</span>
            <span className="seedance-duration">约 {seg.durationEstimate} 秒</span>
          </div>
          <p className="seedance-script">{seg.script}</p>
          <video src={seg.videoUrl} controls playsInline className="seedance-video" />
          <div className="seedance-actions">
            <a className="btn-ghost" href={seg.videoUrl} download={`segment-${seg.index}.mp4`}>
              ⬇ 下载第 {seg.index} 段
            </a>
            <button className="btn-ghost" onClick={() => navigator.clipboard.writeText(seg.videoUrl)}>
              复制链接
            </button>
          </div>
        </div>
      ))}
    </div>
  )
}
