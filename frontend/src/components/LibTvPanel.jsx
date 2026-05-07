import { useEffect, useRef, useState } from 'react'
import AgentForm from './AgentForm'
import MessageBubble from './MessageBubble'
import { fetchLibTvSessionVideos } from '../api'
import './LibTvPanel.css'

export default function LibTvPanel({
  agent,
  messages,
  loading,
  onSubmit,
  onClear,
  onNewLibTvProject,
  libTvPolls,
}) {
  const scrollRef = useRef(null)
  const [querySessionId, setQuerySessionId] = useState('')
  const [queryLoading, setQueryLoading] = useState(false)
  const [queryError, setQueryError] = useState('')
  // 只保存"最终成片"的 URL — 后端 finalOnly:true 永远只返回 1 个
  const [finalVideo, setFinalVideo] = useState(null)
  // 是否已经查询过 (用来区分初始空状态和 "查询过没结果")
  const [queried, setQueried] = useState(false)

  const displayMessages = messages && messages.length > 0 ? messages : []
  const hasHistory = messages && messages.length > 0

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight
    }
  }, [displayMessages, loading])

  useEffect(() => {
    setQuerySessionId('')
    setFinalVideo(null)
    setQueryError('')
    setQueried(false)
  }, [agent.id])

  const handleSubmit = (formValues, model) => {
    onSubmit(agent, formValues, model)
  }

  const availableSessionIds = []
  const seenIds = new Set()
  messages.forEach((msg) => {
    if (msg.sessionId && !seenIds.has(msg.sessionId)) {
      seenIds.add(msg.sessionId)
      availableSessionIds.push({
        id: msg.sessionId,
        timestamp: msg.timestamp || new Date().toISOString(),
      })
    }
  })
  availableSessionIds.sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp))

  const handleQueryBySessionId = async () => {
    const value = querySessionId.trim()
    if (!value || queryLoading) return
    setQueryError('')
    setQueryLoading(true)
    setFinalVideo(null)
    setQueried(true)
    try {
      // finalOnly: true → 后端用 HEAD 请求按 Content-Length 挑最长(=合成成片), 只返回 1 个
      const result = await fetchLibTvSessionVideos(value, 0, true)
      // 后端 videoUrl 字段就是挑出来的最长视频; 兜底用 resultUrls[0]
      const url = result.videoUrl
        || (result.resultUrls || []).find(isVideoUrl)
      if (url) {
        setFinalVideo({
          url,
          sessionId: value,
          projectUrl: result.projectUrl,
        })
      } else {
        setQueryError('该 sessionId 暂无最终成片 (可能还在生成中, 或 sessionId 无效)')
      }
    } catch (err) {
      setQueryError(err.message)
    } finally {
      setQueryLoading(false)
    }
  }

  const handleSelectSession = (sessionId) => {
    setQuerySessionId(sessionId)
    setFinalVideo(null)
    setQueryError('')
    setQueried(false)
  }

  return (
    <main className="libtv-panel">
      <header className="libtv-header">
        <h1 className="libtv-title">{agent.name}</h1>
        <div className="header-actions">
          <button
            type="button"
            className="header-action"
            onClick={() => onNewLibTvProject?.(agent)}
            disabled={loading}
            title="在 LibTV 新建项目，后续生成会进入新的项目会话"
          >
            新建项目
          </button>
          <button
            type="button"
            className="header-action"
            onClick={onClear}
            disabled={!hasHistory || loading}
            title="清空当前对话历史"
          >
            清空对话
          </button>
        </div>
      </header>

      <div className="libtv-body">
        <section className="libtv-left">
          <div className="left-section session-selector">
            <h3 className="section-title">
              <span className="query-icon" />
              选择 Session
            </h3>

            {availableSessionIds.length > 0 && (
              <div className="session-list">
                <div className="session-list-title">当前会话中的 Session</div>
                {availableSessionIds.slice(0, 5).map((session) => (
                  <button
                    key={session.id}
                    type="button"
                    className={`session-item ${querySessionId === session.id ? 'is-active' : ''}`}
                    onClick={() => handleSelectSession(session.id)}
                  >
                    <span className="session-item-id">{session.id.slice(0, 8)}...</span>
                    <span className="session-item-action">查询</span>
                  </button>
                ))}
              </div>
            )}

            <div className="session-query-form">
              <input
                type="text"
                className="session-query-input"
                placeholder="输入任意 sessionId"
                value={querySessionId}
                onChange={(e) => setQuerySessionId(e.target.value)}
                onKeyPress={(e) => {
                  if (e.key === 'Enter' && !queryLoading) {
                    handleQueryBySessionId()
                  }
                }}
                disabled={queryLoading}
              />
              <button
                type="button"
                className="session-query-btn"
                onClick={handleQueryBySessionId}
                disabled={queryLoading || !querySessionId.trim()}
              >
                {queryLoading ? '...' : '查询'}
              </button>
            </div>

            {queryError && (
              <div className="session-query-error">{queryError}</div>
            )}
          </div>

          <div className="left-section final-video-section">
            <h3 className="section-title">
              <span className="preview-icon" />
              最终成片
              {querySessionId && (
                <span className="video-session-badge">
                  {querySessionId.slice(0, 8)}...
                </span>
              )}
            </h3>

            {queryLoading ? (
              <div className="video-loading">
                <div className="video-loading-spinner" />
                <div>正在查询最终成片...</div>
              </div>
            ) : finalVideo ? (
              <div className="final-video-container">
                <video
                  src={finalVideo.url}
                  controls
                  playsInline
                  preload="metadata"
                />
                <div className="final-video-actions">
                  <button
                    type="button"
                    className="preview-btn"
                    onClick={() => downloadVideo(finalVideo.url)}
                  >
                    ⬇ 下载视频
                  </button>
                  {finalVideo.projectUrl && (
                    <a
                      className="preview-btn preview-btn-secondary"
                      href={finalVideo.projectUrl}
                      target="_blank"
                      rel="noreferrer"
                    >
                      ↗ 打开项目画布
                    </a>
                  )}
                </div>
                <div className="final-video-url" title={finalVideo.url}>
                  {finalVideo.url}
                </div>
              </div>
            ) : queried ? (
              <div className="video-empty">
                <div className="empty-icon">🎬</div>
                <div className="empty-text">该 Session 暂无最终成片</div>
                <div className="empty-hint">可能还在生成中, 或 sessionId 无效</div>
              </div>
            ) : (
              <div className="video-empty">
                <div className="empty-icon">🔍</div>
                <div className="empty-text">请先选择一个 Session</div>
                <div className="empty-hint">从上方列表选择或输入 sessionId 查询</div>
              </div>
            )}
          </div>
        </section>

        <section className="libtv-right">
          <div className="right-content">
            {displayMessages.length === 0 ? (
              <div className="welcome-message">
                <div className="welcome-icon">🎥</div>
                <h2>欢迎使用 LibTV 视频生成</h2>
                <p>输入视频脚本或生成要求，AI 将自动为您生成视频</p>
              </div>
            ) : (
              <div className="message-list" ref={scrollRef}>
                {displayMessages.map((msg, idx) => (
                  <MessageBubble key={idx} message={msg} />
                ))}
                {loading && (
                  <MessageBubble
                    message={{ role: 'assistant', type: 'loading', content: '生成中...' }}
                  />
                )}
              </div>
            )}
          </div>

          <div className="right-form">
            <AgentForm agent={agent} onSubmit={handleSubmit} disabled={loading} />
          </div>
        </section>
      </div>
    </main>
  )
}

function isVideoUrl(url) {
  return /\.(mp4|mov|webm)(\?|$)/i.test(url || '')
}

function downloadVideo(url) {
  const a = document.createElement('a')
  a.href = url
  a.download = `libtv-video-${new Date().toISOString().replace(/[:.]/g, '-')}.mp4`
  a.target = '_blank'
  a.rel = 'noopener noreferrer'
  document.body.appendChild(a)
  a.click()
  a.remove()
}
