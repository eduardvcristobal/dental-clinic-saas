export type WorkspaceRole = 'OWNER' | 'ADMIN' | 'DENTIST' | 'FRONT_DESK'
export type CapabilityState = 'active' | 'preview' | 'coming-soon'
export interface WorkspaceModule { id: string; title: string; group: string; description: string; state: CapabilityState; roles: WorkspaceRole[] }
const ALL: WorkspaceRole[] = ['OWNER', 'ADMIN', 'DENTIST', 'FRONT_DESK']
const CLINICAL: WorkspaceRole[] = ['OWNER', 'ADMIN', 'DENTIST']
const ADMIN: WorkspaceRole[] = ['OWNER', 'ADMIN']
export const WORKSPACE_MODULES: WorkspaceModule[] = [
  { id: 'command-center', title: 'Command Center', group: 'Workspace', description: 'A role-specific view of the working day', state: 'preview', roles: ALL },
  { id: 'journey', title: 'Patient Journey', group: 'Workspace', description: 'End-to-end workflow design simulation', state: 'preview', roles: ALL },
  { id: 'calendar', title: 'Calendar', group: 'Workspace', description: 'Dentist and chair scheduling', state: 'preview', roles: ALL },
  { id: 'patients', title: 'Patients', group: 'Workspace', description: 'Patient directory and search', state: 'preview', roles: ALL },
  { id: 'check-in', title: 'Check-in', group: 'Workspace', description: 'Arrivals and waiting room', state: 'coming-soon', roles: ALL },
  { id: 'patient-360', title: 'Patient 360°', group: 'Clinical', description: 'Unified dental patient record', state: 'preview', roles: ALL },
  { id: 'odontogram', title: 'Odontogram', group: 'Clinical', description: 'Tooth and surface history', state: 'coming-soon', roles: CLINICAL },
  { id: 'encounters', title: 'Encounters & SOAP', group: 'Clinical', description: 'Signed clinical visits and notes', state: 'coming-soon', roles: CLINICAL },
  { id: 'treatment-plans', title: 'Treatment Plans', group: 'Clinical', description: 'Proposed and accepted treatment', state: 'coming-soon', roles: CLINICAL },
  { id: 'imaging', title: 'Imaging & Files', group: 'Clinical', description: 'Radiographs and clinical attachments', state: 'coming-soon', roles: CLINICAL },
  { id: 'prescriptions', title: 'Prescriptions', group: 'Clinical', description: 'Medication orders', state: 'coming-soon', roles: CLINICAL },
  { id: 'billing', title: 'Billing', group: 'Revenue', description: 'Invoices and collections', state: 'preview', roles: ALL },
  { id: 'installments', title: 'Installments', group: 'Revenue', description: 'Payment schedules', state: 'coming-soon', roles: ALL },
  { id: 'price-list', title: 'Procedure Price List', group: 'Revenue', description: 'Services and pricing', state: 'coming-soon', roles: ADMIN },
  { id: 'reminders', title: 'Reminders & Recalls', group: 'Growth', description: 'Appointment reminders and recalls', state: 'coming-soon', roles: ALL },
  { id: 'reports', title: 'Reports', group: 'Growth', description: 'Production and collections insights', state: 'preview', roles: ADMIN },
  { id: 'patient-portal', title: 'Patient Portal', group: 'Growth', description: 'Future patient self-service', state: 'coming-soon', roles: ADMIN },
  { id: 'branches', title: 'Branches', group: 'Administration', description: 'Clinic locations and operating hours', state: 'coming-soon', roles: ADMIN },
  { id: 'staff', title: 'Staff & Roles', group: 'Administration', description: 'Personnel and permissions', state: 'coming-soon', roles: ADMIN },
  { id: 'chairs', title: 'Chairs & Resources', group: 'Administration', description: 'Chair and resource setup', state: 'coming-soon', roles: ADMIN },
  { id: 'settings', title: 'Clinic Settings', group: 'Administration', description: 'Clinic preferences', state: 'coming-soon', roles: ADMIN },
  { id: 'module-status', title: 'Module Status', group: 'Administration', description: 'Feature readiness and navigation governance', state: 'preview', roles: ADMIN },
  { id: 'audit-log', title: 'Audit Log', group: 'Administration', description: 'Read-only clinical and system audit activity', state: 'coming-soon', roles: ADMIN },
]
export function canSeeModule(id: string, role: string): boolean { return WORKSPACE_MODULES.some(m => m.id === id && m.roles.includes(role as WorkspaceRole)) }
export const DISPLAY_ROLES: Record<WorkspaceRole, string> = { OWNER: 'Clinic Owner', ADMIN: 'Clinic Administrator', DENTIST: 'Dentist', FRONT_DESK: 'Reception / Front Desk' }
