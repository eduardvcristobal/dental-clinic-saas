import { useEffect, useId, useRef, useState } from 'react'
import { nextEnabledIndex, selectedIndex, triggerText } from './dropdownOptions'
import type { DropdownOption } from './dropdownOptions'

export type { DropdownOption } from './dropdownOptions'

/**
 * Accessible single-select dropdown following the ARIA select-only combobox pattern.
 * Focus stays on the trigger while the listbox is open and the active option is
 * published through `aria-activedescendant`.
 */
export function Dropdown({ label, value, options, placeholder = 'Select…', onChange, disabled, required, invalid, testId, id, visuallyHidden }: {
  label: string
  value: string
  options: DropdownOption[]
  placeholder?: string
  onChange: (value: string) => void
  disabled?: boolean
  required?: boolean
  invalid?: boolean
  testId?: string
  id?: string
  visuallyHidden?: boolean
}) {
  const generated = useId()
  const controlId = id ?? generated
  const menuId = `${controlId}-menu`
  const optionId = (index: number) => `${controlId}-option-${index}`
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const [placement, setPlacement] = useState<'bottom' | 'top'>('bottom')
  const root = useRef<HTMLDivElement | null>(null)
  const trigger = useRef<HTMLButtonElement | null>(null)
  const text = triggerText(options, value, placeholder)

  const close = (restore = true) => {
    setOpen(false)
    setActive(-1)
    if (restore) trigger.current?.focus()
  }

  const openMenu = () => {
    if (disabled) return
    const rect = trigger.current?.getBoundingClientRect()
    if (rect) setPlacement(window.innerHeight - rect.bottom < 240 && rect.top > 240 ? 'top' : 'bottom')
    setOpen(true)
    const current = selectedIndex(options, value)
    setActive(current >= 0 ? current : nextEnabledIndex(options, -1, 1))
  }

  const choose = (option: DropdownOption) => {
    if (option.disabled) return
    onChange(option.value)
    close()
  }

  useEffect(() => {
    if (!open) return
    const onPointerDown = (event: PointerEvent) => {
      if (root.current && !root.current.contains(event.target as Node)) close(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    return () => document.removeEventListener('pointerdown', onPointerDown)
  }, [open])

  useEffect(() => {
    if (!open || active < 0) return
    document.getElementById(`${controlId}-option-${active}`)?.scrollIntoView({ block: 'nearest' })
  }, [open, active, controlId])

  const onKeyDown = (event: React.KeyboardEvent<HTMLButtonElement>) => {
    if (disabled) return
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      if (!open) { openMenu(); return }
      const delta = event.key === 'ArrowDown' ? 1 : -1
      const from = active >= 0 ? active : (delta > 0 ? -1 : options.length)
      setActive(nextEnabledIndex(options, from, delta))
      return
    }
    if (event.key === 'Home' || event.key === 'End') {
      if (!open) return
      event.preventDefault()
      setActive(nextEnabledIndex(options, event.key === 'Home' ? -1 : options.length, event.key === 'Home' ? 1 : -1))
      return
    }
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      if (!open) { openMenu(); return }
      const option = options[active]
      if (option) choose(option)
      return
    }
    if (event.key === 'Escape') {
      if (!open) return
      event.preventDefault()
      close()
      return
    }
    if (event.key === 'Tab' && open) close(false)
  }

  return <div className="ws-dropdown" ref={root} data-open={open ? 'true' : undefined} data-placement={placement}>
    <label className={visuallyHidden ? 'ws-dropdown-label ws-sr-only' : 'ws-dropdown-label'} htmlFor={controlId}>{label}{required && <span aria-hidden="true"> *</span>}</label>
    <button id={controlId} ref={trigger} type="button" data-testid={testId} role="combobox" aria-haspopup="listbox" aria-expanded={open} aria-controls={menuId} aria-autocomplete="none" aria-required={required} aria-invalid={invalid || undefined} aria-activedescendant={open && active >= 0 ? optionId(active) : undefined} className="ws-dropdown-trigger" data-placeholder={value ? undefined : 'true'} data-invalid={invalid ? 'true' : undefined} disabled={disabled} onClick={() => { if (open) close(); else openMenu() }} onKeyDown={onKeyDown}>
      <span className="ws-dropdown-value">{text}</span>
      <svg className="ws-dropdown-caret" viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="m5 8 5 5 5-5"/></svg>
    </button>
    {open && <ul id={menuId} role="listbox" aria-label={label} className="ws-dropdown-menu">
      {options.length === 0 && <li className="ws-dropdown-empty">No options available</li>}
      {options.map((option, index) => <li key={option.value} id={optionId(index)} role="option" aria-selected={option.value === value} aria-disabled={option.disabled || undefined} className={`ws-dropdown-option${index === active ? ' active' : ''}`} data-active={index === active ? 'true' : undefined} onMouseEnter={() => { if (!option.disabled) setActive(index) }} onMouseDown={event => event.preventDefault()} onClick={() => choose(option)}>
        <span className="ws-dropdown-check" aria-hidden="true">{option.value === value && <svg viewBox="0 0 20 20" fill="none"><path d="m4 10 4 4 8-8"/></svg>}</span>
        <span className="ws-dropdown-option-label">{option.label}</span>
        {option.hint && <span className="ws-dropdown-option-hint">{option.hint}</span>}
      </li>)}
    </ul>}
  </div>
}
