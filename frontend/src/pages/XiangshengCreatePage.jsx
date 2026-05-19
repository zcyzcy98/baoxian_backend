import { useState, useEffect } from 'react'
import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { createXiangshengStage1, createXiangshengStage2, createXiangshengStage3, fetchXiangshengDimensions, recommendXiangsheng } from '../api'
import './XiangshengCreatePage.css'

const STEPS = [
  { id: 1, label: '生成台词' },
  { id: 2, label: '台词预览' },
  { id: 3, label: '分镜与提示词' },
]

// 维度 key 到中文标签的映射
const DIM_LABELS = {
  hookType: '钩子类型',
  structure: '剧本结构',
  emotionArc: '情绪弧线',
  audience: '目标受众',
  topicDirection: '话题方向',
  toneStyle: '语气风格',
}

export default function XiangshengCreatePage() {
  const [step, setStep] = useState(1)
  const [topic, setTopic] = useState('')
  const [loading, setLoading] = useState(false)
  const [duration, setDuration] = useState(null)
  const [customDuration, setCustomDuration] = useState('')
  const [durationMode, setDurationMode] = useState(null) // preset | custom
  const [recommendLoading, setRecommendLoading] = useState(false)
  const [styleName, setStyleName] = useState('')
  const [dialogue, setDialogue] = useState('')
  const [editedDialogue, setEditedDialogue] = useState('')
  const [storyboard, setStoryboard] = useState('')
  const [groupPrompts, setGroupPrompts] = useState('')
  const [activeTab, setActiveTab] = useState('storyboard')
  const [targetDuration, setTargetDuration] = useState(null)
  const [copied, setCopied] = useState(false)

  // 维度数据
  const [dimensions, setDimensions] = useState([])
  const [selectedDims, setSelectedDims] = useState({
    hookType: '',
    structure: '',
    emotionArc: '',
    audience: '',
    topicDirection: '',
    toneStyle: '',
  })
  const [recommendReason, setRecommendReason] = useState('')

  useEffect(() => {
    fetchXiangshengDimensions()
      .then(dims => {
        setDimensions(dims)
      })
      .catch(() => {})
  }, [])

  const handleDimSelect = (key, value) => {
    setSelectedDims(prev => ({ ...prev, [key]: value }))
    setRecommendReason('')
  }

  // AI智能推荐
  const handleRecommend = async () => {
    if (!topic.trim()) return
    setRecommendLoading(true)
    setRecommendReason('')
    try {
      const result = await recommendXiangsheng(topic.trim())
      // 用推荐结果填充维度
      const newDims = { ...selectedDims }
      if (result.hookType) newDims.hookType = result.hookType
      if (result.structure) newDims.structure = result.structure
      if (result.emotionArc) newDims.emotionArc = result.emotionArc
      if (result.audience) newDims.audience = result.audience
      if (result.topicDirection) newDims.topicDirection = result.topicDirection
      if (result.toneStyle) newDims.toneStyle = result.toneStyle
      setSelectedDims(newDims)
      setRecommendReason(result.reason || '')
    } catch (e) {
      alert('AI推荐失败：' + (e.message || '请稍后重试'))
    } finally {
      setRecommendLoading(false)
    }
  }

  // 阶段一：仅生成台词
  const handleGenDialogue = async () => {
    if (!topic.trim()) return
    // 校验必填维度
    const required = ['hookType', 'structure', 'emotionArc', 'audience', 'topicDirection']
    for (const key of required) {
      if (!selectedDims[key]) {
        alert(`请选择${DIM_LABELS[key]}`)
        return
      }
    }
    setLoading(true)
    try {
      const dims = { ...selectedDims }
      // toneStyle 为空时不传
      if (!dims.toneStyle || dims.toneStyle === '通用') {
        delete dims.toneStyle
      }
      const td = durationMode === 'custom' ? parseInt(customDuration) || null : duration
      setTargetDuration(td)
      if (td) dims.duration = td
      const res = await createXiangshengStage1(topic.trim(), dims)
      setStyleName(res.styleName || '')
      setDialogue(res.dialogue || '')
      setEditedDialogue(res.dialogue || '')
      setStep(2)
    } catch (e) {
      alert('生成失败：' + (e.message || '请稍后重试'))
    } finally {
      setLoading(false)
    }
  }

  // 阶段二+三：生成分镜 + 分组提示词
  const handleGenStoryboard = async () => {
    setLoading(true)
    try {
      const s2 = await createXiangshengStage2(editedDialogue, targetDuration)
      setStoryboard(s2.storyboard || '')
      const s3 = await createXiangshengStage3(s2.storyboard)
      setGroupPrompts(s3.groupPrompts || '')
      setStep(3)
    } catch (e) {
      alert('生成失败：' + (e.message || '请稍后重试'))
    } finally {
      setLoading(false)
    }
  }

  const handleCopy = (text) => {
    navigator.clipboard.writeText(text)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const handleDownload = (filename, content) => {
    const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className="xs-page">
      {/* Page Head */}
      <div className="xs-page-head">
        <div className="xs-title-row">
          <h2>
            相声剧本创作
            {styleName && <span className="xs-style-badge">{styleName}</span>}
          </h2>
        </div>
        <div className="xs-steps">
          {STEPS.map((s) => (
            <div key={s.id} className={`xs-step ${step === s.id ? 'active' : ''} ${step > s.id ? 'done' : ''}`}>
              <div className="dot">{step > s.id ? '✓' : s.id}</div>
              <div className="slabel">{s.label}</div>
            </div>
          ))}
        </div>
      </div>

      {/* Step 1: 输入选题 + 选择维度 */}
      {step === 1 && (
        <div className="xs-section-card">
          {/* 选题输入 */}
          <div className="xs-section-label">输入选题</div>
          <div className="xs-field">
            <input
              type="text"
              value={topic}
              onChange={e => setTopic(e.target.value)}
              placeholder="例如：为什么车险全险四千不眨眼，医疗险三百嫌贵？"
              onKeyDown={e => e.key === 'Enter' && handleGenDialogue()}
            />
          </div>

          {/* AI推荐按钮 */}
          <div className="xs-recommend-row">
            <button
              className="xs-btn recommend"
              onClick={handleRecommend}
              disabled={!topic.trim() || recommendLoading}
            >
              {recommendLoading ? '分析中...' : 'AI 智能推荐'}
            </button>
            {recommendReason && (
              <div className="xs-recommend-reason">{recommendReason}</div>
            )}
          </div>

          {/* 视频时长 */}
          <div className="xs-section-label" style={{ marginTop: 20 }}>视频时长</div>
          <div className="xs-duration-row">
            <div className="xs-duration-presets">
              {[60, 90, 120].map(sec => (
                <button
                  key={sec}
                  className={`xs-duration-btn ${durationMode === 'preset' && duration === sec ? 'active' : ''}`}
                  onClick={() => { setDurationMode('preset'); setDuration(sec); }}
                >
                  {sec}秒
                </button>
              ))}
              <button
                className={`xs-duration-btn ${durationMode === 'custom' ? 'active' : ''}`}
                onClick={() => setDurationMode('custom')}
              >
                自定义
              </button>
            </div>
            {durationMode === 'custom' && (
              <div className="xs-duration-custom">
                <input
                  type="number"
                  min={30}
                  max={300}
                  step={10}
                  value={customDuration}
                  onChange={e => setCustomDuration(e.target.value)}
                />
                <span>秒</span>
              </div>
            )}
          </div>

          {/* 维度选择器 */}
          {dimensions.map(dim => (
            <div key={dim.key}>
              <div className="xs-section-label" style={{ marginTop: 20 }}>
                {dim.label}
              </div>
              <div className="xs-style-grid">
                {dim.options.map(opt => (
                  <div
                    key={opt.value}
                    className={`xs-style-card ${selectedDims[dim.key] === opt.value ? 'active' : ''}`}
                    onClick={() => handleDimSelect(dim.key, opt.value)}
                  >
                    <div className="xs-style-name">{opt.value}</div>
                    <div className="xs-style-desc">{opt.desc}</div>
                  </div>
                ))}
              </div>
            </div>
          ))}

          <div className="xs-nav-btns">
            <button className="xs-btn primary" onClick={handleGenDialogue} disabled={!topic.trim() || loading}>
              {loading ? '生成中...' : '生成台词'}
            </button>
          </div>
        </div>
      )}

      {/* Step 2: 台词预览 + 生成分镜入口 */}
      {step === 2 && (
        <div className="xs-section-card">
          <div className="xs-section-label">台词预览</div>
          <div className="xs-pre">
            <Markdown remarkPlugins={[remarkGfm]}>
              {editedDialogue.replace(/\n/g, '\n\n')}
            </Markdown>
          </div>

          <div className="xs-nav-btns">
            <button className="xs-btn" onClick={() => setStep(1)}>返回修改</button>
            <button className="xs-btn sm" onClick={() => handleCopy(editedDialogue)}>
              {copied ? '已复制' : '复制台词'}
            </button>
            <button className="xs-btn sm" onClick={() => handleDownload('台词.md', editedDialogue)}>
              下载台词
            </button>
            <button className="xs-btn primary" onClick={handleGenStoryboard} disabled={loading}>
              {loading ? '生成中...' : '创作剧本与分镜'}
            </button>
          </div>
        </div>
      )}

      {/* Step 3: 分镜与分组提示词 */}
      {step === 3 && (
        <div className="xs-section-card">
          <div className="xs-section-label">分镜与分组提示词</div>

          <div className="xs-tabs">
            <button className={`xs-tab ${activeTab === 'storyboard' ? 'active' : ''}`} onClick={() => setActiveTab('storyboard')}>
              分镜剧本
            </button>
            <button className={`xs-tab ${activeTab === 'group' ? 'active' : ''}`} onClick={() => setActiveTab('group')}>
              分组提示词
            </button>
          </div>

          <div className="xs-pre">
            <Markdown remarkPlugins={[remarkGfm]}>
              {activeTab === 'storyboard' ? storyboard : groupPrompts}
            </Markdown>
          </div>

          <div className="xs-nav-btns">
            <button className="xs-btn" onClick={() => setStep(2)}>返回台词</button>
            <button className="xs-btn sm" onClick={() => handleCopy(activeTab === 'storyboard' ? storyboard : groupPrompts)}>
              {copied ? '已复制' : '复制'}
            </button>
            <button className="xs-btn sm" onClick={() => handleDownload(activeTab === 'storyboard' ? '分镜剧本.md' : '分段提示词.md', activeTab === 'storyboard' ? storyboard : groupPrompts)}>
              下载
            </button>
            <button className="xs-btn primary" onClick={() => { setStep(1); setDialogue(''); setStoryboard(''); setGroupPrompts(''); setTopic(''); setStyleName(''); setRecommendReason(''); }}>新建创作</button>
          </div>
        </div>
      )}
    </div>
  )
}
