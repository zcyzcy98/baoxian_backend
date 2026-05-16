import { useState, useEffect } from 'react'
import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { createXiangshengStage1, createXiangshengStage2, createXiangshengStage3, fetchXiangshengStyles } from '../api'
import './XiangshengCreatePage.css'

const STEPS = [
  { id: 1, label: '生成台词' },
  { id: 2, label: '台词预览' },
  { id: 3, label: '分镜与提示词' },
]

export default function XiangshengCreatePage() {
  const [step, setStep] = useState(1)
  const [topic, setTopic] = useState('')
  const [styleIndex, setStyleIndex] = useState(1)
  const [styles, setStyles] = useState([])
  const [loading, setLoading] = useState(false)
  const [styleName, setStyleName] = useState('')
  const [dialogue, setDialogue] = useState('')
  const [editedDialogue, setEditedDialogue] = useState('')
  const [storyboard, setStoryboard] = useState('')
  const [groupPrompts, setGroupPrompts] = useState('')
  const [activeTab, setActiveTab] = useState('storyboard')
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    fetchXiangshengStyles().then(setStyles).catch(() => {})
  }, [])

  // 阶段一：仅生成台词
  const handleGenDialogue = async () => {
    if (!topic.trim()) return
    setLoading(true)
    try {
      const res = await createXiangshengStage1(topic.trim(), styleIndex)
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
      const s2 = await createXiangshengStage2(editedDialogue)
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

      {/* Step 1: 输入选题 + 选择风格 */}
      {step === 1 && (
        <div className="xs-section-card">
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

          <div className="xs-section-label" style={{ marginTop: 20 }}>选择风格</div>
          <div className="xs-style-grid">
            {styles.map(s => (
              <div
                key={s.index}
                className={`xs-style-card ${styleIndex === s.index ? 'active' : ''}`}
                onClick={() => setStyleIndex(s.index)}
              >
                <div className="xs-style-name">{s.name}</div>
                <div className="xs-style-desc">{s.desc}</div>
              </div>
            ))}
          </div>

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
          <textarea
            className="xs-textarea"
            value={editedDialogue}
            onChange={e => setEditedDialogue(e.target.value)}
          />

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
            <button className="xs-btn primary" onClick={() => { setStep(1); setDialogue(''); setStoryboard(''); setGroupPrompts(''); setTopic(''); setStyleName(''); }}>新建创作</button>
          </div>
        </div>
      )}
    </div>
  )
}
