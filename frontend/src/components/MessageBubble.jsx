import { useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { stripMarkdown, copyToClipboard } from '../utils/markdown'
import './MessageBubble.css'

export default function MessageBubble({ message }) {
  const { role, type, content, imageUrl, coverUrl, videoUrl, resultUrls, projectUrl, sessionId, model, currentStep, totalSteps, progressPercent, stepName, steps, cleanedContent } = message
  const isUser = role === 'user'
  const isError = type === 'error'
  const isLoading = type === 'loading'
  const isImage = type === 'image'
  const isVideo = type === 'video'
  const isStatus = type === 'status'
  const mediaUrls = Array.isArray(resultUrls) && resultUrls.length > 0
    ? resultUrls
    : [videoUrl || imageUrl].filter(Boolean)
  const visibleContent = isImage ? '图片已生成' : isVideo && mediaUrls.length > 0 ? 'LibTV 已生成结果' : content
  const useMarkdown = !isUser && !isError && !isLoading && !isImage && !isVideo && !isStatus && content
  const isComplianceResult = !isUser && !isError && !isLoading && cleanedContent !== undefined

  const hasProgress = isStatus && (currentStep !== undefined || progressPercent !== undefined)

  return (
    <div className={'bubble-row ' + (isUser ? 'is-user' : 'is-assistant')}>
      <div className={'bubble bubble-' + role + (isError ? ' is-error' : '') + (isStatus ? ' is-status' : '') + (hasProgress ? ' has-progress' : '')}>
        {!isUser && !isLoading && !isImage && !isVideo && !isStatus && content && !isComplianceResult && <CopyButton text={content} />}
        {!isUser && !isLoading && isImage && imageUrl && <DownloadButton imageUrl={imageUrl} />}
        {!isUser && !isLoading && isVideo && mediaUrls.length === 1 && (
          <DownloadButton imageUrl={mediaUrls[0]} kind={isVideoUrl(mediaUrls[0]) ? 'video' : 'image'} />
        )}
        {isLoading && <span className="dot-anim">●●●</span>}
        {isStatus && (
          <div className="bubble-status">
            <span className="status-spinner" aria-hidden="true" />
            <div className="status-copy">
              <div className="status-title">LibTV 正在生成</div>
              {visibleContent && <div className="status-text">{renderPlain(visibleContent)}</div>}
            </div>
            {hasProgress && (
              <ProgressIndicator
                currentStep={currentStep || 0}
                totalSteps={totalSteps || 5}
                progressPercent={progressPercent || 0}
                stepName={stepName || ''}
                steps={steps || ['等待开始', '排队中', 'AI 理解', '模型生成', '生成完成']}
              />
            )}
          </div>
        )}
        {coverUrl && (
          <div className="bubble-image">
            <div className="image-label">文章封面（2.35:1）</div>
            <img src={coverUrl} alt="生成的封面图" />
          </div>
        )}
        {isImage && imageUrl && (
          <div className="bubble-image">
            <div className="image-label">文章配图（2.35:1）</div>
            <img src={imageUrl} alt="生成的配图" />
          </div>
        )}
        {isVideo && mediaUrls.length > 0 && (
          <div className="bubble-media-list">
            {mediaUrls.map((url) => (
              <MediaResult key={url} url={url} />
            ))}
          </div>
        )}
        {!isLoading && !isStatus && isComplianceResult && (
          <ComplianceResult content={visibleContent} cleanedContent={cleanedContent} />
        )}
        {!isLoading && !isStatus && !isComplianceResult && visibleContent && (
          <div className="bubble-text">
            {useMarkdown ? (
              <ReactMarkdown remarkPlugins={[remarkGfm]}>{visibleContent}</ReactMarkdown>
            ) : (
              renderPlain(visibleContent)
            )}
          </div>
        )}
        {model && !isUser && !isError && (
          <div className="bubble-meta">via {model}</div>
        )}
        {(isVideo || isStatus) && projectUrl && !isUser && !isError && (
          <div className="bubble-meta">
            <a href={projectUrl} target="_blank" rel="noopener noreferrer">打开 LibTV 项目画布</a>
          </div>
        )}
        {(isVideo || isStatus) && sessionId && !isUser && !isError && (
          <div className="bubble-meta">sessionId: {sessionId}</div>
        )}
      </div>
    </div>
  )
}

function ComplianceResult({ content, cleanedContent }) {
  const [showCleaned, setShowCleaned] = useState(true)
  const hasCleanedText = typeof cleanedContent === 'string' && cleanedContent.length > 0

  return (
    <div className="compliance-result">
      <div className="compliance-header">
        <div>
          <div className="compliance-title">合规检测结果</div>
          <div className="compliance-subtitle">已按词库命中优先，必要时追加 RAG 复核</div>
        </div>
        <CopyButton text={content} variant="inline" label="复制报告" />
      </div>

      <div className="compliance-report bubble-text">
        <ReactMarkdown remarkPlugins={[remarkGfm]}>{content}</ReactMarkdown>
      </div>

      {hasCleanedText && (
        <div className="cleaned-panel">
          <div className="cleaned-toolbar">
            <button
              type="button"
              className="cleaned-toggle"
              onClick={() => setShowCleaned((v) => !v)}
            >
              {showCleaned ? '收起剔除后文本' : '查看剔除后文本'}
            </button>
            <CopyButton text={cleanedContent} variant="inline" label="复制剔除后文本" />
          </div>
          {showCleaned && (
            <div className="cleaned-content">
              {renderPlain(cleanedContent || ' ')}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function ProgressIndicator({ currentStep, totalSteps, progressPercent, stepName, steps }) {
  return (
    <div className="progress-indicator">
      <div className="progress-header">
        <span className="progress-step-name">{stepName || '处理中'}</span>
        <span className="progress-percent">{progressPercent || 0}%</span>
      </div>
      <div className="progress-bar">
        <div
          className="progress-fill"
          style={{ width: `${progressPercent || 0}%` }}
        />
      </div>
      <div className="progress-steps">
        {steps && steps.map((step, index) => (
          <div
            key={index}
            className={`progress-step ${index < currentStep ? 'completed' : ''} ${index === currentStep ? 'active' : ''}`}
          >
            <div className="step-dot">
              {index < currentStep ? '✓' : index + 1}
            </div>
            <span className="step-label">{step}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

function MediaResult({ url }) {
  const video = isVideoUrl(url)
  return (
    <div className={video ? 'bubble-video' : 'bubble-image'}>
      {video ? (
        <video src={url} controls playsInline />
      ) : (
        <img src={url} alt="LibTV 生成结果" />
      )}
      <DownloadButton imageUrl={url} kind={video ? 'video' : 'image'} />
    </div>
  )
}

function isVideoUrl(url) {
  return /\.(mp4|mov|webm)(\?|$)/i.test(url || '')
}

function renderPlain(text) {
  return text.split('\n').map((line, i) => (
    <p key={i} className="bubble-line">
      {line || ' '}
    </p>
  ))
}

function DownloadButton({ imageUrl, kind = 'image' }) {
  const [status, setStatus] = useState('idle')

  const handleClick = async (e) => {
    e.stopPropagation()
    const ext = kind === 'video' ? 'mp4' : 'png'
    const filename = `agent-${kind}-${new Date().toISOString().replace(/[:.]/g, '-')}.${ext}`
    try {
      setStatus('loading')
      const res = await fetch(imageUrl)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const blob = await res.blob()
      const objectUrl = URL.createObjectURL(blob)
      triggerDownload(objectUrl, filename)
      URL.revokeObjectURL(objectUrl)
      setStatus('done')
      setTimeout(() => setStatus('idle'), 1500)
    } catch {
      triggerDownload(imageUrl, filename, true)
      setStatus('idle')
    }
  }

  return (
    <button
      type="button"
      className={'copy-btn download-btn' + (status === 'done' ? ' is-copied' : '')}
      onClick={handleClick}
      title={kind === 'video' ? '下载视频' : '下载图片'}
      disabled={status === 'loading'}
    >
      {status === 'loading' ? '下载中' : status === 'done' ? '已下载' : '下载'}
    </button>
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

function CopyButton({ text, variant = 'float', label = '复制' }) {
  const [copied, setCopied] = useState(false)
  const [error, setError] = useState(false)

  const handleClick = async (e) => {
    e.stopPropagation()
    try {
      await copyToClipboard(stripMarkdown(text))
      setCopied(true)
      setError(false)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      setError(true)
      setTimeout(() => setError(false), 1500)
    }
  }

  return (
    <button
      type="button"
      className={(variant === 'inline' ? 'copy-btn-inline' : 'copy-btn') + (copied ? ' is-copied' : '') + (error ? ' is-error' : '')}
      onClick={handleClick}
      title="复制为纯文本"
    >
      {error ? '失败' : copied ? '已复制' : label}
    </button>
  )
}
