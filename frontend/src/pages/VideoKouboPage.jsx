import { useState, useRef, useEffect } from 'react'
import { callAgent, generateSeedanceSegment, mergeVideos } from '../api'
import ConfirmModal, { useConfirmModal } from '../components/ConfirmModal'
import './VideoKouboPage.css'

// ─── Constants ────────────────────────────────────────────────────────────────

const PLATFORMS = [
  { id: 'douyin', label: '抖音', logo: '抖', meta: '30-60 秒 · 快节奏 · 强钩子' },
  { id: 'xhs', label: '小红书', logo: '小', meta: '60-90 秒 · 干货密度 · 清单感' },
  { id: 'sph', label: '视频号', logo: '视', meta: '60-180 秒 · 故事化 · 生活感' },
]

const DURATION_OPTIONS = [
  { id: 'short', label: '短 30-45 秒', targetMin: 30, targetMax: 45 },
  { id: 'medium', label: '中 45-75 秒', targetMin: 45, targetMax: 75 },
  { id: 'long', label: '长 75-120 秒', targetMin: 75, targetMax: 120 },
]

const INSURANCE_TYPES = ['医疗险', '重疾险', '寿险', '意外险', '年金险', '储蓄险']
const AUDIENCE_OPTIONS = ['中产', '宝妈', '高净值', '银发族', '单身青年', '创业者', '全职主妇']

const STYLE_OPTIONS = [
  { id: 'personal', label: '我的个人风格', desc: '上传素材后训练你的专属风格' },
  { id: 'professional', label: '通用专业风格', desc: '严谨、引经据典、数据驱动' },
  { id: 'warm', label: '通用温暖风格', desc: '亲切、生活化、像跟朋友聊天' },
  { id: 'sharp', label: '通用犀利风格', desc: '痛点直击、节奏快、容易爆' },
]

const VOICE_TEMPLATES = [
  { id: 'f1', label: '女声 1', file: '/voice/女 1.mp3', tone: '温柔亲切' },
  { id: 'f2', label: '女声 2', file: '/voice/女 2.mp3', tone: '清晰干练' },
  { id: 'm1', label: '男声 1', file: '/voice/男 1.mp3', tone: '沉稳专业' },
  { id: 'm2', label: '男声 2', file: '/voice/男 2.mp3', tone: '年轻有力' },
]

// Blob → base64 data URL（用于把音频喂给 Seedance）
function blobToDataUrl(blob) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = reject
    reader.readAsDataURL(blob)
  })
}

// URL → base64 data URL（拉取模板音频再转 base64）
async function urlToDataUrl(url) {
  const res = await fetch(url)
  const blob = await res.blob()
  return blobToDataUrl(blob)
}

const STEPS = [
  { id: 1, label: '选题与平台' },
  { id: 2, label: '分镜脚本' },
  { id: 3, label: '视频预览' },
]

// ─── Helpers ──────────────────────────────────────────────────────────────────

function parseStoryboardJson(raw) {
  if (!raw) return []
  try {
    const clean = raw.replace(/```[a-z]*/gi, '').replace(/```/g, '').trim()
    const parsed = JSON.parse(clean)
    if (Array.isArray(parsed?.segments)) return parsed.segments
    if (Array.isArray(parsed)) return parsed
  } catch {}
  return []
}

function stripTitlePrefix(s) {
  return (s || '')
    .replace(/^\s*\d+[.、)]\s*/, '')           // 1. / 1、 / 1)
    .replace(/^\s*[\[【][^\]】]+[\]】]\s*/, '') // [xxx] 或 【xxx】
    .replace(/^["“]|["”]$/g, '')               // 首尾引号
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
  // fall back: split by newlines
  return raw.split('\n').filter(l => l.trim()).slice(0, 3).map((t, i) => ({
    text: stripTitlePrefix(t),
    style: ['数据型', '悬念型', '冲突型'][i] || '通用',
  }))
}

function computeTotalSeconds(shots) {
  return shots.reduce((s, sh) => s + (Number(sh.duration) || 0), 0)
}

function formatSeconds(s) {
  const m = Math.floor(s / 60)
  const sec = s % 60
  return m > 0 ? `${m}:${String(sec).padStart(2, '0')}` : `${sec} 秒`
}

async function uploadFile(file) {
  const form = new FormData()
  form.append('file', file)
  const res = await fetch('/api/avatar/upload', { method: 'POST', body: form })
  const data = await res.json()
  if (!res.ok || data.error) throw new Error(data.error || '上传失败')
  return data.url
}

// ─── Sub-components ───────────────────────────────────────────────────────────

function ChevronIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
      <polyline points="6 9 12 15 18 9" />
    </svg>
  )
}

function ShotCard({ shot, index, expanded, onToggle, onUpdate, onRegen, onDelete, onAddAfter }) {
  const charCount = (shot.voiceover || '').replace(/\s/g, '').length
  const secEst = Math.round(charCount / 4)
  const num = String(index + 1).padStart(2, '0')

  return (
    <div className={`vk-shot-card${expanded ? ' expanded' : ''}`}>
      <div className="vk-shot-head" onClick={onToggle}>
        <div className="vk-shot-num">{num}</div>
        <div className="vk-shot-head-info">
          <div className="vk-shot-head-title">分镜 {index + 1}</div>
          <div className="vk-shot-head-meta">
            <span>{shot.shot_type || '中景'}</span>
            <span className="vk-time-bar" />
            <span>{secEst} 秒 · {charCount} 字</span>
          </div>
        </div>
        <span className="vk-shot-arrow"><ChevronIcon /></span>
      </div>

      <div className="vk-shot-body">
        <div className="vk-shot-body-inner">
          <div className="vk-shot-field vk-shot-scene">
            <span className="vk-sf-key">— 画面</span>
            <textarea
              className="vk-sf-textarea"
              value={shot.scene || ''}
              onChange={e => onUpdate('scene', e.target.value)}
              rows={2}
            />
          </div>
          <div className="vk-shot-field vk-shot-line">
            <span className="vk-sf-key">— 口播</span>
            <textarea
              className="vk-sf-textarea vk-sf-line"
              value={shot.voiceover || ''}
              onChange={e => onUpdate('voiceover', e.target.value)}
              rows={3}
            />
          </div>
          <div className="vk-shot-field">
            <span className="vk-sf-key">— 备注</span>
            <textarea
              className="vk-sf-textarea"
              value={shot.notes || ''}
              onChange={e => onUpdate('notes', e.target.value)}
              rows={2}
            />
          </div>

          <div className="vk-shot-actions">
            <button className="vk-shot-action-btn" onClick={e => { e.stopPropagation(); onRegen() }}>
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                <polyline points="23 4 23 10 17 10" />
                <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
              </svg>
              <span>重新生成这段</span>
            </button>
            <button className="vk-shot-action-btn" onClick={e => { e.stopPropagation(); onAddAfter() }}>
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                <line x1="12" y1="5" x2="12" y2="19" />
                <line x1="5" y1="12" x2="19" y2="12" />
              </svg>
              <span>在下方加段</span>
            </button>
            <button className="vk-shot-action-btn vk-danger" onClick={e => { e.stopPropagation(); onDelete() }}>
              删除
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

// ─── Main Component ───────────────────────────────────────────────────────────

export default function VideoKouboPage({ topicPrefill, onPrefillConsumed, isActive }) {
  const [step, setStep] = useState(1)

  // 仅在 step 2（分镜脚本）禁用外层 .content 的上下滚动
  // step 1 / 3 / 离开本页 → 恢复正常滚动
  useEffect(() => {
    const outer = document.querySelector('.content.scroll-y')
    if (!outer) return
    if (!isActive || step !== 2) return
    const originalOverflow = outer.style.overflowY
    outer.style.overflowY = 'hidden'
    outer.scrollTop = 0
    return () => {
      outer.style.overflowY = originalOverflow
    }
  }, [isActive, step])

  // 每次切换 step 时，把页内可滚的子区域重置到顶部
  useEffect(() => {
    if (!isActive) return
    const resetScroll = () => {
      document.querySelectorAll('.vk-storyboard-main, .vk-aux-panel').forEach(el => {
        el.scrollTop = 0
      })
    }
    resetScroll()
    const raf = requestAnimationFrame(resetScroll)
    const t = setTimeout(resetScroll, 100)
    return () => {
      cancelAnimationFrame(raf)
      clearTimeout(t)
    }
  }, [isActive, step])

  // Step 1 form
  const [topic, setTopic] = useState(topicPrefill?.topic || '')
  const [platform, setPlatform] = useState('')
  const [direction, setDirection] = useState('')
  const [duration, setDuration] = useState('')
  const [customDuration, setCustomDuration] = useState(60)
  const [insuranceTypes, setInsuranceTypes] = useState([])
  const [audiences, setAudiences] = useState([])
  const [styleOption, setStyleOption] = useState('')
  const [refMaterials, setRefMaterials] = useState([])

  // Step 2 storyboard
  const [shots, setShots] = useState([])
  const [expandedShots, setExpandedShots] = useState(new Set([0]))
  const [titles, setTitles] = useState([])
  const [selectedTitle, setSelectedTitle] = useState(0)
  const [citations, setCitations] = useState([])

  // Step 2 production
  const [characterImageUrl, setCharacterImageUrl] = useState('')
  const [characterPreview, setCharacterPreview] = useState(null)
  const [characterUploading, setCharacterUploading] = useState(false)
  const [backgroundImageUrl, setBackgroundImageUrl] = useState('')
  const [backgroundPreview, setBackgroundPreview] = useState(null)
  const [backgroundUploading, setBackgroundUploading] = useState(false)
  const [licenseChecked, setLicenseChecked] = useState(false)

  // Voice reference (Seedance reference_audios)
  const [voiceMode, setVoiceMode] = useState('')            // 'template' | 'recording'
  const [selectedVoiceTemplate, setSelectedVoiceTemplate] = useState('')
  const [recordedAudioUrl, setRecordedAudioUrl] = useState('')  // 本地 ObjectURL，用于回放
  const [recordedAudioData, setRecordedAudioData] = useState('') // base64 data URL，送给后端
  const [isRecording, setIsRecording] = useState(false)
  const [recordSeconds, setRecordSeconds] = useState(0)
  const [showRecordModal, setShowRecordModal] = useState(false)
  const mediaRecorderRef = useRef(null)
  const recordTimerRef = useRef(null)

  const RECORD_SCRIPT = '买保险这件事，其实没有想象中那么复杂，关键是看清自己真正的需要，再做选择。'

  // Step 3 video
  const [seedanceSegments, setSeedanceSegments] = useState([])
  const [videoLoading, setVideoLoading] = useState(false)
  const [videoError, setVideoError] = useState('')
  const [mergedVideoUrl, setMergedVideoUrl] = useState('')
  const [mergeLoading, setMergeLoading] = useState(false)

  // UI state
  const [genLoading, setGenLoading] = useState(false)
  const [genError, setGenError] = useState('')
  const [showRefModal, setShowRefModal] = useState(false)
  const [refTab, setRefTab] = useState(0)
  const [refLink, setRefLink] = useState('')

  const charFileRef = useRef(null)
  const bgFileRef = useRef(null)

  // ── prefill ──────────────────────────────────────────────────────────────
  if (topicPrefill?.topic && !topic) {
    setTopic(topicPrefill.topic)
    if (topicPrefill.angle) setDirection(topicPrefill.angle)
    onPrefillConsumed?.()
  }

  // ── toggle helpers ────────────────────────────────────────────────────────
  function toggleChip(list, setter, val) {
    setter(list.includes(val) ? list.filter(v => v !== val) : [...list, val])
  }

  function toggleExpanded(i) {
    setExpandedShots(prev => {
      const s = new Set(prev)
      s.has(i) ? s.delete(i) : s.add(i)
      return s
    })
  }

  // ── shot edit ─────────────────────────────────────────────────────────────
  function updateShot(index, field, value) {
    setShots(prev => prev.map((s, i) => i === index ? { ...s, [field]: value } : s))
  }

  function deleteShot(index) {
    setShots(prev => prev.filter((_, i) => i !== index).map((s, i) => ({ ...s, index: i + 1 })))
    setExpandedShots(prev => {
      const s = new Set()
      prev.forEach(n => { if (n < index) s.add(n); else if (n > index) s.add(n - 1) })
      return s
    })
  }

  function addShotAfter(index) {
    const newShot = { index: 0, shot_type: '中景', scene: '（待填写）', voiceover: '（待填写）', notes: '', duration: 6 }
    setShots(prev => {
      const next = [...prev]
      next.splice(index + 1, 0, newShot)
      return next.map((s, i) => ({ ...s, index: i + 1 }))
    })
    setExpandedShots(prev => new Set([...prev, index + 1]))
  }

  function insertShotAt(insertIndex) {
    const newShot = { index: 0, shot_type: '中景', scene: '（待填写）', voiceover: '（待填写）', notes: '', duration: 6 }
    setShots(prev => {
      const next = [...prev]
      next.splice(insertIndex, 0, newShot)
      return next.map((s, i) => ({ ...s, index: i + 1 }))
    })
    setExpandedShots(prev => new Set([...prev, insertIndex]))
  }

  // ── regen one shot ────────────────────────────────────────────────────────
  async function regenShot(index) {
    const shot = shots[index]
    if (!shot) return
    setGenLoading(true)
    try {
      const r = await callAgent('video-storyboard', {
        topic,
        platform,
        duration,
        style: `只重新生成第 ${index + 1} 个分镜，当前口播为："${shot.voiceover}"。请保持整体风格，输出单个 segment 对象（含 shot_type/scene/voiceover/notes/duration 字段）`,
      }, 'rag-video')
      try {
        const raw = (r?.content || '').replace(/```[a-z]*/gi, '').replace(/```/g, '').trim()
        const parsed = JSON.parse(raw)
        const newShot = parsed?.segments?.[0] ?? parsed
        if (newShot?.voiceover) {
          setShots(prev => prev.map((s, i) => i === index ? { ...newShot, index: i + 1 } : s))
        }
      } catch {}
    } catch (err) {
      setGenError(err.message)
    } finally {
      setGenLoading(false)
    }
  }

  // ── step 1 → 2: generate storyboard ──────────────────────────────────────
  async function handleGenStoryboard() {
    if (!topic.trim()) { setGenError('请填写选题主题'); return }
    if (!platform) { setGenError('请选择发布平台'); return }
    setGenLoading(true)
    setGenError('')
    try {
      const durOpt = DURATION_OPTIONS.find(d => d.id === duration)
      let durationText = duration
      if (durOpt) durationText = `${durOpt.label}（${durOpt.targetMin}-${durOpt.targetMax} 秒）`
      else if (duration === 'custom') durationText = `自定义 ${customDuration} 秒（严格按此时长生成，允许 ±5 秒浮动）`

      const payload = {
        topic,
        platform: PLATFORMS.find(p => p.id === platform)?.label || platform,
        direction,
        duration: durationText,
        insuranceTypes,
        audiences,
        style: styleOption,
      }
      const [sbResult, titleResult] = await Promise.all([
        callAgent('video-storyboard', payload, 'rag-video'),
        callAgent('title', { topic, direction, platform: payload.platform, count: 3 }, 'chat'),
      ])

      const parsedShots = parseStoryboardJson(sbResult?.content || '')
      const parsedTitles = parseTitles(titleResult?.content || '')

      setShots(parsedShots)
      setTitles(parsedTitles)
      setSelectedTitle(0)
      if (sbResult?.citations) setCitations(sbResult.citations)

      setExpandedShots(new Set(parsedShots.map((_, i) => i)))

      setStep(2)
    } catch (err) {
      setGenError(err.message)
    } finally {
      setGenLoading(false)
    }
  }

  // ── step 2 → 3: generate video ────────────────────────────────────────────
  async function goGenerate() {
    if (characterImageUrl && !licenseChecked) {
      setGenError('请勾选人物授权声明')
      return
    }
    setGenError('')
    setVideoError('')
    setSeedanceSegments([])
    setMergedVideoUrl('')
    setStep(3)

    setVideoLoading(true)
    let previousLastFrame = null
    const accumulated = []
    try {
      for (let i = 0; i < shots.length; i++) {
        const s = shots[i]
        const payload = {
          segment: {
            voiceover: s.voiceover,
            duration: s.duration,
            prompt: s.scene || '',
          },
          characterImageUrl: characterImageUrl || null,
          backgroundImageUrl: backgroundImageUrl || null,
          previousLastFrameUrl: previousLastFrame,
          referenceAudioUrl: recordedAudioData || null,
          index: i,
          total: shots.length,
        }
        const r = await generateSeedanceSegment(payload)
        if (!r?.videoUrl) throw new Error(`第 ${i + 1} 段生成失败`)
        accumulated.push({
          index: r.index,
          videoUrl: r.videoUrl,
          durationEstimate: r.durationEstimate,
        })
        // 实时更新前端，让用户立即看到这一段已完成
        setSeedanceSegments([...accumulated])
        previousLastFrame = r.lastFrameUrl || null
      }
      setVideoLoading(false)

      // 所有分段生成完后，自动调用拼接
      const urls = accumulated.map(s => s.videoUrl).filter(Boolean)
      if (urls.length >= 2) {
        setMergeLoading(true)
        try {
          const merged = await mergeVideos(urls)
          if (merged?.url) setMergedVideoUrl(merged.url)
        } catch (err) {
          setVideoError('视频拼接失败：' + err.message)
        } finally {
          setMergeLoading(false)
        }
      } else if (urls.length === 1) {
        setMergedVideoUrl(urls[0])
      }
    } catch (err) {
      setVideoError(err.message)
      setVideoLoading(false)
    }
  }

  // ── upload character photo ─────────────────────────────────────────────────
  async function handleCharUpload(file) {
    if (!file) return
    setCharacterPreview(URL.createObjectURL(file))
    setCharacterUploading(true)
    try {
      const url = await uploadFile(file)
      setCharacterImageUrl(url)
    } catch (err) {
      alert('上传失败：' + err.message)
      setCharacterPreview(null)
      setCharacterImageUrl('')
    } finally {
      setCharacterUploading(false)
    }
  }

  // ── voice reference: template select ─────────────────────────────────────
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

  // ── voice reference: record ──────────────────────────────────────────────
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
          if (s + 1 >= 15) { // 上限 15s，触发自动停止
            stopRecording()
            return 15
          }
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

  async function handleBgUpload(file) {
    if (!file) return
    setBackgroundPreview(URL.createObjectURL(file))
    setBackgroundUploading(true)
    try {
      const url = await uploadFile(file)
      setBackgroundImageUrl(url)
    } catch (err) {
      alert('上传失败：' + err.message)
      setBackgroundPreview(null)
      setBackgroundImageUrl('')
    } finally {
      setBackgroundUploading(false)
    }
  }

  // ── add ref material (link) ────────────────────────────────────────────────
  function addRefLink() {
    if (!refLink.trim()) return
    setRefMaterials(prev => [...prev, { type: 'link', name: refLink.trim() }])
    setRefLink('')
    setShowRefModal(false)
  }

  // ── computed ──────────────────────────────────────────────────────────────
  const totalSeconds = computeTotalSeconds(shots)
  const durationOpt = DURATION_OPTIONS.find(d => d.id === duration)
  const durationOk = durationOpt ? totalSeconds >= durationOpt.targetMin && totalSeconds <= durationOpt.targetMax : false
  const durationPct = durationOpt ? Math.min(100, Math.round((totalSeconds / durationOpt.targetMax) * 100)) : 0
  const platformLabel = PLATFORMS.find(p => p.id === platform)?.label || ''
  const selectedTitleText = titles[selectedTitle]?.text || ''

  // ── 步骤切换 + 返回确认 ──────────────────────────────────────────────────
  const { confirm, props: confirmProps } = useConfirmModal()

  const goStep = (n) => {
    if (n < 1 || n > 3 || n === step) return
    const doGo = () => setStep(n)
    if (n < step) {
      const willLose = []
      if (n < 2 && step >= 2 && (shots.length > 0 || titles.length > 0)) willLose.push('分镜脚本与标题')
      if (n < 3 && step >= 3 && (seedanceSegments.length > 0 || mergedVideoUrl)) willLose.push('已生成的视频')
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

  // ── back/next bar logic ───────────────────────────────────────────────────
  function handleBack() {
    if (step > 1) goStep(step - 1)
  }

  function handleNext() {
    if (step === 1) handleGenStoryboard()
    else if (step === 2) goGenerate()
    else goStep(1)
  }

  const STEP_CONFIG = {
    1: { backText: '返回选题广场', nextText: '生成分镜脚本', credits: '本步骤将消耗 8 积分' },
    2: { backText: '重做需求', nextText: '下一步：生成视频', credits: '本步骤已消耗 8 积分' },
    3: { backText: '回到分镜脚本', nextText: '完成 / 关闭', credits: '本步骤已消耗 80 积分' },
  }
  const sc = STEP_CONFIG[step]

  // ─── Render ──────────────────────────────────────────────────────────────

  return (
    <div className="vk-page">

      {/* ── Page Head ─────────────────────────────────────────── */}
      <div className="vk-page-head">
        <div className="vk-title-row">
          <div className="vk-title">
            {platform && <span className={`vk-platform-tag vk-platform-tag--${platform}`}>{platformLabel}</span>}
            <h2>口播视频</h2>
            {topic && (
              <span className="vk-topic-from">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
                  <path d="M12 2 L13.5 8.5 L20 10 L13.5 11.5 L12 18 L10.5 11.5 L4 10 L10.5 8.5 Z" />
                </svg>
                主题：<b>{topic}</b>
              </span>
            )}
          </div>
          <div className="vk-page-actions">
            <button className="vk-btn-draft">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z" />
              </svg>
              <span>保存为草稿</span>
            </button>
          </div>
        </div>

        <div className="vk-steps">
          {STEPS.map(s => (
            <div
              key={s.id}
              className={`vk-step${step === s.id ? ' active' : step > s.id ? ' done' : ''}`}
              onClick={() => step > s.id && goStep(s.id)}
            >
              <div className="vk-step-dot">{step > s.id ? '✓' : s.id}</div>
              <div className="vk-step-label">{s.label}</div>
            </div>
          ))}
        </div>
      </div>

      {/* ── Content Area ──────────────────────────────────────── */}
      <div className="vk-content">
        <div className="vk-content-inner">

          {/* ===== STEP 1 ===== */}
          {step === 1 && (
            <div className="vk-section-card">
              <div className="vk-sc-head">
                <h3>告诉 AI 你想拍什么</h3>
                <span className="vk-sc-desc">AI 会基于爆款样本库和平台特性生成对应风格的分镜脚本</span>
              </div>
              <div className="vk-sc-body">

                {/* 选题主题 */}
                <div className="vk-form-row">
                  <div className="vk-form-label">选题主题 <span className="vk-req">*</span></div>
                  <div className="vk-form-control">
                    <input
                      type="text"
                      className="vk-text-field"
                      value={topic}
                      onChange={e => setTopic(e.target.value)}
                      placeholder="例：医保只报 60%，剩下的谁来兜？"
                    />
                  </div>
                </div>

                {/* 发布平台 */}
                <div className="vk-form-row">
                  <div className="vk-form-label">发布平台 <span className="vk-req">*</span></div>
                  <div className="vk-form-control">
                    <div className="vk-platform-cards">
                      {PLATFORMS.map(p => (
                        <div
                          key={p.id}
                          className={`vk-platform-card vk-p-${p.id}${platform === p.id ? ' selected' : ''}`}
                          onClick={() => setPlatform(p.id)}
                        >
                          <div className={`vk-p-logo ${p.id}`}>{p.logo}</div>
                          <div className="vk-p-name">{p.label}</div>
                          <div className="vk-p-meta">{p.meta}</div>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>

                {/* 主要内容方向 */}
                <div className="vk-form-row">
                  <div className="vk-form-label">内容方向</div>
                  <div className="vk-form-control">
                    <textarea
                      className="vk-text-field"
                      value={direction}
                      onChange={e => setDirection(e.target.value)}
                      placeholder="（选填）告诉 AI 你想强调的角度、叙事结构、开头方式等"
                    />
                  </div>
                </div>

                {/* 参考材料 */}
                <div className="vk-form-row">
                  <div className="vk-form-label">参考材料</div>
                  <div className="vk-form-control">
                    {refMaterials.length > 0 && (
                      <div className="vk-refs-list">
                        {refMaterials.map((ref, i) => (
                          <div key={i} className="vk-ref-item">
                            <div className={`vk-ref-ico vk-ref-${ref.type}`}>
                              {ref.type === 'pdf' ? (
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" /><polyline points="14 2 14 8 20 8" />
                                </svg>
                              ) : (
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                                  <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" /><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
                                </svg>
                              )}
                            </div>
                            <span className="vk-ref-name">{ref.name}</span>
                            <button className="vk-ref-remove" onClick={() => setRefMaterials(prev => prev.filter((_, j) => j !== i))}>×</button>
                          </div>
                        ))}
                      </div>
                    )}
                    <div className="vk-ref-actions">
                      <button className="vk-btn-add-ref" onClick={() => setShowRefModal(true)}>
                        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
                          <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
                        </svg>
                        <span>添加参考材料</span>
                      </button>
                      <span className="vk-ref-hint">支持 PDF / DOC / 图片 / 链接 · 仅本次使用</span>
                    </div>
                  </div>
                </div>

                {/* 视频时长 */}
                <div className="vk-form-row">
                  <div className="vk-form-label">视频时长 <span className="vk-req">*</span></div>
                  <div className="vk-form-control">
                    <div className="vk-chip-group">
                      {DURATION_OPTIONS.map(d => (
                        <span
                          key={d.id}
                          className={`vk-chip${duration === d.id ? ' selected' : ''}`}
                          onClick={() => setDuration(d.id)}
                        >
                          {d.label}
                        </span>
                      ))}
                      <span
                        className={`vk-chip${duration === 'custom' ? ' selected' : ''}`}
                        onClick={() => setDuration('custom')}
                      >
                        自定义
                      </span>
                      {duration === 'custom' && (
                        <span className="vk-custom-duration">
                          <input
                            type="text"
                            inputMode="numeric"
                            value={customDuration}
                            onChange={e => setCustomDuration(e.target.value.replace(/\D/g, '').slice(0, 3))}
                            className="vk-custom-duration-input"
                          />
                          <span>秒</span>
                        </span>
                      )}
                    </div>
                  </div>
                </div>

                {/* 险种 */}
                <div className="vk-form-row">
                  <div className="vk-form-label">险种 <span className="vk-req">*</span></div>
                  <div className="vk-form-control">
                    <div className="vk-chip-group">
                      {INSURANCE_TYPES.map(t => (
                        <span
                          key={t}
                          className={`vk-chip${insuranceTypes.includes(t) ? ' selected' : ''}`}
                          onClick={() => toggleChip(insuranceTypes, setInsuranceTypes, t)}
                        >
                          {t}
                        </span>
                      ))}
                    </div>
                  </div>
                </div>

                {/* 目标人群 */}
                <div className="vk-form-row">
                  <div className="vk-form-label">目标人群 <span className="vk-req">*</span></div>
                  <div className="vk-form-control">
                    <div className="vk-chip-group">
                      {AUDIENCE_OPTIONS.map(a => (
                        <span
                          key={a}
                          className={`vk-chip${audiences.includes(a) ? ' selected' : ''}`}
                          onClick={() => toggleChip(audiences, setAudiences, a)}
                        >
                          {a}
                        </span>
                      ))}
                    </div>
                  </div>
                </div>

                {/* 写作风格 */}
                <div className="vk-form-row">
                  <div className="vk-form-label">写作风格 <span className="vk-req">*</span></div>
                  <div className="vk-form-control">
                    <div className="vk-style-options">
                      {STYLE_OPTIONS.map(s => (
                        <div
                          key={s.id}
                          className={`vk-style-option${styleOption === s.id ? ' selected' : ''}`}
                          onClick={() => setStyleOption(s.id)}
                        >
                          <div className="vk-style-radio" />
                          <div className="vk-style-info">
                            <strong>
                              {s.label}
                              {s.tag && <span className="vk-ver-tag">{s.tag}</span>}
                            </strong>
                            <span>{s.desc}</span>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>

              </div>
            </div>
          )}

          {/* ===== STEP 2 ===== */}
          {step === 2 && (
            <div className="vk-storyboard-grid">

              {/* 左主区 */}
              <div className="vk-storyboard-main">
                {/* 标题候选 — 放在分镜列表上方 */}
                {titles.length > 0 && (
                  <div className="vk-aux-card vk-titles-top">
                    <div className="vk-aux-head">
                      <span>— Titles · {titles.length} 个标题候选</span>
                    </div>
                    <div className="vk-aux-body">
                      <div className="vk-title-cards">
                        {titles.map((t, i) => (
                          <div
                            key={i}
                            className={`vk-title-card${selectedTitle === i ? ' selected' : ''}`}
                            onClick={() => setSelectedTitle(i)}
                          >
                            <span className="vk-style-label">{t.style || `方案 ${i + 1}`}</span>
                            <span className="vk-title-text">{t.text}</span>
                            <div className="vk-title-check">✓</div>
                          </div>
                        ))}
                      </div>
                    </div>
                  </div>
                )}

                <div className="vk-storyboard-toolbar">
                  <div className="vk-toolbar-stats">
                    <div className="vk-toolbar-stat">
                      <span>分镜：</span><b>{shots.length} 个</b>
                    </div>
                    <div className="vk-toolbar-stat">
                      <span>预估时长：</span><b>约 {totalSeconds} 秒</b>
                    </div>
                  </div>
                </div>

                {shots.length === 0 && !genLoading && (
                  <div className="vk-empty-shots">
                    <p>暂无分镜 — 生成时出现问题，请点击「整篇重新生成」</p>
                  </div>
                )}

                <div className="vk-shot-list">
                  {shots.map((shot, i) => (
                    <div key={i}>
                      <ShotCard
                        shot={shot}
                        index={i}
                        expanded={expandedShots.has(i)}
                        onToggle={() => toggleExpanded(i)}
                        onUpdate={(field, val) => updateShot(i, field, val)}
                        onRegen={() => regenShot(i)}
                        onDelete={() => deleteShot(i)}
                        onAddAfter={() => addShotAfter(i)}
                      />
                      {i < shots.length - 1 && (
                        <button className="vk-insert-shot-btn" onClick={() => insertShotAt(i + 1)}>
                          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                            <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
                          </svg>
                          <span>在这里插入新分镜</span>
                        </button>
                      )}
                    </div>
                  ))}

                  {shots.length > 0 && (
                    <button className="vk-insert-shot-btn" onClick={() => addShotAfter(shots.length - 1)}>
                      <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                        <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
                      </svg>
                      <span>在末尾添加新分镜</span>
                    </button>
                  )}
                </div>
              </div>

              {/* 右辅助栏 */}
              <div className="vk-aux-panel">

                {/* 拍摄要素 */}
                <div className="vk-aux-card">
                  <div className="vk-aux-head">
                    <span>— Production · 拍摄要素</span>
                  </div>

                  <div className="vk-shoot-pair">
                    <div className="vk-shoot-row vk-shoot-half">
                      <div className="vk-se-label">出镜人物 <span className="vk-req">*</span></div>
                      <input
                        ref={charFileRef}
                        type="file"
                        accept="image/*"
                        style={{ display: 'none' }}
                        onChange={e => handleCharUpload(e.target.files?.[0])}
                      />
                      <div
                        className={`vk-portrait-zone${characterPreview ? ' has-image' : ''}`}
                        onClick={() => charFileRef.current?.click()}
                      >
                        {characterPreview ? (
                          <>
                            <img src={characterPreview} alt="出镜人物" className="vk-portrait-preview-img" />
                            <div className="vk-change-overlay">
                              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="17 8 12 3 7 8" /><line x1="12" y1="3" x2="12" y2="15" />
                              </svg>
                              <span>更换照片</span>
                            </div>
                          </>
                        ) : (
                          <div className="vk-upload-prompt">
                            <div className="vk-upload-ico">
                              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="17 8 12 3 7 8" /><line x1="12" y1="3" x2="12" y2="15" />
                              </svg>
                            </div>
                            <div className="vk-upload-text">上传出镜照片</div>
                            <div className="vk-upload-hint">{characterUploading ? '上传中…' : 'JPG / PNG · 建议正面免冠'}</div>
                          </div>
                        )}
                      </div>
                    </div>

                    <div className="vk-shoot-row vk-shoot-half">
                      <div className="vk-se-label">场景背景</div>
                      <input
                        ref={bgFileRef}
                        type="file"
                        accept="image/*"
                        style={{ display: 'none' }}
                        onChange={e => handleBgUpload(e.target.files?.[0])}
                      />
                      <div
                        className={`vk-scene-zone${backgroundPreview ? ' has-image' : ''}`}
                        onClick={() => bgFileRef.current?.click()}
                      >
                        <div className="vk-scene-ico">
                          {backgroundPreview ? (
                            <img src={backgroundPreview} alt="背景" style={{ width: '100%', height: '100%', objectFit: 'cover', borderRadius: 'var(--r-sm)' }} />
                          ) : (
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                              <rect x="3" y="3" width="18" height="18" rx="2" /><circle cx="8.5" cy="8.5" r="1.5" /><polyline points="21 15 16 10 5 21" />
                            </svg>
                          )}
                        </div>
                        <div className="vk-scene-info">
                          <strong>{backgroundPreview ? '已上传背景图' : '未上传 · AI 自适应'}</strong>
                          <span>{backgroundUploading ? '上传中…' : backgroundPreview ? '点击更换背景图' : '根据脚本内容自动匹配背景'}</span>
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* 授权声明独立一行 */}
                  <div className="vk-shoot-row">
                    <div
                      className={`vk-license-row${licenseChecked ? ' checked' : ''}`}
                      onClick={() => setLicenseChecked(v => !v)}
                    >
                      <div className="vk-license-check" />
                      <label>我承诺此照片为<b>本人</b>或<b>已获本人书面授权</b>，授权承知使用此照片生成 AI 视频内容。</label>
                    </div>
                  </div>

                  {/* 声音参考 — 保证多段视频音色一致 */}
                  <div className="vk-shoot-row">
                    <div className="vk-se-label">声音参考</div>
                    <div className="vk-voice-templates">
                      {VOICE_TEMPLATES.map(t => (
                        <div
                          key={t.id}
                          className={`vk-voice-card${selectedVoiceTemplate === t.id ? ' selected' : ''}`}
                          onClick={() => pickVoiceTemplate(t.id)}
                        >
                          <div className="vk-voice-ico">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                              <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/><path d="M19 10v2a7 7 0 0 1-14 0v-2"/><line x1="12" y1="19" x2="12" y2="23"/><line x1="8" y1="23" x2="16" y2="23"/>
                            </svg>
                          </div>
                          <div className="vk-voice-info">
                            <strong>{t.label}</strong>
                            <span>{t.tone}</span>
                          </div>
                          <audio
                            src={t.file}
                            controls
                            preload="none"
                            className="vk-voice-audio"
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

                    <div className="vk-voice-record">
                      {!isRecording && !recordedAudioUrl && (
                        <button className="vk-btn-record" onClick={() => setShowRecordModal(true)}>
                          <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="12" r="6"/></svg>
                          <span>录制我的声音</span>
                          <span className="vk-record-hint">2-15 秒</span>
                        </button>
                      )}
                      {!isRecording && recordedAudioUrl && (
                        <div className="vk-voice-recorded">
                          <audio
                            src={recordedAudioUrl}
                            controls
                            className="vk-voice-audio"
                            onPlay={e => {
                              document.querySelectorAll('audio').forEach(a => {
                                if (a !== e.currentTarget) a.pause()
                              })
                            }}
                          />
                          <button className="vk-btn-mini" onClick={() => { setRecordedAudioUrl(''); setRecordedAudioData(''); setVoiceMode(''); }}>重录</button>
                        </div>
                      )}
                    </div>

                    {(voiceMode || recordedAudioData) && (
                      <div className="vk-voice-status">
                        ✓ 已选 {voiceMode === 'recording' ? '自录音频' : VOICE_TEMPLATES.find(t => t.id === selectedVoiceTemplate)?.label} · 全片将统一音色
                        <button className="vk-link-btn" onClick={clearVoice}>清除</button>
                      </div>
                    )}
                  </div>
                </div>


                {/* 重新生成 */}
                <button className="vk-regen-btn" onClick={handleGenStoryboard} disabled={genLoading}>
                  {genLoading ? (
                    <>
                      <span className="vk-spin" />
                      <span>生成中…</span>
                    </>
                  ) : (
                    <>
                      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
                        <polyline points="23 4 23 10 17 10" /><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
                      </svg>
                      <span>整篇重新生成</span>
                      <span className="vk-credits-tip">−8 积分</span>
                    </>
                  )}
                </button>

              </div>
            </div>
          )}

          {/* ===== STEP 3 ===== */}
          {step === 3 && (
            <div className="vk-video-grid">

              {/* 工具栏 */}
              <div className="vk-video-result-toolbar">
                <div className="vk-vrt-l">
                  {videoLoading ? (
                    <>
                      <span className="vk-spin" />
                      <div className="vk-info-text">
                        <div>正在生成分段视频…（已完成 {seedanceSegments.length} / {shots.length}）</div>
                        <div className="vk-info-meta">Seedance AI 正在处理每段口播 · 通常需要 1-3 分钟</div>
                      </div>
                    </>
                  ) : mergeLoading ? (
                    <>
                      <span className="vk-spin" />
                      <div className="vk-info-text">
                        <div>所有分段已完成，正在拼接成片…</div>
                        <div className="vk-info-meta">共 {seedanceSegments.length} 段，需 30-60 秒</div>
                      </div>
                    </>
                  ) : mergedVideoUrl ? (
                    <>
                      <div className="vk-check-icon">✓</div>
                      <div className="vk-info-text">
                        <div>视频已生成 · <b>{totalSeconds} 秒</b> · 共 <b>{shots.length} 段</b>拼接</div>
                        <div className="vk-info-meta">9:16 竖屏 · 1080×1920 · {platformLabel}原生格式</div>
                      </div>
                    </>
                  ) : (
                    <div className="vk-info-text">
                      <div>{videoError || '视频生成就绪'}</div>
                    </div>
                  )}
                </div>
                <div className="vk-vrt-r">
                  {!videoLoading && (
                    <button className="vk-btn-tool vk-primary" onClick={goGenerate}>
                      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
                        <polyline points="23 4 23 10 17 10" /><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
                      </svg>
                      <span>全部重新生成</span>
                    </button>
                  )}
                </div>
              </div>

              {/* 主预览 */}
              <div className="vk-video-main">
                {/* 9:16 播放器 */}
                <div className="vk-video-player">
                  <div className="vk-video-frame">
                    {mergedVideoUrl ? (
                      <video
                        src={mergedVideoUrl}
                        controls
                        playsInline
                        style={{ width: '100%', height: '100%', objectFit: 'contain', background: '#000' }}
                      />
                    ) : (
                      <div className="vk-video-content-shot">
                        <div className="vk-person-circle">
                          {characterPreview
                            ? <img src={characterPreview} alt="出镜" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                            : <span>我</span>}
                        </div>
                        <div className="vk-speaking-line" />
                        <div className="vk-subtitle-area">
                          <div className="vk-subtitle-line">
                            {mergeLoading ? '正在拼接成片…' : videoLoading ? '正在生成分段视频…' : '等待生成'}
                          </div>
                          <div className="vk-subtitle-meta">
                            — {shots.length} 段分镜 · {totalSeconds} 秒 —
                          </div>
                        </div>
                      </div>
                    )}
                  </div>

                  <div className="vk-video-ui">
                    <div className="vk-video-ui-top">
                      <div className="vk-platform-bar">
                        <span className="vk-live-tag" />
                        <span>{platformLabel} 预览 · 9:16</span>
                      </div>
                      <div className="vk-right-meta">1080×1920</div>
                    </div>
                    {/* 没拼好成片时显示假进度条，有了就交给原生 <video controls> */}
                    {!mergedVideoUrl && (
                      <div className="vk-video-ui-bottom">
                        <div className="vk-video-progress-track">
                          <div className="vk-video-progress-fill" style={{ width: videoLoading ? '33%' : seedanceSegments.length > 0 ? '100%' : '0%' }} />
                        </div>
                        <div className="vk-video-controls">
                          <div className="vk-play-btn">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                              <path d="M8 5v14l11-7z" />
                            </svg>
                          </div>
                          <div className="vk-video-time">
                            <span>0:00</span>
                            <span className="vk-video-total">/ {formatSeconds(totalSeconds)}</span>
                          </div>
                        </div>
                      </div>
                    )}
                  </div>
                </div>

                {/* 右侧信息 */}
                <div className="vk-video-info-panel">
                  <div className="vk-video-info-title">
                    {selectedTitleText || topic || '口播视频'}
                  </div>
                  <div className="vk-video-stats">
                    <div className="vk-video-stat">
                      <div className="vk-stat-label">— Total duration</div>
                      <div className="vk-stat-value">{totalSeconds}s</div>
                      <div className="vk-stat-meta">含 {shots.length} 段拼接</div>
                    </div>
                    <div className="vk-video-stat">
                      <div className="vk-stat-label">— Resolution</div>
                      <div className="vk-stat-value">1080P</div>
                      <div className="vk-stat-meta">9:16 竖屏</div>
                    </div>
                  </div>
                  <div>
                    <div className="vk-video-info-row"><span className="vk-vik">发布平台</span><span className="vk-viv">{platformLabel}</span></div>
                    <div className="vk-video-info-row"><span className="vk-vik">出镜人物</span><span className="vk-viv">{characterPreview ? '已上传照片' : '数字人'}</span></div>
                    <div className="vk-video-info-row"><span className="vk-vik">背景</span><span className="vk-viv">{backgroundPreview ? '已上传背景' : 'AI 自适应'}</span></div>
                    <div className="vk-video-info-row"><span className="vk-vik">分镜数</span><span className="vk-viv">{shots.length} 个</span></div>
                    <div className="vk-video-info-row"><span className="vk-vik">目标平台</span><span className="vk-viv">{durationOpt?.label}</span></div>
                  </div>
                </div>
              </div>

              {/* 分段列表 */}
              {(seedanceSegments.length > 0 || videoLoading) && (
                <div className="vk-segment-section">
                  <div className="vk-sc-head">
                    <h3>分段视频</h3>
                    <span className="vk-sc-desc">每段可单独预览、重新生成、下载</span>
                  </div>
                  <div className="vk-segment-list">
                    {seedanceSegments.map((seg, i) => (
                      <div key={i} className="vk-segment-card">
                        <div className="vk-segment-thumb">
                          {seg.videoUrl ? (
                            <video
                              src={seg.videoUrl}
                              controls
                              playsInline
                              preload="metadata"
                              style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                              onPlay={e => {
                                document.querySelectorAll('video').forEach(v => {
                                  if (v !== e.currentTarget) v.pause()
                                })
                              }}
                            />
                          ) : (
                            <div className="vk-seg-loading">
                              <span className="vk-spin" />
                            </div>
                          )}
                          <span className="vk-seg-num">{String(i + 1).padStart(2, '0')}</span>
                          {seg.videoUrl && <span className="vk-seg-time">{shots[i]?.duration || 0}s</span>}
                        </div>
                        <div className="vk-segment-meta">
                          <div className="vk-seg-title">分镜 {i + 1}</div>
                          <div className="vk-seg-desc">{shots[i]?.voiceover?.slice(0, 30) || ''}…</div>
                        </div>
                        <div className="vk-segment-actions">
                          <button className="vk-seg-action-btn">
                            <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                              <polyline points="23 4 23 10 17 10" /><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
                            </svg>
                            重生成
                          </button>
                          {seg.videoUrl && (
                            <a className="vk-seg-action-btn" href={seg.videoUrl} download={`分镜${i + 1}.mp4`}>
                              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="7 10 12 15 17 10" />
                              </svg>
                              下载
                            </a>
                          )}
                        </div>
                      </div>
                    ))}

                    {/* Loading placeholders */}
                    {videoLoading && shots.slice(seedanceSegments.length).map((_, i) => (
                      <div key={`ph-${i}`} className="vk-segment-card vk-seg-placeholder">
                        <div className="vk-segment-thumb">
                          <div className="vk-seg-loading"><span className="vk-spin" /></div>
                          <span className="vk-seg-num">{String(seedanceSegments.length + i + 1).padStart(2, '0')}</span>
                        </div>
                        <div className="vk-segment-meta">
                          <div className="vk-seg-title">生成中…</div>
                          <div className="vk-seg-desc">{shots[seedanceSegments.length + i]?.voiceover?.slice(0, 20) || ''}…</div>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Done section */}
              {mergedVideoUrl && (
                <div className="vk-done-section">
                  <div className="vk-done-l">
                    <div className="vk-done-check">✓</div>
                    <div>
                      <h4>这条口播视频已就绪</h4>
                      <div className="vk-done-desc">{titles.length} 个标题 · {shots.length} 个分镜 · {totalSeconds} 秒成片 · 9:16 竖屏</div>
                    </div>
                  </div>
                  <div className="vk-done-r">
                    <a className="vk-btn-done" href={mergedVideoUrl} download={`口播视频_${topic || '成品'}.mp4`}>
                      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="7 10 12 15 17 10" /><line x1="12" y1="15" x2="12" y2="3" />
                      </svg>
                      <span>下载完整视频</span>
                    </a>
                    <button className="vk-btn-done vk-primary">
                      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z" />
                      </svg>
                      <span>保存为草稿</span>
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}

        </div>
      </div>

      {/* ── Error banner ──────────────────────────────────────── */}
      {genError && (
        <div className="vk-error-banner">
          {genError}
          <button onClick={() => setGenError('')}>×</button>
        </div>
      )}

      {/* ── Actions Bar ───────────────────────────────────────── */}
      <div className="vk-actions-bar">
        <div className="vk-ab-l">
          {step > 1 && (
            <button className="vk-btn-back" onClick={handleBack} disabled={genLoading || videoLoading}>
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
                <polyline points="15 18 9 12 15 6" />
              </svg>
              <span>← {sc.backText}</span>
            </button>
          )}
        </div>
        <div className="vk-ab-r">
          <span className="vk-ab-meta">{sc.credits}</span>
          {step < 3 && (
            <button
              className="vk-btn-primary-large"
              onClick={handleNext}
              disabled={genLoading || videoLoading}
            >
              {genLoading ? (
                <><span className="vk-spin" /><span>生成中…</span></>
              ) : (
                <><span>{sc.nextText}</span><span className="vk-arr">→</span></>
              )}
            </button>
          )}
        </div>
      </div>

      {/* ── Ref Modal ─────────────────────────────────────────── */}
      {showRefModal && (
        <div className="vk-modal-bg" onClick={e => e.target === e.currentTarget && setShowRefModal(false)}>
          <div className="vk-modal">
            <button className="vk-modal-close" onClick={() => setShowRefModal(false)}>×</button>
            <h3>添加参考材料</h3>
            <p className="vk-modal-sub">AI 会读完这些材料，结合到这次创作里。<b>仅本次使用，不会保存到素材库。</b></p>

            <div className="vk-tab-row">
              <div className={`vk-tab${refTab === 0 ? ' active' : ''}`} onClick={() => setRefTab(0)}>上传文件</div>
              <div className={`vk-tab${refTab === 1 ? ' active' : ''}`} onClick={() => setRefTab(1)}>粘贴链接</div>
            </div>

            {refTab === 0 ? (
              <div className="vk-upload-zone">
                <div className="vk-uz-ico">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="17 8 12 3 7 8" /><line x1="12" y1="3" x2="12" y2="15" />
                  </svg>
                </div>
                <div className="vk-uz-text">点击上传，或把文件拖到这里</div>
                <div className="vk-uz-hint">支持 PDF / DOC / DOCX / JPG / PNG · 单文件最大 20MB</div>
              </div>
            ) : (
              <input
                type="text"
                className="vk-text-field"
                value={refLink}
                onChange={e => setRefLink(e.target.value)}
                placeholder="粘贴公众号 / 知乎 / 网页文章链接…"
              />
            )}

            <div className="vk-modal-actions">
              <button className="vk-btn-back" onClick={() => setShowRefModal(false)}>取消</button>
              <button className="vk-btn-primary-large vk-sm" onClick={() => {
                if (refTab === 1 && refLink.trim()) addRefLink()
                else setShowRefModal(false)
              }}>
                <span>添加</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── 录音弹窗 ────────────────────────────────────────── */}
      {showRecordModal && (
        <div className="vk-modal-bg" onClick={e => {
          if (e.target === e.currentTarget && !isRecording) setShowRecordModal(false)
        }}>
          <div className="vk-modal vk-record-modal">
            {!isRecording && (
              <button className="vk-modal-close" onClick={() => setShowRecordModal(false)}>×</button>
            )}
            <h3>录制你的声音</h3>
            <p className="vk-modal-sub">
              请用<b>自然的语速</b>朗读下面这段文本（<b>4-10 秒</b>），AI 会用你的声纹生成全片口播。
              <br />确保<b>环境安静</b>、距离麦克风约 20cm。
            </p>

            <div className="vk-record-script">
              {RECORD_SCRIPT}
            </div>

            <div className="vk-record-control">
              {!isRecording ? (
                <button className="vk-btn-record-big" onClick={startRecording}>
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="12" r="6"/></svg>
                  <span>开始录制</span>
                </button>
              ) : (
                <button className="vk-btn-record-big recording" onClick={stopRecording}>
                  <span className="vk-rec-dot" />
                  <span>停止录制 · {recordSeconds}s / 15s</span>
                </button>
              )}
            </div>
          </div>
        </div>
      )}

      <ConfirmModal {...confirmProps} />
    </div>
  )
}
