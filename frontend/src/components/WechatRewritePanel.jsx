import { useEffect, useRef, useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { extractWechatArticle, rewriteWechat, fetchRewriteModes } from '../api'
import { loadWorkflowState, saveWorkflowState, clearWorkflowState } from '../storage'
import { stripMarkdown, copyToClipboard } from '../utils/markdown'
import './XhsToWxPanel.css'

const DEFAULT_STATE = {
  url: '',
  cookie: '',
  extractedArticle: null,
  rewriteMode: 'mp-rewrite',
  requirements: '',
  rewriteResult: null,
}

export default function WechatRewritePanel({ agent }) {
  const persisted = loadWorkflowState(agent.id) || {}
  const [url, setUrl] = useState(persisted.url ?? DEFAULT_STATE.url)
  const [cookie, setCookie] = useState(DEFAULT_STATE.cookie)
  const [extractedArticle, setExtractedArticle] = useState(
    persisted.extractedArticle ?? DEFAULT_STATE.extractedArticle,
  )
  const [extracting, setExtracting] = useState(false)
  const [extractError, setExtractError] = useState(null)

  const [rewriteMode, setRewriteMode] = useState(
    persisted.rewriteMode ?? DEFAULT_STATE.rewriteMode,
  )
  const [requirements, setRequirements] = useState(
    persisted.requirements ?? DEFAULT_STATE.requirements,
  )
  const [model, setModel] = useState('chat')
  const [rewriting, setRewriting] = useState(false)
  const [rewriteResult, setRewriteResult] = useState(
    persisted.rewriteResult ?? DEFAULT_STATE.rewriteResult,
  )
  const [rewriteError, setRewriteError] = useState(null)

  const [modes, setModes] = useState([])
  const [modesLoading, setModesLoading] = useState(false)

  // load modes once
  useEffect(() => {
    let cancelled = false
    setModesLoading(true)
    fetchRewriteModes()
      .then((data) => {
        if (!cancelled) setModes(data || [])
      })
      .catch(() => {
        if (!cancelled)
          setModes([
            { id: 'mp-rewrite', label: '公众号深度改写' },
            { id: 'mp-to-xhs', label: '公众号转小红书' },
            { id: 'mp-to-short', label: '公众号转短文案/朋友圈' },
            { id: 'mp-summary', label: '公众号摘要/大纲提取' },
            { id: 'mp-optimize', label: '公众号标题+开头优化' },
          ])
      })
      .finally(() => !cancelled && setModesLoading(false))
    return () => {
      cancelled = true
    }
  }, [])

  // persist
  useEffect(() => {
    saveWorkflowState(agent.id, {
      url,
      extractedArticle,
      rewriteMode,
      requirements,
      rewriteResult,
    })
  }, [agent.id, url, extractedArticle, rewriteMode, requirements, rewriteResult])

  const handleExtract = async () => {
    if (!url.trim() || extracting) return
    setExtractError(null)
    setExtracting(true)
    try {
      const article = await extractWechatArticle(url.trim(), cookie.trim())
      setExtractedArticle(article)
      setRewriteResult(null)
    } catch (err) {
      setExtractError(err.message)
    } finally {
      setExtracting(false)
    }
  }

  const handleRewrite = async () => {
    if (!extractedArticle || rewriting) return
    setRewriteError(null)
    setRewriting(true)
    try {
      const result = await rewriteWechat({
        url: url.trim(),
        cookie: cookie.trim(),
        title: extractedArticle.title,
        content: extractedArticle.content,
        mode: rewriteMode,
        requirements: requirements.trim(),
        model,
      })
      setRewriteResult(result)
    } catch (err) {
      setRewriteError(err.message)
    } finally {
      setRewriting(false)
    }
  }

  const handleResetAll = () => {
    if (extracting || rewriting) return
    if (!confirm('确认清空当前工作流的所有内容吗？')) return
    clearWorkflowState(agent.id)
    setUrl('')
    setCookie('')
    setExtractedArticle(null)
    setExtractError(null)
    setRewriteMode('mp-rewrite')
    setRequirements('')
    setRewriteResult(null)
    setRewriteError(null)
  }

  return (
    <main className="wf-panel">
      <header className="wf-header">
        <h1 className="wf-title">{agent.name}</h1>
        <button
          type="button"
          className="wf-action"
          onClick={handleResetAll}
          disabled={extracting || rewriting}
          title="清空当前工作流"
        >
          清空工作流
        </button>
      </header>

      <div className="wf-columns">
        <StepExtract
          url={url}
          cookie={cookie}
          onUrlChange={setUrl}
          onCookieChange={setCookie}
          extracting={extracting}
          extractError={extractError}
          extractedArticle={extractedArticle}
          onExtract={handleExtract}
        />

        <StepRewrite
          extractedArticle={extractedArticle}
          modes={modes}
          modesLoading={modesLoading}
          rewriteMode={rewriteMode}
          onModeChange={setRewriteMode}
          requirements={requirements}
          onRequirementsChange={setRequirements}
          model={model}
          onModelChange={setModel}
          rewriting={rewriting}
          rewriteError={rewriteError}
          rewriteResult={rewriteResult}
          onRewrite={handleRewrite}
        />
      </div>
    </main>
  )
}

function StepExtract({
  url,
  cookie,
  onUrlChange,
  onCookieChange,
  extracting,
  extractError,
  extractedArticle,
  onExtract,
}) {
  return (
    <section className="wf-col">
      <h2 className="wf-step-title">1、提取公众号文章</h2>
      <div className="wf-field">
        <label className="wf-label">
          公众号文章链接<span className="wf-req">*</span>
        </label>
        <textarea
          className="wf-input"
          rows={3}
          placeholder="粘贴 mp.weixin.qq.com/s/... 文章链接"
          value={url}
          onChange={(e) => onUrlChange(e.target.value)}
          disabled={extracting}
        />
      </div>

      <div className="wf-help">
        ✅ 无需 Cookie，自动提取标题、正文、封面图、摘要和公众号信息。
      </div>

      <button
        type="button"
        className="wf-btn wf-btn-primary"
        onClick={onExtract}
        disabled={!url.trim() || extracting}
      >
        {extracting ? '提取中...' : '提取文章'}
      </button>

      {extractError && <div className="wf-error">{extractError}</div>}

      {extractedArticle && (
        <div className="wf-preview">
          <div className="wf-preview-head">
            <span className="wf-preview-tag">已提取</span>
            <span className="wf-preview-meta">
              {extractedArticle.type === 'image' ? '图片型文章' : '图文文章'} · {extractedArticle.accountName}
            </span>
          </div>
          <div className="wf-preview-title">{extractedArticle.title || '(无标题)'}</div>
          {extractedArticle.digest && (
            <div className="wf-preview-body">摘要：{extractedArticle.digest}</div>
          )}
          {extractedArticle.content && (
            <div className="wf-preview-body">正文预览：{extractedArticle.content.substring(0, 200)}...</div>
          )}
          {extractedArticle.cover && (
            <div className="wf-preview-images">
              <img src={extractedArticle.cover} alt="封面" loading="lazy" referrerPolicy="no-referrer" />
            </div>
          )}
        </div>
      )}
    </section>
  )
}

function StepRewrite({
  extractedArticle,
  modes,
  modesLoading,
  rewriteMode,
  onModeChange,
  requirements,
  onRequirementsChange,
  model,
  onModelChange,
  rewriting,
  rewriteError,
  rewriteResult,
  onRewrite,
}) {
  const ready = !!extractedArticle
  return (
    <section className={'wf-col' + (ready ? '' : ' is-locked')}>
      <h2 className="wf-step-title">2、公众号仿写</h2>
      {!ready && <div className="wf-locked-tip">请先在左侧提取公众号文章</div>}

      <div className="wf-field">
        <label className="wf-label">
          仿写模式<span className="wf-req">*</span>
        </label>
        <select
          className="wf-input"
          value={rewriteMode}
          onChange={(e) => onModeChange(e.target.value)}
          disabled={!ready || rewriting || modesLoading}
        >
          {modes.map((m) => (
            <option key={m.id} value={m.id}>{m.label}</option>
          ))}
        </select>
      </div>

      <div className="wf-field">
        <label className="wf-label">
          附加要求（可选）
        </label>
        <textarea
          className="wf-input"
          rows={3}
          placeholder="例如：更偏干货风 / 去掉专业术语 / 增加一些案例"
          value={requirements}
          onChange={(e) => onRequirementsChange(e.target.value)}
          disabled={!ready || rewriting}
        />
      </div>

      <div className="wf-field wf-field-row">
        <label className="wf-label">使用模型</label>
        <div className="wf-model-toggle">
          <button
            type="button"
            className={'wf-pill' + (model === 'chat' ? ' is-active' : '')}
            onClick={() => onModelChange('chat')}
            disabled={!ready || rewriting}
          >
            DeepSeek 原版
          </button>
          <button
            type="button"
            className={'wf-pill' + (model === 'reasoner' ? ' is-active' : '')}
            onClick={() => onModelChange('reasoner')}
            disabled={!ready || rewriting}
          >
            DeepSeek (投喂)
          </button>
        </div>
      </div>

      <button
        type="button"
        className="wf-btn wf-btn-accent"
        onClick={onRewrite}
        disabled={!ready || rewriting}
      >
        {rewriting ? '改写中...' : '一键仿写'}
      </button>

      {rewriteError && <div className="wf-error">{rewriteError}</div>}

      {rewriteResult && (
        <div className="wf-preview">
          <div className="wf-preview-head">
            <span className="wf-preview-tag">仿写结果</span>
            {rewriteResult.rewritten?.model && (
              <span className="wf-preview-meta">via {rewriteResult.rewritten.model}</span>
            )}
            <CopyButton text={rewriteResult.rewritten?.content || rewriteResult.rewritten?.title} />
          </div>
          {rewriteResult.rewritten?.title && (
            <div className="wf-preview-title">{rewriteResult.rewritten.title}</div>
          )}
          {rewriteResult.rewritten?.content && (
            <div className="wf-preview-md">
              <ReactMarkdown remarkPlugins={[remarkGfm]}>{rewriteResult.rewritten.content}</ReactMarkdown>
            </div>
          )}
          {!rewriteResult.rewritten?.content && rewriteResult.rewritten?.title && (
            <div className="wf-preview-body">{rewriteResult.rewritten.title}</div>
          )}
        </div>
      )}
    </section>
  )
}

function CopyButton({ text }) {
  const [copied, setCopied] = useState(false)
  const timeoutRef = useRef(null)
  const handleClick = async () => {
    try {
      await copyToClipboard(stripMarkdown(text))
      setCopied(true)
      if (timeoutRef.current) clearTimeout(timeoutRef.current)
      timeoutRef.current = setTimeout(() => setCopied(false), 1500)
    } catch {
      // ignore
    }
  }
  return (
    <button
      type="button"
      className={'wf-copy-btn' + (copied ? ' is-copied' : '')}
      onClick={handleClick}
      title="复制为纯文本"
    >
      {copied ? '已复制' : '复制纯文本'}
    </button>
  )
}
