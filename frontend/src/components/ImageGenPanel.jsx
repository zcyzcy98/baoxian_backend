import { useEffect, useState } from 'react'
import { fetchImageTemplates, generateImage, generateSeedreamImage } from '../api'
import { loadWorkflowState, saveWorkflowState, clearWorkflowState } from '../storage'
import './ImageGenPanel.css'

const NO_TEMPLATE = '__none__'

export default function ImageGenPanel({ agent }) {
  const persisted = loadWorkflowState(agent.id) || {}
  const [topic, setTopic] = useState(persisted.topic ?? '')
  const [style, setStyle] = useState(persisted.style ?? '')
  const [templateId, setTemplateId] = useState(persisted.templateId ?? null)
  const [model, setModel] = useState('chat')
  const [templates, setTemplates] = useState([])
  const [templatesLoading, setTemplatesLoading] = useState(false)
  const [templatesError, setTemplatesError] = useState(null)

  const [generatingProvider, setGeneratingProvider] = useState(null)
  const [error, setError] = useState(null)
  const [result, setResult] = useState(persisted.result ?? null)
  const generating = !!generatingProvider

  // load templates from backend on mount
  useEffect(() => {
    let cancelled = false
    setTemplatesLoading(true)
    fetchImageTemplates()
      .then((data) => {
        if (cancelled) return
        setTemplates(Array.isArray(data) ? data : [])
        if (!persisted.templateId && data && data.length > 0 && persisted.templateId !== NO_TEMPLATE) {
          setTemplateId(data[0].id)
        }
      })
      .catch((err) => {
        if (!cancelled) setTemplatesError(err.message)
      })
      .finally(() => !cancelled && setTemplatesLoading(false))
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [agent.id])

  // persist state on change
  useEffect(() => {
    saveWorkflowState(agent.id, {
      topic,
      style,
      templateId,
      result,
    })
  }, [agent.id, topic, style, templateId, result])

  const handleGenerate = async (provider = 'hiapi') => {
    if (!topic.trim() || generating) return
    setError(null)
    setGeneratingProvider(provider)
    try {
      const generator = provider === 'seedream' ? generateSeedreamImage : generateImage
      const res = await generator({
        topic: topic.trim(),
        style: style.trim(),
        templateId: templateId === NO_TEMPLATE ? null : templateId,
        model,
      })
      setResult(res)
    } catch (err) {
      setError(err.message)
    } finally {
      setGeneratingProvider(null)
    }
  }

  const handleClear = () => {
    if (generating) return
    if (!confirm('清空当前页面所有输入和生成结果?')) return
    clearWorkflowState(agent.id)
    setTopic('')
    setStyle('')
    setResult(null)
    setError(null)
    setTemplateId(templates[0]?.id ?? null)
  }

  return (
    <main className="ig-panel">
      <header className="ig-header">
        <h1 className="ig-title">{agent.name}</h1>
        <button
          type="button"
          className="ig-action"
          onClick={handleClear}
          disabled={generating}
          title="清空当前面板"
        >
          清空
        </button>
      </header>

      <div className="ig-columns">
        <TemplateColumn
          templates={templates}
          loading={templatesLoading}
          error={templatesError}
          activeId={templateId}
          onSelect={setTemplateId}
          disabled={generating}
        />

        <InputColumn
          topic={topic}
          onTopicChange={setTopic}
          style={style}
          onStyleChange={setStyle}
          model={model}
          onModelChange={setModel}
          generating={generating}
          generatingProvider={generatingProvider}
          onGenerate={handleGenerate}
          error={error}
          activeTemplateId={templateId}
          templates={templates}
        />

        <ResultColumn result={result} generating={generating} />
      </div>
    </main>
  )
}

function TemplateColumn({ templates, loading, error, activeId, onSelect, disabled }) {
  return (
    <section className="ig-col ig-col-template">
      <h2 className="ig-step-title">模板</h2>
      {loading && <div className="ig-help">加载模板中...</div>}
      {error && <div className="ig-error">加载模板失败: {error}</div>}

      <div className="ig-template-grid">
        {templates.map((t) => (
          <button
            type="button"
            key={t.id}
            className={'ig-template' + (activeId === t.id ? ' is-active' : '')}
            onClick={() => !disabled && onSelect(t.id)}
            disabled={disabled}
            title={t.name}
            aria-label={t.name}
          >
            <img src={t.thumbnail} alt={t.name} loading="lazy" />
            {activeId === t.id && <span className="ig-template-tick" aria-hidden>✓</span>}
          </button>
        ))}

        <button
          type="button"
          className={'ig-template ig-template-none' + (activeId === NO_TEMPLATE ? ' is-active' : '')}
          onClick={() => !disabled && onSelect(NO_TEMPLATE)}
          disabled={disabled}
          title="不使用模板, 让 AI 自动决定风格"
        >
          <span className="ig-template-none-text">
            不使用模板
            <small>AI 自动决定风格</small>
          </span>
          {activeId === NO_TEMPLATE && <span className="ig-template-tick" aria-hidden>✓</span>}
        </button>
      </div>
    </section>
  )
}

function InputColumn({
  topic,
  onTopicChange,
  style,
  onStyleChange,
  model,
  onModelChange,
  generating,
  generatingProvider,
  onGenerate,
  error,
  activeTemplateId,
  templates,
}) {
  const usingTemplate = !!templates.find((t) => t.id === activeTemplateId)
  return (
    <section className="ig-col ig-col-input">
      <h2 className="ig-step-title">内容</h2>

      <div className="ig-field">
        <label className="ig-label">
          画面主题<span className="ig-req">*</span>
        </label>
        <textarea
          className="ig-input ig-textarea"
          rows={6}
          placeholder="描述你想要呈现的内容,越具体越好&#10;例如: 给爸妈买保险的 3 个常见坑 — 第一个坑是 60 岁以上买重疾险,第二个是只看价格不看健康告知,第三个是给爸妈买寿险"
          value={topic}
          onChange={(e) => onTopicChange(e.target.value)}
          disabled={generating}
        />
      </div>

      <div className="ig-field">
        <label className="ig-label">附加风格说明 (可选)</label>
        <input
          type="text"
          className="ig-input"
          placeholder="例如: 强调避坑感 / 用红黄警示色"
          value={style}
          onChange={(e) => onStyleChange(e.target.value)}
          disabled={generating}
        />
      </div>

      <div className="ig-field">
        <label className="ig-label">模型</label>
        <select
          className="ig-input"
          value={model}
          onChange={(e) => onModelChange(e.target.value)}
          disabled={generating}
        >
          <option value="chat">AI 原版 (chat)</option>
          <option value="reasoner">AI 增强 (reasoner)</option>
        </select>
      </div>

      <div className="ig-help">
        当前选择: {usingTemplate ? '已使用模板' : '不使用模板'}
        <br />
        生成约需 30 秒(AI 拟稿 + 图片 API 出图)。
      </div>

      <div className="ig-generate-actions">
        <button
          type="button"
          className="ig-btn ig-btn-primary"
          onClick={() => onGenerate('hiapi')}
          disabled={!topic.trim() || generating}
        >
          {generatingProvider === 'hiapi' ? 'HiAPI 生成中...' : '用 HiAPI 生成'}
        </button>
        <button
          type="button"
          className="ig-btn ig-btn-seedream"
          onClick={() => onGenerate('seedream')}
          disabled={!topic.trim() || generating}
        >
          {generatingProvider === 'seedream' ? 'Seedream 生成中...' : '用 Seedream 生成'}
        </button>
      </div>

      {error && <div className="ig-error">{error}</div>}
    </section>
  )
}

function ResultColumn({ result, generating }) {
  const [downloadStatus, setDownloadStatus] = useState('idle')

  const handleDownload = async () => {
    if (!result?.imageUrl || downloadStatus === 'loading') return
    const filename = `xhs-image-${new Date().toISOString().replace(/[:.]/g, '-')}.png`
    try {
      setDownloadStatus('loading')
      const res = await fetch(result.imageUrl)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const blob = await res.blob()
      const objectUrl = URL.createObjectURL(blob)
      triggerDownload(objectUrl, filename)
      URL.revokeObjectURL(objectUrl)
      setDownloadStatus('done')
      setTimeout(() => setDownloadStatus('idle'), 1500)
    } catch {
      triggerDownload(result.imageUrl, filename, true)
      setDownloadStatus('idle')
    }
  }

  return (
    <section className="ig-col ig-col-result">
      <h2 className="ig-step-title">生成结果</h2>

      {!result && !generating && (
        <div className="ig-result-empty">
          填好左、中两栏后点"生成图片"<br />
          结果会显示在这里
        </div>
      )}

      {generating && (
        <div className="ig-result-loading">
          <div className="ig-spinner" />
          <div>正在生成中, 请稍候...</div>
        </div>
      )}

      {result && !generating && (
        <>
          {result.imageUrl && (
            <div className="ig-result-image">
              <img src={result.imageUrl} alt="生成结果" />
              <div className="ig-result-status">图片已生成</div>
              <div className="ig-result-actions">
                <button
                  type="button"
                  className="ig-btn ig-btn-secondary"
                  onClick={handleDownload}
                  disabled={downloadStatus === 'loading'}
                >
                  {downloadStatus === 'loading'
                    ? '下载中'
                    : downloadStatus === 'done'
                      ? '已下载'
                      : '下载图片'}
                </button>
                <a
                  href={result.imageUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="ig-btn ig-btn-secondary"
                >
                  原图
                </a>
              </div>
            </div>
          )}

          {result.model && (
            <div className="ig-result-meta">via {result.model}</div>
          )}
        </>
      )}
    </section>
  )
}

function triggerDownload(url, filename, newTab = false) {
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  if (newTab) {
    a.target = '_blank'
    a.rel = 'noopener noreferrer'
  }
  document.body.appendChild(a)
  a.click()
  a.remove()
}
