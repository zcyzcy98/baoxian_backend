import { useEffect, useRef } from 'react'
import AgentForm from './AgentForm'
import MessageBubble from './MessageBubble'
import './MainPanel.css'

const introMessage = (agent) => ({
  role: 'assistant',
  type: 'text',
  content: agent.intro || `你好, 这是 ${agent.name}。`,
})

export default function MainPanel({ agent, messages, loading, onSubmit, onClear, onNewLibTvProject, prefill, onPrefillConsumed }) {
  const scrollRef = useRef(null)

  const displayMessages = messages && messages.length > 0 ? messages : [introMessage(agent)]
  const hasHistory = messages && messages.length > 0
  const isLibTv = agent.endpoint === 'video-generate'

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight
    }
  }, [displayMessages, loading])

  const handleSubmit = (formValues, model) => {
    onSubmit(agent, formValues, model)
  }

  return (
    <main className="main-panel">
      <header className="main-header">
        <h1 className="main-title">{agent.name}</h1>
        <div className="header-actions">
          {isLibTv && (
            <button
              type="button"
              className="header-action"
              onClick={() => onNewLibTvProject?.(agent)}
              disabled={loading}
              title="在 LibTV 新建项目，后续生成会进入新的项目会话"
            >
              新建 LibTV 项目
            </button>
          )}
          <button
            type="button"
            className="header-action"
            onClick={onClear}
            disabled={!hasHistory || loading}
            title="清空当前智能体的对话历史"
          >
            清空对话
          </button>
        </div>
      </header>

      <div className="main-scroll" ref={scrollRef}>
        <div className="main-content">
          {displayMessages.map((msg, idx) => (
            <MessageBubble key={idx} message={msg} />
          ))}
          {loading && (
            <MessageBubble
              message={{ role: 'assistant', type: 'loading', content: '生成中...' }}
            />
          )}
        </div>
      </div>

      <div className="main-footer">
        {agent.disabled || !agent.endpoint ? (
          <div className="disabled-hint">该智能体为占位项, 暂未启用。请从侧栏选择已启用的智能体。</div>
        ) : (
          <AgentForm
            agent={agent}
            onSubmit={handleSubmit}
            disabled={loading}
            prefill={prefill}
            onPrefillConsumed={onPrefillConsumed}
          />
        )}
      </div>
    </main>
  )
}
