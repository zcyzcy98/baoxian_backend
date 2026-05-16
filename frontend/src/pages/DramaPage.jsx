import { useEffect, useRef, useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { callAgent, generateSeedanceVideo, mergeVideos } from '../api'
import { copyToClipboard, stripMarkdown } from '../utils/markdown'
import ConfirmModal, { useConfirmModal } from '../components/ConfirmModal'
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

function parseStoryboardJson(raw) {
  try {
    const json = JSON.parse(raw)
    if (Array.isArray(json?.segments)) return json.segments
  } catch { /* fall through */ }
  return []
}

// 从文本中提取链接
function extractUrlFromText(text) {
  if (!text) return ''
  const urlMatch = text.match(/https?:\/\/[^\s<>"{}|\\^`\[\]]+/)
  return urlMatch ? urlMatch[0] : ''
}

// 尝试解析分析结果为结构化 JSON
function parseAnalysisJson(raw) {
  if (!raw) return null
  try {
    // 先尝试直接解析
    return JSON.parse(raw)
  } catch {
    // 尝试从 markdown 中提取 JSON 块
    const jsonMatch = raw.match(/```(?:json)?\s*([\s\S]*?)```/)
    if (jsonMatch) {
      try { return JSON.parse(jsonMatch[1].trim()) } catch { /* fall through */ }
    }
    // 尝试找到第一个 { 和最后一个 }
    const firstBrace = raw.indexOf('{')
    const lastBrace = raw.lastIndexOf('}')
    if (firstBrace >= 0 && lastBrace > firstBrace) {
      try { return JSON.parse(raw.slice(firstBrace, lastBrace + 1)) } catch { /* fall through */ }
    }
    return null
  }
}

function stripTitlePrefix(s) {
  return (s || '')
    .replace(/^\s*\d+[.、)]\s*/, '')
    .replace(/^\s*[\[【][^\]】]+[\]】]\s*/, '')
    .replace(/^["“]|["”]$/g, '')
    .trim()
}

function parseTitles(raw) {
  if (!raw) return []
  try {
    const clean = raw.replace(/```[a-z]*/gi, '').replace(/```/g, '').trim()
    const parsed = JSON.parse(clean)
    const arr = Array.isArray(parsed) ? parsed : parsed?.titles
    if (Array.isArray(arr)) return arr.map(t => ({ ...t, text: stripTitlePrefix(t.text) }))
  } catch {}
  return raw.split('\n').filter(l => l.trim()).slice(0, 3).map((t, i) => ({
    text: stripTitlePrefix(t),
    style: ['数据型', '悬念型', '冲突型'][i] || '通用',
  }))
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
export default function DramaPage({
  topicPrefill, onPrefillConsumed,
  contentPrefill, onContentPrefillConsumed,
  onNavigate, onNavigateWithContentPrefill,
  mode = 'create',
}) {
  const ST = {
    activeMode: mode,
    step: 1,
    topic: '',
    direction: '',
    platform: '',
    duration: '',
    refUrl: '',
    analysisResult: '',
    analyzing: false,
    scriptTab: 'full',
    script: '',
    storyboardSegs: [],
    genLoading: false,
    genError: '',
    videoLoading: false,
    videoError: '',
    seedanceSegments: [],
    characterImageUrl: '',
    characterPreview: null,
    characterUploading: false,
    backgroundImageUrl: '',
    backgroundPreview: null,
    backgroundUploading: false,
    licenseChecked: false,
    videoRatio: '',
    videoResolution: '',
  }
  const [activeMode, setActiveMode] = useState(mode)
  const [step, setStep] = useState(ST.step)
  const [topic, setTopic] = useState(ST.topic)
  const [direction, setDirection] = useState(ST.direction)
  const [platform, setPlatform] = useState(ST.platform)
  const [duration, setDuration] = useState(ST.duration)
  const [refUrl, setRefUrl] = useState(ST.refUrl)
  const [rawInput, setRawInput] = useState('')  // 保存用户原始输入（含分享文案+话题+链接）
  const [analysisResult, setAnalysisResult] = useState(ST.analysisResult)
  const [analyzing, setAnalyzing] = useState(ST.analyzing)
  const [scriptTab, setScriptTab] = useState(ST.scriptTab)
  const [script, setScript] = useState(ST.script)
  const [storyboardSegs, setStoryboardSegs] = useState(ST.storyboardSegs)
  const [genLoading, setGenLoading] = useState(ST.genLoading)
  const [genError, setGenError] = useState(ST.genError)
  const [videoLoading, setVideoLoading] = useState(ST.videoLoading)
  const [videoError, setVideoError] = useState(ST.videoError)
  const [seedanceSegments, setSeedanceSegments] = useState(ST.seedanceSegments)
  const [characterImageUrl, setCharacterImageUrl] = useState(ST.characterImageUrl)
  const [characterPreview, setCharacterPreview] = useState(ST.characterPreview)
  const [characterUploading, setCharacterUploading] = useState(ST.characterUploading)
  const [backgroundImageUrl, setBackgroundImageUrl] = useState(ST.backgroundImageUrl)
  const [backgroundPreview, setBackgroundPreview] = useState(ST.backgroundPreview)
  const [backgroundUploading, setBackgroundUploading] = useState(ST.backgroundUploading)
  const [licenseChecked, setLicenseChecked] = useState(ST.licenseChecked)
  const [videoRatio, setVideoRatio] = useState(ST.videoRatio)
  const [videoResolution, setVideoResolution] = useState(ST.videoResolution)
  const [citations, setCitations] = useState([])

  // 仿写模式 Step 3：分镜剧本状态
  const [expandedShots, setExpandedShots] = useState(new Set([0]))
  const [titles, setTitles] = useState([])
  const [selectedTitle, setSelectedTitle] = useState(0)

  // 声音参考（Seedance reference_audios）
  const { confirm, props: confirmProps } = useConfirmModal()

  /**
   * 统一步骤切换。当从靠后的步骤往前走时，如果中间步骤已有生成内容则弹确认。
   * - create 模式 3 步：1.选题  2.剧本/分镜  3.视频
   * - rip 模式 4 步：1.链接  2.爆款分析  3.仿写剧本  4.视频
   */
  const goStep = (n, cleanup) => {
    const maxStep = activeMode === 'rip' ? 4 : 3
    if (n < 1 || n > maxStep || n === step) return
    const doGo = () => { cleanup?.(); setStep(n) }
    if (n < step) {
      const willLose = []
      if (activeMode === 'create') {
        if (n < 2 && step >= 2 && (script || storyboardSegs.length > 0)) willLose.push('剧本与分镜')
        if (n < 3 && step >= 3 && seedanceSegments.length > 0) willLose.push('已生成的视频')
      } else {
        if (n < 2 && step >= 2 && analysisResult) willLose.push('爆款分析结果')
        if (n < 3 && step >= 3 && (script || storyboardSegs.length > 0)) willLose.push('仿写剧本与分镜')
        if (n < 4 && step >= 4 && seedanceSegments.length > 0) willLose.push('已生成的视频')
      }
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

  const [voiceMode, setVoiceMode] = useState('')
  const [selectedVoiceTemplate, setSelectedVoiceTemplate] = useState('')
  const [recordedAudioUrl, setRecordedAudioUrl] = useState('')
  const [recordedAudioData, setRecordedAudioData] = useState('')
  const [isRecording, setIsRecording] = useState(false)
  const [recordSeconds, setRecordSeconds] = useState(0)
  const [showRecordModal, setShowRecordModal] = useState(false)
  const mediaRecorderRef = useRef(null)
  const recordTimerRef = useRef(null)

  const VOICE_TEMPLATES = [
    { id: 'f1', label: '女声 1', file: '/voice/女 1.mp3', tone: '温柔亲切' },
    { id: 'f2', label: '女声 2', file: '/voice/女 2.mp3', tone: '清晰干练' },
    { id: 'm1', label: '男声 1', file: '/voice/男 1.mp3', tone: '沉稳专业' },
    { id: 'm2', label: '男声 2', file: '/voice/男 2.mp3', tone: '年轻有力' },
  ]
  const RECORD_SCRIPT = '买保险这件事，其实没有想象中那么复杂，关键是看清自己真正的需要，再做选择。'

  const charFileRef = useRef(null)
  const bgFileRef = useRef(null)

  const resetState = () => {
    setActiveMode(mode)
    setStep(ST.step)
    setTopic(ST.topic)
    setDirection(ST.direction)
    setPlatform(ST.platform)
    setDuration(ST.duration)
    setRefUrl(ST.refUrl)
    setRawInput('')
    setAnalysisResult(ST.analysisResult)
    setAnalyzing(ST.analyzing)
    setScriptTab(ST.scriptTab)
    setScript(ST.script)
    setStoryboardSegs(ST.storyboardSegs)
    setGenLoading(ST.genLoading)
    setGenError(ST.genError)
    setVideoLoading(ST.videoLoading)
    setVideoError(ST.videoError)
    setSeedanceSegments(ST.seedanceSegments)
    setCharacterImageUrl(ST.characterImageUrl)
    setCharacterPreview(ST.characterPreview)
    setCharacterUploading(ST.characterUploading)
    setBackgroundImageUrl(ST.backgroundImageUrl)
    setBackgroundPreview(ST.backgroundPreview)
    setBackgroundUploading(ST.backgroundUploading)
    setLicenseChecked(ST.licenseChecked)
    setVideoRatio(ST.videoRatio)
    setVideoResolution(ST.videoResolution)
  }

  // 选题广场预填
  useEffect(() => {
    if (topicPrefill?.topic) {
      setTopic(topicPrefill.topic)
      if (topicPrefill.angle) setDirection(topicPrefill.angle)
      onPrefillConsumed?.()
    }
  }, [topicPrefill])

  useEffect(() => {
    if (contentPrefill) {
      if (contentPrefill.activeMode) setActiveMode(contentPrefill.activeMode)
      if (contentPrefill.step != null) setStep(contentPrefill.step)
      if (contentPrefill.topic) setTopic(contentPrefill.topic)
      if (contentPrefill.direction) setDirection(contentPrefill.direction)
      if (contentPrefill.platform) setPlatform(contentPrefill.platform)
      if (contentPrefill.duration) setDuration(contentPrefill.duration)
      if (contentPrefill.refUrl) setRefUrl(contentPrefill.refUrl)
      if (contentPrefill.analysisResult) setAnalysisResult(contentPrefill.analysisResult)
      if (contentPrefill.script) { setScript(contentPrefill.script); if (contentPrefill.step == null) setStep(2) }
      if (contentPrefill.storyboardSegs) setStoryboardSegs(contentPrefill.storyboardSegs)
      if (contentPrefill.seedanceSegments) setSeedanceSegments(contentPrefill.seedanceSegments)
      if (contentPrefill.videoRatio) setVideoRatio(contentPrefill.videoRatio)
      if (contentPrefill.videoResolution) setVideoResolution(contentPrefill.videoResolution)
      onContentPrefillConsumed?.()
    }
  }, [contentPrefill])

  // 处理链接输入
  const handleUrlChange = (e) => {
    const inputText = e.target.value
    setRawInput(inputText)  // 保存原始输入
    const extractedUrl = extractUrlFromText(inputText)
    setRefUrl(extractedUrl || inputText)
  }

  // ── 分镜编辑 helpers ──
  function computeTotalSeconds(segs) {
    return segs.reduce((s, sh) => s + (Number(sh.duration) || 0), 0)
  }

  function formatSeconds(s) {
    const m = Math.floor(s / 60)
    const sec = s % 60
    return m > 0 ? `${m}:${String(sec).padStart(2, '0')}` : `${sec} 秒`
  }

  function toggleExpanded(i) {
    setExpandedShots(prev => {
      const s = new Set(prev)
      s.has(i) ? s.delete(i) : s.add(i)
      return s
    })
  }

  function updateShot(index, field, value) {
    setStoryboardSegs(prev => prev.map((s, i) => i === index ? { ...s, [field]: value } : s))
  }

  // ── 声音参考 helpers ──
  function blobToDataUrl(blob) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(reader.result)
      reader.onerror = reject
      reader.readAsDataURL(blob)
    })
  }

  async function urlToDataUrl(url) {
    const res = await fetch(url)
    const blob = await res.blob()
    return blobToDataUrl(blob)
  }

  async function pickVoiceTemplate(id) {
    const t = VOICE_TEMPLATES.find(v => v.id === id)
    if (!t) return
    setVoiceMode('template')
    setSelectedVoiceTemplate(id)
    setRecordedAudioUrl('')
    setRecordedAudioData('')
    try {
      const dataUrl = await urlToDataUrl(t.file)
      setRecordedAudioData(dataUrl)
    } catch (err) {
      setGenError('加载声音模板失败：' + err.message)
    }
  }

  async function startRecording() {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      const mr = new MediaRecorder(stream)
      const chunks = []
      mr.ondataavailable = e => { if (e.data.size > 0) chunks.push(e.data) }
      mr.onstop = async () => {
        const blob = new Blob(chunks, { type: mr.mimeType || 'audio/webm' })
        const dataUrl = await blobToDataUrl(blob)
        setRecordedAudioUrl(URL.createObjectURL(blob))
        setRecordedAudioData(dataUrl)
        setVoiceMode('recording')
        setSelectedVoiceTemplate('')
        stream.getTracks().forEach(t => t.stop())
      }
      mr.start()
      mediaRecorderRef.current = mr
      setIsRecording(true)
      setRecordSeconds(0)
      recordTimerRef.current = setInterval(() => {
        setRecordSeconds(s => {
          if (s + 1 >= 15) { stopRecording(); return 15 }
          return s + 1
        })
      }, 1000)
    } catch (err) {
      setGenError('麦克风访问失败：' + err.message)
    }
  }

  function stopRecording() {
    const mr = mediaRecorderRef.current
    if (mr && mr.state !== 'inactive') mr.stop()
    if (recordTimerRef.current) clearInterval(recordTimerRef.current)
    recordTimerRef.current = null
    setIsRecording(false)
    setShowRecordModal(false)
  }

  function clearVoice() {
    setVoiceMode('')
    setSelectedVoiceTemplate('')
    setRecordedAudioUrl('')
    setRecordedAudioData('')
  }

  // 分析爆款原因
  const handleAnalyzeVideo = async () => {
    if (!refUrl.trim()) return
    setAnalyzing(true)
    setGenError('')
    try {
      // 优先发送完整分享文本（含文案+话题+链接），后端可解析文案；回退用纯链接
      const videoInput = (rawInput && rawInput.trim().length > refUrl.trim().length) ? rawInput.trim() : refUrl.trim()
      const r = await callAgent('video-to-script', {
        videoUrl: videoInput,
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
      const parsedSegs = parseStoryboardJson(sb?.content || '')
      setStoryboardSegs(parsedSegs)
      setExpandedShots(new Set(parsedSegs.map((_, i) => i)))

      // 标题候选（非关键，失败不影响主流程）
      try {
        const titleResult = await callAgent('title', {
          topic: '爆款仿写',
          direction: `基于以下爆款分析生成标题：${analysisResult.slice(0, 500)}`,
          platform,
          count: 3,
        }, 'chat')
        setTitles(parseTitles(titleResult?.content || ''))
        setSelectedTitle(0)
      } catch { /* 标题生成失败不影响分镜 */ }

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
      if (sc?.citations) setCitations(sc.citations)
      setStoryboardSegs(parseStoryboardJson(sb?.content || ''))
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
      setStoryboardSegs(parseStoryboardJson(r?.content || ''))
      setScriptTab('storyboard')
    } catch (err) {
      setGenError(err.message)
    } finally {
      setGenLoading(false)
    }
  }

  const handleRegenSegment = async (segIndex) => {
    const seg = storyboardSegs[segIndex]
    if (!seg) return
    setGenLoading(true)
    try {
      const r = await callAgent('video-storyboard', {
        topic,
        style: `只重新生成第 ${seg.index} 个镜头，口播原文为："${seg.voiceover}"，景别建议 ${seg.shot_type}，请保持其他镜头不变，只输出这一个镜头的 JSON segment 对象（不含外层 segments 数组）`,
        duration,
        script,
      })
      try {
        const raw = (r?.content || '').replace(/```[a-z]*/gi, '').replace(/```/g, '').trim()
        const parsed = JSON.parse(raw)
        const newSeg = parsed?.segments?.[0] ?? parsed
        if (newSeg?.voiceover) {
          setStoryboardSegs(prev => prev.map((s, i) => i === segIndex ? { ...newSeg, index: seg.index } : s))
        }
      } catch { /* keep old */ }
    } catch (err) {
      setGenError(err.message)
    } finally {
      setGenLoading(false)
    }
  }

  const handleDeleteSegment = (segIndex) => {
    setStoryboardSegs(prev => prev.filter((_, i) => i !== segIndex).map((s, i) => ({ ...s, index: i + 1 })))
  }

  const handleAddSegmentAfter = (segIndex) => {
    const newSeg = { index: 0, duration: 6, shot_type: '中景', scene: '（待填写）', voiceover: '（待填写）', subtitle: '', notes: '' }
    setStoryboardSegs(prev => {
      const next = [...prev]
      next.splice(segIndex + 1, 0, newSeg)
      return next.map((s, i) => ({ ...s, index: i + 1 }))
    })
  }

  const handleInsertSegmentAt = (insertIndex) => {
    const newSeg = { index: 0, duration: 6, shot_type: '中景', scene: '（待填写）', voiceover: '（待填写）', subtitle: '', notes: '' }
    setStoryboardSegs(prev => {
      const next = [...prev]
      next.splice(insertIndex, 0, newSeg)
      return next.map((s, i) => ({ ...s, index: i + 1 }))
    })
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

  // 点「生成视频」验证后跳转并开始生成
  const goGenerate = () => {
    if (characterImageUrl && !licenseChecked) { setGenError('请勾选人物授权声明'); return }
    setGenError('')
    setVideoError('')
    setSeedanceSegments([])
    // rip 模式 step 3 是分镜，step 4 是视频生成；create 模式 step 2 是分镜，step 3 是视频生成
    setStep(activeMode === 'rip' ? 4 : 3)
    startGeneration()
  }

  const startGeneration = async () => {
    setVideoLoading(true)
    setVideoError('')
    setSeedanceSegments([])
    try {
      const payload = {
        characterImageUrl: characterImageUrl || null,
        backgroundImageUrl: backgroundImageUrl || null,
        style: direction,
        videoRatio,
        videoResolution,
        referenceAudioUrl: recordedAudioData || null,
      }
      // 如果有已编辑的分镜段，直接传给后端，跳过 DeepSeek 重新拆分
      if (storyboardSegs.length > 0) {
        payload.storyboardSegments = storyboardSegs.map(s => ({
          voiceover: s.voiceover,
          duration: s.duration,
          prompt: s.scene || '',  // 用画面描述作为 prompt 参考
        }))
      } else {
        payload.script = script
      }
      const r = await generateSeedanceVideo(payload)
      setSeedanceSegments(r?.segments || [])
    } catch (err) {
      setVideoError(err.message)
    } finally {
      setVideoLoading(false)
    }
  }

  const currentSteps = activeMode === 'create' ? STEPS_CREATE : STEPS_RIP

  return (
    <div className="drama-page">
      {mode === 'rip' ? (
        <>
          <div className="rip-page-head">
            <div className="rip-page-title-row">
              <div className="rip-page-title">
                <span className="rip-platform-tag">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
                </span>
                <h2>视频仿做</h2>
                <span className="rip-page-sub">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
                  拆解爆款视频 · 引导你做自己的版本
                </span>
              </div>
              <button className="btn-ghost reset-btn" onClick={resetState} title="清除所有内容重新开始">
                重新开始
              </button>
            </div>
            <div className="steps">
              {currentSteps.map((s) => (
                <div key={s.id}
                  className={'step' + (step > s.id ? ' done' : '') + (step === s.id ? ' active' : '')}
                  onClick={() => step >= s.id && goStep(s.id)}>
                  <div className="dot">{step > s.id ? '✓' : s.id}</div>
                  <div className="step-label">{s.label}</div>
                </div>
              ))}
            </div>
          </div>
        </>
      ) : (
        <>
          <header className="drama-header">
            <div>
              <h2>视频创作</h2>
              <p className="page-sub">支持空白创作和爆款仿写两种模式</p>
            </div>
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
            <button className="btn-ghost reset-btn" onClick={resetState} title="清除所有内容重新开始">
              重新开始
            </button>
          </header>

          <div className="step-bar">
            {currentSteps.map((s) => (
              <div key={s.id}
                className={'step-item' + (step >= s.id ? ' is-done' : '') + (step === s.id ? ' is-active' : '')}
                onClick={() => step > s.id && goStep(s.id)}>
                <div className="step-num">{s.id}</div>
                <div className="step-label">{s.label}</div>
              </div>
            ))}
          </div>
        </>
      )}

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
            <div className="sb-grid">
              {/* 左：脚本内容 */}
              <section className="drama-card sb-main">
                <div className="result-head">
                  <div className="script-tabs">
                    <button className={'tab' + (scriptTab === 'full' ? ' is-active' : '')}
                      onClick={() => setScriptTab('full')}>完整剧本</button>
                    <button className={'tab' + (scriptTab === 'storyboard' ? ' is-active' : '')}
                      onClick={() => setScriptTab('storyboard')}>拆解分镜</button>
                  </div>
                  <div className="result-actions">
                    <button className="btn-ghost" onClick={() => goStep(1)}>← 返回</button>
                    <button className="btn-ghost" onClick={() => copyToClipboard(stripMarkdown(script))}>复制</button>
                    {scriptTab === 'full' && (
                      <button className="btn-ghost" onClick={handleGenStoryboard} disabled={genLoading}>
                        {genLoading ? '拆解中…' : '拆分镜'}
                      </button>
                    )}
                  </div>
                </div>
                {genError && <div className="kb-error">{genError}</div>}
                {scriptTab === 'full' ? (
                  <div className="script-content">
                    <ReactMarkdown remarkPlugins={[remarkGfm]}>{script || '(空)'}</ReactMarkdown>
                  </div>
                ) : (
                  <StoryboardView
                    segments={storyboardSegs}
                    genLoading={genLoading}
                    onRegen={handleRegenSegment}
                    onDelete={handleDeleteSegment}
                    onAddAfter={handleAddSegmentAfter}
                    onInsertAt={handleInsertSegmentAt}
                  />
                )}
              </section>

              {/* 右：上传 + 生成 */}
              <aside className="sb-aside">
                {citations.length > 0 && (
                  <div className="drama-card" style={{ marginBottom: 12, padding: '12px 16px' }}>
                    <div style={{ fontSize: 11, fontFamily: 'var(--mono, monospace)', color: 'var(--ink-3, #888)', marginBottom: 8 }}>
                      — Citations · 爆款样本引用
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                      {citations.map((cite, i) => (
                        <div key={i} style={{ display: 'flex', gap: 8, fontSize: 12, color: 'var(--ink-2, #555)' }}>
                          <span style={{ flexShrink: 0, fontFamily: 'var(--mono, monospace)', color: 'var(--clay, #c8855a)' }}>[{cite.index}]</span>
                          <span>{cite.title || '爆款样本 ' + cite.index}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
                <UploadPanel
                  characterPreview={characterPreview} characterUploading={characterUploading}
                  backgroundPreview={backgroundPreview} backgroundUploading={backgroundUploading}
                  charFileRef={charFileRef} bgFileRef={bgFileRef}
                  onUpload={handleUploadImage}
                  onClearChar={() => { setCharacterPreview(null); setCharacterImageUrl(''); if (charFileRef.current) charFileRef.current.value = '' }}
                  onClearBg={() => { setBackgroundPreview(null); setBackgroundImageUrl(''); if (bgFileRef.current) bgFileRef.current.value = '' }}
                  licenseChecked={licenseChecked} onLicenseToggle={() => setLicenseChecked(v => !v)}
                  onGenerate={goGenerate} genError={genError}
                  videoRatio={videoRatio} onRatioChange={setVideoRatio}
                  videoResolution={videoResolution} onResolutionChange={setVideoResolution}
                />
              </aside>
            </div>
          )}

          {step === 3 && (
            <section className="drama-card">
              <div className="result-head">
                <h3>视频生成</h3>
                <div className="result-actions">
                  <button className="btn-ghost" onClick={() => goStep(2, () => { setSeedanceSegments([]); setVideoError('') })}>← 返回</button>
                </div>
              </div>

              {videoLoading && (
                <div className="video-loading">
                  <div className="spinner" />
                  <div>正在生成口播视频片段，请稍候…</div>
                  <p>AI 拆分脚本 → 逐段调用 AtlasCloud，可能需要 3-10 分钟</p>
                </div>
              )}

              {videoError && !videoLoading && (
                <div className="video-error-panel">
                  <div className="video-error-title">生成失败</div>
                  <div className="video-error-msg">{videoError}</div>
                  <button className="btn-ghost" onClick={() => goStep(2, () => setVideoError(''))}>返回重试</button>
                </div>
              )}

              {seedanceSegments.length > 0 && (
                <SeedanceResults segments={seedanceSegments}
                  onReset={() => goStep(2, () => setSeedanceSegments([]))} />
              )}
            </section>
          )}
        </>
      )}

      {/* 爆款仿写模式 */}
      {activeMode === 'rip' && (
        <>
          {step === 1 && (
            <div className="step-pane">
              <div className="section-card">
                <div className="sc-head">
                  <h3>提供你想仿做的视频</h3>
                  <span className="desc">支持抖音 / 小红书链接 · 目前仅支持口播视频</span>
                </div>
                <div className="sc-body">
                  <div style={{
                    display: 'flex', alignItems: 'center', gap: 8,
                    padding: '10px 14px', marginBottom: 16,
                    background: 'var(--sand-tint, #F4ECD9)', border: '1px solid var(--sand-soft, #ECDCBF)',
                    borderRadius: 'var(--r-sm, 6px)', fontSize: 12.5, color: '#8a6a3a', lineHeight: 1.5
                  }}>
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
                    <span>目前只支持<b>口播视频</b>的分析与仿做，暂不支持剧情类、混剪类视频</span>
                  </div>
                  <div className="form-row">
                    <div className="label">参考视频链接 <span className="req">*</span></div>
                    <div className="control">
                      <input className="text-field" value={refUrl}
                        placeholder="粘贴小红书/抖音视频链接（支持粘贴带文字的分享内容）"
                        onChange={handleUrlChange} />
                    </div>
                  </div>

                  <div className="form-row">
                    <div className="label">发布平台</div>
                    <div className="control">
                      <div className="chip-row">
                        {PLATFORMS.filter(p => p.id !== 'video').map((p) => (
                          <button key={p.id}
                            className={'chip' + (platform === p.id ? ' is-active' : '')}
                            onClick={() => setPlatform(p.id)}>
                            {p.label}
                          </button>
                        ))}
                      </div>
                    </div>
                  </div>

                  <div className="form-row">
                    <div className="label">仿写时长</div>
                    <div className="control">
                      <div className="chip-row">
                        {['30秒', '60秒', '90秒', '3分钟'].map((d) => (
                          <button key={d}
                            className={'chip' + (duration === d ? ' is-active' : '')}
                            onClick={() => setDuration(d)}>{d}</button>
                        ))}
                      </div>
                    </div>
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
            </div>
          )}

          {step === 2 && (() => {
            const aj = parseAnalysisJson(analysisResult)
            const ROLE_MAP = { '钩子': 'hook', '展开': 'expand', '高潮': 'climax', '收尾': 'cta' }
            const ICON_MAP = {
              'Hook': <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/></svg>,
              'Pacing': <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>,
              'Empathy': <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>,
              'Data': <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M9 11H5a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h7a2 2 0 0 0 2-2v-3"/><path d="M22 14V8h-6"/><path d="M22 8L13 17l-4-4-6 6"/></svg>,
              'Visual': <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="m21 15-5-5L5 21"/></svg>,
            }
            return (
            <div className="step-pane">
              {/* 顶部摘要 */}
              <div className="analysis-summary">
                <div className="l">
                  <div className="ico">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
                  </div>
                  <div className="info">
                    <div>拆解完成 · {aj?.summary?.video_type || '视频分析结果'}{aj?.summary?.duration ? ` · 时长 ${aj.summary.duration}` : ''}</div>
                    <div className="meta">{aj?.summary?.hook_highlight || '基于 AI 深度分析，提取爆款关键要素'}</div>
                  </div>
                </div>
                <button className="btn-tool" onClick={() => goStep(1)}>
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
                  <span>返回修改</span>
                </button>
              </div>

              {/* 分隔条 */}
              <div className="result-divider">
                <span className="line"></span>
                <span className="label">— 拆解结果 · Analysis —</span>
                <span className="line"></span>
              </div>

              {/* ① 主题与话题 + ② 内容结构 */}
              <div className="analysis-grid">
                <div className="analysis-card">
                  <div className="analysis-card-head">
                    <div className="head-l">
                      <span className="head-num">01</span>
                      <h4>主题与话题方向</h4>
                    </div>
                  </div>
                  <div className="analysis-card-body">
                    <div className="topic-tags">
                      {(aj?.topic?.tags || []).map((tag, i) => (
                        <span key={i} className="topic-tag">#{tag}</span>
                      ))}
                      {!aj?.topic?.tags?.length && <span className="topic-tag">#待分析</span>}
                    </div>
                    <div className="topic-meta">
                      {aj?.topic?.theme || 'AI 将自动提取视频的核心主题和话题方向'}
                    </div>
                  </div>
                </div>

                <div className="analysis-card">
                  <div className="analysis-card-head">
                    <div className="head-l">
                      <span className="head-num">02</span>
                      <h4>内容结构（钩子-展开-收尾）</h4>
                    </div>
                  </div>
                  <div className="analysis-card-body">
                    {aj?.structure?.length > 0 ? (
                      <>
                        <div className="struct-bar">
                          {aj.structure.map((seg, i) => {
                            const role = seg.role || ''
                            const cls = ROLE_MAP[role] || 'expand'
                            const totalDur = aj.structure.reduce((s, x) => {
                              const m = x.time?.match(/(\d+):(\d+)/)
                              return m ? s + parseInt(m[1]) * 60 + parseInt(m[2]) : s + 1
                            }, 0)
                            const width = totalDur > 0 ? Math.max(10, Math.round(100 / aj.structure.length)) : 25
                            return <div key={i} className={`struct-seg ${cls}`} style={{width:`${width}%`}}>{role}</div>
                          })}
                        </div>
                        <div className="struct-list">
                          {aj.structure.map((seg, i) => {
                            const cls = ROLE_MAP[seg.role] || 'expand'
                            return (
                              <div key={i} className="struct-row">
                                <span className={`role ${cls}`}>{seg.role}</span>
                                <span className="time">{seg.time || '—'}</span>
                                <span className="text">{seg.text}</span>
                              </div>
                            )
                          })}
                        </div>
                      </>
                    ) : (
                      <div style={{color:'var(--ink-3)', fontSize:13, textAlign:'center', padding:'20px 0'}}>
                        AI 将自动识别视频的内容结构和节奏分布
                      </div>
                    )}
                  </div>
                </div>
              </div>

              {/* ③ 完整脚本 */}
              {aj?.script_segments?.length > 0 && (
                <div className="analysis-grid full">
                  <div className="analysis-card">
                    <div className="analysis-card-head">
                      <div className="head-l">
                        <span className="head-num">03</span>
                        <h4>完整脚本（共 {aj.script_segments.length} 个分镜）</h4>
                      </div>
                      <button className="btn-ghost" onClick={() => {
                        const text = aj.script_segments.map((s, i) =>
                          `【分镜${s.index || i+1}】${s.time || ''}\n画面：${s.scene || ''}\n${s.speaker ? s.speaker + '：' : ''}${s.dialog || ''}`
                        ).join('\n\n')
                        copyToClipboard(text)
                      }} style={{padding:'5px 10px', fontSize:12}}>复制脚本</button>
                    </div>
                    <div className="analysis-card-body">
                      <div className="script-list-cards">
                        {aj.script_segments.map((seg, i) => (
                          <div key={i} className="script-row-card">
                            <div className="num">{String(seg.index || i + 1).padStart(2, '0')}</div>
                            <div className="content">
                              {seg.scene && <div className="scene-line">{seg.scene}</div>}
                              <div className="dialog-line">
                                {seg.speaker && <span className="speaker">{seg.speaker}：</span>}
                                {seg.dialog}
                              </div>
                              {seg.time && <div className="timing">{seg.time}</div>}
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {/* ④ 涉及人物 */}
              {aj?.characters?.length > 0 && (
                <div className="analysis-grid">
                  <div className="analysis-card">
                    <div className="analysis-card-head">
                      <div className="head-l">
                        <span className="head-num">04</span>
                        <h4>涉及人物（识别 {aj.characters.length} 个）</h4>
                      </div>
                    </div>
                    <div className="analysis-card-body">
                      <div className="char-list">
                        {aj.characters.map((c, i) => (
                          <div key={i} className="char-row">
                            <div className={`char-thumb ${i % 2 === 1 ? 'female' : ''}`}>{(c.name || '?')[0]}</div>
                            <div className="char-info">
                              <div className="char-name">
                                {c.name}
                                {c.role && <span className={`role-tag ${c.role.includes('主') || c.role.includes('引导') ? 'lead' : ''}`}>{c.role}</span>}
                              </div>
                              {c.desc && <div className="char-desc">{c.desc}</div>}
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  </div>

                  {/* ⑤ 爆点分析 */}
                  <div className="analysis-card">
                    <div className="analysis-card-head">
                      <div className="head-l">
                        <span className="head-num">05</span>
                        <h4>爆点分析</h4>
                      </div>
                      <span className="desc">为什么这条会火</span>
                    </div>
                    <div className="analysis-card-body">
                      <div className="viral-list">
                        {(aj?.viral_points || []).map((vp, i) => (
                          <div key={i} className="viral-row">
                            <div className="icon-circle">
                              {ICON_MAP[vp.tag] || ICON_MAP['Hook']}
                            </div>
                            <div className="v-content">
                              <div className="v-title">{vp.title} {vp.tag && <span className="v-tag">{vp.tag}</span>}</div>
                              <div className="v-desc">{vp.desc}</div>
                            </div>
                          </div>
                        ))}
                        {!aj?.viral_points?.length && (
                          <div style={{color:'var(--ink-3)', fontSize:13, textAlign:'center', padding:'20px 0'}}>
                            AI 将自动分析视频的爆点要素
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {/* ⑥ 仿做建议 */}
              {aj?.suggestions?.length > 0 && (
                <div className="analysis-grid full">
                  <div className="analysis-card">
                    <div className="analysis-card-head">
                      <div className="head-l">
                        <span className="head-num">{aj?.characters?.length > 0 ? '06' : '04'}</span>
                        <h4>AI 给你的仿做建议</h4>
                      </div>
                      <span className="desc">基于你的风格与目标人群定制</span>
                    </div>
                    <div className="analysis-card-body">
                      {aj.suggestions.map((s, i) => (
                        <div key={i} className="suggest-card">
                          {s.label && <div className="label">— {s.label}</div>}
                          {s.title && <div className="title">{s.title}</div>}
                          {s.body && <div className="body">{s.body}</div>}
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              )}

              {/* 降级：如果不是 JSON，显示 markdown */}
              {!aj && analysisResult && (
                <div className="analysis-grid full">
                  <div className="analysis-card">
                    <div className="analysis-card-head">
                      <div className="head-l">
                        <span className="head-num">03</span>
                        <h4>完整分析报告</h4>
                      </div>
                      <button className="btn-ghost" onClick={() => copyToClipboard(stripMarkdown(analysisResult))} style={{padding:'5px 10px', fontSize:12}}>复制</button>
                    </div>
                    <div className="analysis-card-body">
                      <div className="script-content" style={{maxHeight:600}}>
                        <ReactMarkdown remarkPlugins={[remarkGfm]}>{analysisResult}</ReactMarkdown>
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {/* 操作栏 */}
              {genError && <div className="kb-error">{genError}</div>}
              <div className="step-actions" style={{marginTop:16}}>
                <button className="btn-ghost" onClick={() => goStep(1)}>← 返回</button>
                <button className="btn-primary" onClick={handleRewriteScript} disabled={genLoading}>
                  {genLoading ? '生成中…' : '下一步: 仿写剧本 (10 积分)'}
                </button>
              </div>
            </div>
            )})()}

          {step === 3 && (
            <div className="rip-sb-grid">

              {/* 左主区 */}
              <div className="rip-sb-main">
                {/* 标题候选 */}
                {titles.length > 0 && (
                  <div className="rip-aux-card rip-titles-top">
                    <div className="rip-aux-head">
                      <span>— Titles · {titles.length} 个标题候选</span>
                    </div>
                    <div className="rip-aux-body">
                      <div className="rip-title-cards">
                        {titles.map((t, i) => (
                          <div
                            key={i}
                            className={`rip-title-card${selectedTitle === i ? ' selected' : ''}`}
                            onClick={() => setSelectedTitle(i)}
                          >
                            <span className="rip-style-label">{t.style || `方案 ${i + 1}`}</span>
                            <span className="rip-title-text">{t.text}</span>
                            <div className="rip-title-check">✓</div>
                          </div>
                        ))}
                      </div>
                    </div>
                  </div>
                )}

                <div className="rip-sb-toolbar">
                  <div className="rip-toolbar-stats">
                    <div className="rip-toolbar-stat">
                      <span>分镜：</span><b>{storyboardSegs.length} 个</b>
                    </div>
                    <div className="rip-toolbar-stat">
                      <span>预估时长：</span><b>约 {computeTotalSeconds(storyboardSegs)} 秒</b>
                    </div>
                  </div>
                  <button className="btn-ghost" onClick={() => goStep(2)} style={{padding:'5px 10px', fontSize:12}}>
                    ← 返回分析
                  </button>
                </div>

                {storyboardSegs.length === 0 && !genLoading && (
                  <div style={{textAlign:'center', padding:'40px 0', color:'var(--ink-3)', fontSize:13}}>
                    暂无分镜 — 请返回上一步重新生成
                  </div>
                )}

                {genError && <div className="kb-error">{genError}</div>}

                <div className="vk-shot-list">
                  {storyboardSegs.map((shot, i) => (
                    <div key={i}>
                      <RipShotCard
                        shot={shot}
                        index={i}
                        expanded={expandedShots.has(i)}
                        onToggle={() => toggleExpanded(i)}
                        onUpdate={(field, val) => updateShot(i, field, val)}
                        onRegen={() => handleRegenSegment(i)}
                        onDelete={() => handleDeleteSegment(i)}
                        onAddAfter={() => handleAddSegmentAfter(i)}
                      />
                      {i < storyboardSegs.length - 1 && (
                        <button className="rip-insert-shot-btn" onClick={() => handleInsertSegmentAt(i + 1)}>
                          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                            <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
                          </svg>
                          <span>在这里插入新分镜</span>
                        </button>
                      )}
                    </div>
                  ))}

                  {storyboardSegs.length > 0 && (
                    <button className="rip-insert-shot-btn" onClick={() => handleAddSegmentAfter(storyboardSegs.length - 1)}>
                      <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                        <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
                      </svg>
                      <span>在末尾添加新分镜</span>
                    </button>
                  )}
                </div>
              </div>

              {/* 右辅助栏 */}
              <div className="rip-aux-panel">

                {/* 拍摄要素 */}
                <div className="rip-aux-card">
                  <div className="rip-aux-head">
                    <span>— Production · 拍摄要素</span>
                  </div>

                  <div className="rip-shoot-pair">
                    <div className="rip-shoot-row rip-shoot-half">
                      <div className="rip-se-label">出镜人物 <span className="req">*</span></div>
                      <input
                        ref={charFileRef}
                        type="file"
                        accept="image/*"
                        style={{ display: 'none' }}
                        onChange={e => handleUploadImage('character', e.target.files?.[0])}
                      />
                      <div
                        className={`rip-portrait-zone${characterPreview ? ' has-image' : ''}`}
                        onClick={() => charFileRef.current?.click()}
                      >
                        {characterPreview ? (
                          <>
                            <img src={characterPreview} alt="出镜人物" className="rip-portrait-preview-img" />
                            <div className="rip-change-overlay">
                              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="17 8 12 3 7 8" /><line x1="12" y1="3" x2="12" y2="15" />
                              </svg>
                              <span>更换照片</span>
                            </div>
                          </>
                        ) : (
                          <div className="rip-upload-prompt">
                            <div className="rip-upload-ico">
                              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="17 8 12 3 7 8" /><line x1="12" y1="3" x2="12" y2="15" />
                              </svg>
                            </div>
                            <div className="rip-upload-text">上传出镜照片</div>
                            <div className="rip-upload-hint">{characterUploading ? '上传中…' : 'JPG / PNG · 建议正面免冠'}</div>
                          </div>
                        )}
                      </div>
                    </div>

                    <div className="rip-shoot-row rip-shoot-half">
                      <div className="rip-se-label">场景背景</div>
                      <input
                        ref={bgFileRef}
                        type="file"
                        accept="image/*"
                        style={{ display: 'none' }}
                        onChange={e => handleUploadImage('background', e.target.files?.[0])}
                      />
                      <div
                        className={`rip-scene-zone${backgroundPreview ? ' has-image' : ''}`}
                        onClick={() => bgFileRef.current?.click()}
                      >
                        <div className="rip-scene-ico">
                          {backgroundPreview ? (
                            <img src={backgroundPreview} alt="背景" style={{ width: '100%', height: '100%', objectFit: 'cover', borderRadius: 'var(--r-sm)' }} />
                          ) : (
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                              <rect x="3" y="3" width="18" height="18" rx="2" /><circle cx="8.5" cy="8.5" r="1.5" /><polyline points="21 15 16 10 5 21" />
                            </svg>
                          )}
                        </div>
                        <div className="rip-scene-info">
                          <strong>{backgroundPreview ? '已上传背景图' : '未上传 · AI 自适应'}</strong>
                          <span>{backgroundUploading ? '上传中…' : backgroundPreview ? '点击更换背景图' : '根据脚本内容自动匹配背景'}</span>
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* 授权声明 */}
                  <div className="rip-shoot-row">
                    <div
                      className={`rip-license-row${licenseChecked ? ' checked' : ''}`}
                      onClick={() => setLicenseChecked(v => !v)}
                    >
                      <div className="rip-license-check" />
                      <label>我承诺此照片为<b>本人</b>或<b>已获本人书面授权</b>，授权承知使用此照片生成 AI 视频内容。</label>
                    </div>
                  </div>

                  {/* 声音参考 */}
                  <div className="rip-shoot-row">
                    <div className="rip-se-label">声音参考</div>
                    <div className="rip-voice-templates">
                      {VOICE_TEMPLATES.map(t => (
                        <div
                          key={t.id}
                          className={`rip-voice-card${selectedVoiceTemplate === t.id ? ' selected' : ''}`}
                          onClick={() => pickVoiceTemplate(t.id)}
                        >
                          <div className="rip-voice-ico">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                              <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/><path d="M19 10v2a7 7 0 0 1-14 0v-2"/><line x1="12" y1="19" x2="12" y2="23"/><line x1="8" y1="23" x2="16" y2="23"/>
                            </svg>
                          </div>
                          <div className="rip-voice-info">
                            <strong>{t.label}</strong>
                            <span>{t.tone}</span>
                          </div>
                          <audio
                            src={t.file}
                            controls
                            preload="none"
                            className="rip-voice-audio"
                            onClick={e => e.stopPropagation()}
                            onPlay={e => {
                              document.querySelectorAll('audio').forEach(a => {
                                if (a !== e.currentTarget) a.pause()
                              })
                            }}
                          />
                        </div>
                      ))}
                    </div>

                    <div className="rip-voice-record">
                      {!isRecording && !recordedAudioUrl && (
                        <button className="rip-btn-record" onClick={() => setShowRecordModal(true)}>
                          <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="12" r="6"/></svg>
                          <span>录制我的声音</span>
                          <span className="rip-record-hint">2-15 秒</span>
                        </button>
                      )}
                      {!isRecording && recordedAudioUrl && (
                        <div className="rip-voice-recorded">
                          <audio
                            src={recordedAudioUrl}
                            controls
                            className="rip-voice-audio"
                            onPlay={e => {
                              document.querySelectorAll('audio').forEach(a => {
                                if (a !== e.currentTarget) a.pause()
                              })
                            }}
                          />
                          <button className="rip-btn-mini" onClick={() => { setRecordedAudioUrl(''); setRecordedAudioData(''); setVoiceMode(''); }}>重录</button>
                        </div>
                      )}
                    </div>

                    {(voiceMode || recordedAudioData) && (
                      <div className="rip-voice-status">
                        ✓ 已选 {voiceMode === 'recording' ? '自录音频' : VOICE_TEMPLATES.find(t => t.id === selectedVoiceTemplate)?.label} · 全片将统一音色
                        <button className="rip-link-btn" onClick={clearVoice}>清除</button>
                      </div>
                    )}
                  </div>
                </div>

                {/* 重新生成 */}
                <button className="rip-regen-btn" onClick={handleRewriteScript} disabled={genLoading}>
                  {genLoading ? (
                    <>
                      <span className="rip-spin" />
                      <span>生成中…</span>
                    </>
                  ) : (
                    <>
                      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
                        <polyline points="23 4 23 10 17 10" /><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
                      </svg>
                      <span>整篇重新生成</span>
                      <span className="rip-credits-tip">−10 积分</span>
                    </>
                  )}
                </button>

                {/* 生成视频 */}
                <button className="btn-primary" onClick={goGenerate} style={{width:'100%', padding:'10px'}}>
                  下一步：生成视频
                </button>
              </div>
            </div>
          )}

          {step === 4 && (
            <section className="drama-card">
              <div className="result-head">
                <h3>视频生成</h3>
                <div className="result-actions">
                  <button className="btn-ghost" onClick={() => goStep(3, () => { setSeedanceSegments([]); setVideoError('') })}>← 返回</button>
                </div>
              </div>
              {videoLoading && (
                <div className="video-loading">
                  <div className="spinner" />
                  <div>正在生成口播视频片段，请稍候…</div>
                  <p>AI 拆分脚本 → 逐段调用 AtlasCloud，可能需要 3-10 分钟</p>
                </div>
              )}
              {videoError && !videoLoading && (
                <div className="video-error-panel">
                  <div className="video-error-title">生成失败</div>
                  <div className="video-error-msg">{videoError}</div>
                  <button className="btn-ghost" onClick={() => goStep(3, () => setVideoError(''))}>返回重试</button>
                </div>
              )}
              {seedanceSegments.length > 0 && (
                <SeedanceResults segments={seedanceSegments}
                  onReset={() => goStep(3, () => setSeedanceSegments([]))} />
              )}
            </section>
          )}

          {/* 录制声音弹窗 */}
          {showRecordModal && (
            <div className="modal-bg active" onClick={e => {
              if (e.target === e.currentTarget && !isRecording) setShowRecordModal(false)
            }}>
              <div className="modal" style={{maxWidth:420}}>
                {!isRecording && (
                  <button className="btn-ghost" onClick={() => setShowRecordModal(false)}
                    style={{position:'absolute', top:10, right:10, fontSize:18}}>×</button>
                )}
                <h3 style={{margin:'0 0 8px', fontSize:16}}>录制你的声音</h3>
                <p style={{margin:'0 0 12px', fontSize:13, color:'var(--ink-2)', lineHeight:1.6}}>
                  请用<b>自然的语速</b>朗读下面这段文本（<b>4-10 秒</b>），AI 会用你的声纹生成全片口播。
                  <br />确保<b>环境安静</b>、距离麦克风约 20cm。
                </p>

                <div style={{
                  padding:'12px 14px', background:'var(--bone)', borderRadius:'var(--r-sm)',
                  fontSize:13.5, lineHeight:1.7, color:'var(--ink)', marginBottom:16,
                  border:'1px solid var(--line)'
                }}>
                  {RECORD_SCRIPT}
                </div>

                <div style={{display:'flex', justifyContent:'center'}}>
                  {!isRecording ? (
                    <button className="btn-primary" onClick={startRecording}
                      style={{display:'flex', alignItems:'center', gap:6, padding:'10px 24px'}}>
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="12" r="6"/></svg>
                      <span>开始录制</span>
                    </button>
                  ) : (
                    <button className="btn-primary" onClick={stopRecording}
                      style={{display:'flex', alignItems:'center', gap:6, padding:'10px 24px', background:'#c0392b'}}>
                      <span style={{width:10,height:10,borderRadius:2,background:'#fff'}} />
                      <span>停止录制 · {recordSeconds}s / 15s</span>
                    </button>
                  )}
                </div>
              </div>
            </div>
          )}
        </>
      )}

      <ConfirmModal {...confirmProps} />
    </div>
  )
}

function UploadPanel({
  characterPreview, characterUploading,
  backgroundPreview, backgroundUploading,
  charFileRef, bgFileRef,
  onUpload, onClearChar, onClearBg,
  licenseChecked, onLicenseToggle,
  onGenerate, genError,
  videoRatio, onRatioChange,
  videoResolution, onResolutionChange,
}) {
  return (
    <div className="upload-panel">
      <div className="upload-panel-title">出镜人物 <span className="required">*</span></div>

      <input ref={charFileRef} type="file" accept="image/*" style={{ display: 'none' }}
        disabled={characterUploading}
        onChange={(e) => onUpload('character', e.target.files?.[0])} />

      {characterPreview ? (
        <div className="up-preview">
          <img src={characterPreview} alt="出镜人物" className="up-thumb" />
          <div className="up-preview-info">
            {characterUploading
              ? <span className="up-status">上传中…</span>
              : <span className="up-status up-ok">✓ 已上传</span>}
            <button type="button" className="btn-ghost up-btn"
              onClick={() => charFileRef.current?.click()} disabled={characterUploading}>
              重新选择
            </button>
            <button type="button" className="up-remove" onClick={onClearChar} disabled={characterUploading}>
              移除
            </button>
          </div>
        </div>
      ) : (
        <button type="button" className="up-zone" onClick={() => charFileRef.current?.click()}
          disabled={characterUploading}>
          <span className="up-zone-icon">📷</span>
          <span>点击上传人物照片</span>
          <span className="up-zone-hint">JPG / PNG，建议正脸清晰照</span>
        </button>
      )}

      <label className="up-license">
        <input type="checkbox" checked={licenseChecked} onChange={onLicenseToggle} />
        <span>本人已获得出镜人物肖像授权，可用于 AI 视频生成</span>
      </label>

      <div className="upload-panel-divider" />

      <div className="upload-panel-subtitle">背景场景 <span className="up-optional">可选</span></div>

      <input ref={bgFileRef} type="file" accept="image/*" style={{ display: 'none' }}
        disabled={backgroundUploading}
        onChange={(e) => onUpload('background', e.target.files?.[0])} />

      {backgroundPreview ? (
        <div className="up-preview">
          <img src={backgroundPreview} alt="背景场景" className="up-thumb" />
          <div className="up-preview-info">
            {backgroundUploading
              ? <span className="up-status">上传中…</span>
              : <span className="up-status up-ok">✓ 已上传</span>}
            <button type="button" className="btn-ghost up-btn"
              onClick={() => bgFileRef.current?.click()} disabled={backgroundUploading}>
              重新选择
            </button>
            <button type="button" className="up-remove" onClick={onClearBg} disabled={backgroundUploading}>
              移除
            </button>
          </div>
        </div>
      ) : (
        <button type="button" className="up-zone up-zone-sm" onClick={() => bgFileRef.current?.click()}
          disabled={backgroundUploading}>
          <span>+ 上传背景图（可选）</span>
        </button>
      )}

      <div className="upload-panel-divider" />

      {/* 视频参数 */}
      <div className="up-options">
        <div className="up-option-group">
          <div className="up-option-label">画面比例</div>
          <div className="up-option-btns">
            {['9:16', '16:9'].map(r => (
              <button key={r}
                className={'up-option-btn' + (videoRatio === r ? ' is-active' : '')}
                onClick={() => onRatioChange(r)}>
                {r === '9:16' ? '📱 竖屏 9:16' : '🖥 横屏 16:9'}
              </button>
            ))}
          </div>
        </div>
        <div className="up-option-group">
          <div className="up-option-label">清晰度</div>
          <div className="up-option-btns">
            {['720p', '1080p'].map(res => (
              <button key={res}
                className={'up-option-btn' + (videoResolution === res ? ' is-active' : '')}
                onClick={() => onResolutionChange(res)}>
                {res === '1080p' ? '✨ ' : ''}{res}
              </button>
            ))}
          </div>
        </div>
      </div>

      {genError && <div className="kb-error" style={{fontSize:12}}>{genError}</div>}

      <button className="btn-primary up-generate" onClick={onGenerate}>
        生成视频
      </button>
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

function formatTime(seconds) {
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${String(s).padStart(2, '0')}`
}

function ChevronIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
      <polyline points="6 9 12 15 18 9" />
    </svg>
  )
}

function RipShotCard({ shot, index, expanded, onToggle, onUpdate, onRegen, onDelete, onAddAfter }) {
  const charCount = (shot.voiceover || '').replace(/\s/g, '').length
  const secEst = Math.round(charCount / 4)
  const num = String(index + 1).padStart(2, '0')

  return (
    <div className={`rip-shot-card${expanded ? ' expanded' : ''}`}>
      <div className="rip-shot-head" onClick={onToggle}>
        <div className="rip-shot-num">{num}</div>
        <div className="rip-shot-head-info">
          <div className="rip-shot-head-title">分镜 {index + 1}</div>
          <div className="rip-shot-head-meta">
            <span>{shot.shot_type || '中景'}</span>
            <span className="rip-time-bar" />
            <span>{secEst} 秒 · {charCount} 字</span>
          </div>
        </div>
        <span className="rip-shot-arrow"><ChevronIcon /></span>
      </div>

      <div className="rip-shot-body">
        <div className="rip-shot-body-inner">
          <div className="rip-shot-field rip-shot-scene">
            <span className="rip-sf-key">— 画面</span>
            <textarea
              className="rip-sf-textarea"
              value={shot.scene || ''}
              onChange={e => onUpdate('scene', e.target.value)}
              rows={2}
            />
          </div>
          <div className="rip-shot-field rip-shot-line">
            <span className="rip-sf-key">— 口播</span>
            <textarea
              className="rip-sf-textarea"
              value={shot.voiceover || ''}
              onChange={e => onUpdate('voiceover', e.target.value)}
              rows={3}
            />
          </div>
          <div className="rip-shot-field">
            <span className="rip-sf-key">— 备注</span>
            <textarea
              className="rip-sf-textarea"
              value={shot.notes || ''}
              onChange={e => onUpdate('notes', e.target.value)}
              rows={2}
            />
          </div>

          <div className="rip-shot-actions">
            <button className="rip-shot-action-btn" onClick={e => { e.stopPropagation(); onRegen() }}>
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                <polyline points="23 4 23 10 17 10" />
                <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
              </svg>
              <span>重新生成这段</span>
            </button>
            <button className="rip-shot-action-btn" onClick={e => { e.stopPropagation(); onAddAfter() }}>
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                <line x1="12" y1="5" x2="12" y2="19" />
                <line x1="5" y1="12" x2="19" y2="12" />
              </svg>
              <span>在下方加段</span>
            </button>
            <button className="rip-shot-action-btn vk-danger" onClick={e => { e.stopPropagation(); onDelete() }}>
              删除
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

function StoryboardView({ segments, genLoading, onRegen, onDelete, onAddAfter, onInsertAt }) {
  if (!segments || segments.length === 0) {
    return (
      <div className="sb-empty">
        <p>点上方"拆分镜"先生成分镜表</p>
      </div>
    )
  }

  // calculate cumulative start times
  let cumulative = 0
  const withTime = segments.map((seg) => {
    const start = cumulative
    cumulative += seg.duration || 0
    return { ...seg, startTime: start }
  })

  return (
    <div className="sbv-list">
      {withTime.map((seg, i) => (
        <div key={i}>
          <div className="sbv-card">
            <div className="sbv-card-header">
              <div className="sbv-badge">{String(seg.index ?? i + 1).padStart(2, '0')}</div>
              <div className="sbv-card-title">
                <span className="sbv-title-text">分镜 {seg.index ?? i + 1}</span>
                <span className="sbv-timing">
                  {formatTime(seg.startTime)} – {formatTime(seg.startTime + (seg.duration || 0))}
                  <span className="sbv-dot">•</span>
                  {seg.duration || 0} 秒
                  {seg.voiceover && <><span className="sbv-dot">·</span>{seg.voiceover.length} 字</>}
                </span>
              </div>
              <button className="sbv-collapse-btn" onClick={(e) => {
                const card = e.currentTarget.closest('.sbv-card')
                card.classList.toggle('is-collapsed')
              }}>∧</button>
            </div>

            <div className="sbv-card-body">
              <div className="sbv-row">
                <div className="sbv-row-label">— 画面</div>
                <div className="sbv-row-value sbv-scene">{seg.scene}</div>
              </div>
              <div className="sbv-divider" />
              <div className="sbv-row">
                <div className="sbv-row-label">— 口播</div>
                <div className="sbv-row-value sbv-voiceover">{seg.voiceover}</div>
              </div>
              {seg.notes && (
                <>
                  <div className="sbv-divider" />
                  <div className="sbv-row">
                    <div className="sbv-row-label">— 备注</div>
                    <div className="sbv-row-value sbv-notes">{seg.notes}</div>
                  </div>
                </>
              )}

              <div className="sbv-actions">
                <button className="sbv-btn" onClick={() => onRegen(i)} disabled={genLoading}>
                  ↻ 重新生成这段
                </button>
                <button className="sbv-btn" onClick={() => onAddAfter(i)}>
                  + 在下方加段
                </button>
                <button className="sbv-btn sbv-btn-danger" onClick={() => onDelete(i)}>
                  删除
                </button>
              </div>
            </div>
          </div>

          <button className="sbv-insert" onClick={() => onInsertAt(i + 1)}>
            + 在这里插入新分镜
          </button>
        </div>
      ))}
    </div>
  )
}

function SeedanceResults({ segments, onReset }) {
  const [merging, setMerging] = useState(false)
  const [mergeError, setMergeError] = useState('')
  const [mergedUrl, setMergedUrl] = useState('')

  const handleMerge = async () => {
    setMerging(true)
    setMergeError('')
    setMergedUrl('')
    try {
      const urls = segments.map(s => s.videoUrl).filter(Boolean)
      const r = await mergeVideos(urls)
      setMergedUrl(r?.url || '')
    } catch (err) {
      setMergeError(err.message)
    } finally {
      setMerging(false)
    }
  }

  return (
    <div className="seedance-results">
      <div className="seedance-header">
        <h4>口播视频片段（共 {segments.length} 段）</h4>
        <div style={{ display: 'flex', gap: 8 }}>
          {segments.length > 1 && (
            <button className="btn-primary" onClick={handleMerge} disabled={merging}>
              {merging ? '拼接中…' : '▶ 合并成一个视频'}
            </button>
          )}
          <button className="btn-ghost" onClick={onReset}>重新生成</button>
        </div>
      </div>

      {/* 合并结果 */}
      {merging && (
        <div className="video-loading" style={{ padding: '20px' }}>
          <div className="spinner" />
          <div>正在下载并拼接片段，请稍候…</div>
        </div>
      )}
      {mergeError && <div className="kb-error">{mergeError}</div>}
      {mergedUrl && (
        <div className="seedance-merged">
          <div className="seedance-merged-title">✓ 合并完成</div>
          <video src={mergedUrl} controls playsInline className="seedance-video" />
          <div className="seedance-actions">
            <a className="btn-primary" href={mergedUrl} download="merged.mp4">⬇ 下载合并视频</a>
          </div>
        </div>
      )}

      {/* 各段详情 */}
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
