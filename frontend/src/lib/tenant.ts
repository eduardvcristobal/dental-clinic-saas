const STORAGE_KEY = 'dental.clinicSlug'
const RESERVED = new Set(['www', 'app', 'api', 'localhost'])

/**
 * Tenant (clinic) identification. In production each clinic uses its own subdomain
 * (greenvalley.yourapp.com -> "greenvalley"). Otherwise (localhost, single domain) the user types
 * the clinic code, and we remember it. The slug only SELECTS the clinic at login;
 * authorization always comes from the backend session, never from this value.
 */
export function slugFromHost(hostname: string = window.location.hostname): string | null {
  const host = hostname.trim().toLowerCase().replace(/\.$/, '')
  if (host.includes(':')) return null // IPv6 literal, e.g. [::1]
  const parts = host.split('.')
  // An IP address such as 127.0.0.1 has a first label ("127") that is not a clinic subdomain.
  if (parts.length >= 3 && parts.every((part) => /^\d+$/.test(part))) return null
  if (parts.length < 3) return null
  const first = parts[0]
  return RESERVED.has(first) ? null : first
}

export function getRememberedSlug(): string {
  try {
    return localStorage.getItem(STORAGE_KEY) ?? ''
  } catch {
    return ''
  }
}

export function rememberSlug(slug: string): void {
  try {
    localStorage.setItem(STORAGE_KEY, slug)
  } catch {
    /* storage unavailable: fine */
  }
}

export function normalizeSlug(raw: string): string {
  return raw.trim().toLowerCase()
}
