import { useState } from 'react'
import './ConfirmModal.css'

/**
 * 项目风格的确认弹窗。受控组件。
 *
 * 用法 A — 直接受控：
 *   <ConfirmModal open={open} title="..." message="..." onConfirm={...} onCancel={...} />
 *
 * 用法 B — 配合 useConfirmModal hook（推荐）：
 *   const { confirm, props } = useConfirmModal()
 *   confirm({ title: '返回会丢失内容', message: '...', confirmText: '确认返回', onConfirm: () => setStep(n) })
 *   <ConfirmModal {...props} />
 */
export default function ConfirmModal({
  open,
  title,
  message,
  confirmText = '确认',
  cancelText = '取消',
  onConfirm,
  onCancel,
}) {
  if (!open) return null
  return (
    <div className="confirm-modal-backdrop" onClick={onCancel}>
      <div className="confirm-modal-sheet" onClick={e => e.stopPropagation()}>
        <div className="confirm-modal-top">
          <h3>{title || '请确认'}</h3>
          <button className="confirm-modal-close" onClick={onCancel} aria-label="关闭">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div className="confirm-modal-body">{message}</div>
        <div className="confirm-modal-footer">
          <button className="confirm-btn-ghost" onClick={onCancel}>{cancelText}</button>
          <button className="confirm-btn-solid" onClick={onConfirm}>{confirmText}</button>
        </div>
      </div>
    </div>
  )
}

/**
 * 简化状态管理：在页面顶部一次性 const { confirm, props } = useConfirmModal()
 * 然后任何位置调用 confirm({...}) 即可弹窗。
 */
export function useConfirmModal() {
  const [config, setConfig] = useState(null)
  const confirm = (cfg) => setConfig(cfg)
  const close = () => setConfig(null)
  const handleConfirm = () => {
    const fn = config?.onConfirm
    close()
    fn?.()
  }
  return {
    confirm,
    close,
    props: {
      open: !!config,
      title: config?.title,
      message: config?.message,
      confirmText: config?.confirmText,
      cancelText: config?.cancelText,
      onConfirm: handleConfirm,
      onCancel: close,
    },
  }
}
