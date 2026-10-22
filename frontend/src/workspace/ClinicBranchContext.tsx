import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { api } from '../api/client'

export type ClinicBranch = { id: string; name: string; active: boolean; address?: string | null; phone?: string | null }
type ClinicBranchState = {
  branches: ClinicBranch[]
  branchId: string
  setBranchId: (id: string) => void
  setBranches: (branches: ClinicBranch[]) => void
}

const ClinicBranchContext = createContext<ClinicBranchState>({
  branches: [], branchId: '', setBranchId: () => undefined, setBranches: () => undefined,
})

export function ClinicBranchProvider({ children }: { children: ReactNode }) {
  const [branches, setBranches] = useState<ClinicBranch[]>([])
  const [branchId, setBranchId] = useState(() => window.localStorage.getItem('clinic.activeBranch') ?? '')

  useEffect(() => {
    let active = true
    void api<ClinicBranch[]>('/api/clinic-management/branches').then(rows => {
      if (!active) return
      setBranches(rows)
      setBranchId(current => rows.some(branch => branch.id === current)
        ? current
        : rows.find(branch => branch.active)?.id ?? rows[0]?.id ?? '')
    }).catch(() => {
      if (active) setBranches([])
    })
    return () => { active = false }
  }, [])

  useEffect(() => {
    if (branchId) window.localStorage.setItem('clinic.activeBranch', branchId)
  }, [branchId])

  return <ClinicBranchContext.Provider value={{ branches, branchId, setBranchId, setBranches }}>
    {children}
  </ClinicBranchContext.Provider>
}

export function useClinicBranch() {
  return useContext(ClinicBranchContext)
}
