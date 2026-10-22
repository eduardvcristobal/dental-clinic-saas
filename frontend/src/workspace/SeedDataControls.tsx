import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { useToast } from './toastContext'
import { describeError } from './toast'

export function SeedDataControls() {
  const [enabled, setEnabled] = useState(false)
  const [count, setCount] = useState(10)
  const [busy, setBusy] = useState(false)
  const toast = useToast()

  useEffect(() => {
    let active = true
    void api<{enabled:boolean}>('/api/dev/seed/status', {}, false)
      .then(status => { if (active) setEnabled(status.enabled) })
      .catch(() => { if (active) setEnabled(false) })
    return () => { active = false }
  }, [])

  if (!enabled) return null

  async function seedPatients() {
    setBusy(true)
    try {
      const result = await api<{created:number}>(`/api/dev/seed/patients?count=${count}`, {method:'POST'})
      toast.success(`Added ${result.created} synthetic patients`)
      window.dispatchEvent(new Event('clinic-seed-data'))
    } catch (error) {
      toast.error(describeError(error, 'Could not seed patients'))
    } finally {
      setBusy(false)
    }
  }

  return <div className="ws-seed-controls">
    <label className="ws-seed-count">Seed patients
      <input aria-label="Number of synthetic patients to seed" type="number" min={1} max={100} value={count}
        onChange={event => setCount(Math.max(1, Math.min(100, Number(event.target.value) || 1)))}/>
    </label>
    <button type="button" disabled={busy} onClick={() => void seedPatients()}>{busy ? 'Seeding…' : 'Add synthetic patients'}</button>
  </div>
}
