import { useEffect, useRef, type ReactNode } from 'react'
import { Icon } from './Icon'

// Модальное окно на нативном <dialog>, как в макете: Esc и клик по фону закрывают.

interface DialogProps {
  title: string
  className?: string
  onClose: () => void
  actions?: ReactNode
  children: ReactNode
}

export function Dialog({ title, className = '', onClose, actions, children }: DialogProps) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = ref.current
    if (dialog && !dialog.open) dialog.showModal()
    return () => dialog?.close()
  }, [])

  const closeOnBackdrop = (event: React.MouseEvent<HTMLDialogElement>) => {
    if (event.target !== ref.current) return
    const rect = ref.current.getBoundingClientRect()
    const outside = event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom
    if (outside) onClose()
  }

  return (
    <dialog
      ref={ref}
      className={`dialog ${className}`}
      aria-label={title}
      onCancel={(event) => { event.preventDefault(); onClose() }}
      onClick={closeOnBackdrop}
    >
      <div className="dialog-heading">
        <h2>{title}</h2>
        <button type="button" className="icon-button" aria-label="Закрыть" onClick={onClose}><Icon name="close" /></button>
      </div>
      <div className="dialog-body">{children}</div>
      {actions && <div className="dialog-actions">{actions}</div>}
    </dialog>
  )
}
