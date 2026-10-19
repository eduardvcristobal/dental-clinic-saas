export type GoogleCredential = {
  credential?: string
  clientId?: string
  select_by?: string
}

export type GoogleButtonOptions = {
  type?: 'standard' | 'icon'
  theme?: 'outline' | 'filled_blue' | 'filled_black'
  size?: 'large' | 'medium' | 'small'
  text?: 'signin_with' | 'signup_with' | 'continue_with' | 'signin'
  shape?: 'rectangular' | 'pill' | 'circle' | 'square'
  width?: string | number
}

type IdentityApi = {
  initialize: (config: { client_id: string; callback: (credential: GoogleCredential) => void }) => void
  renderButton: (container: HTMLElement, options: GoogleButtonOptions) => void
}

type IdentityState = {
  clientId: string
  initialized: boolean
  activeMount: symbol | null
  onSuccess?: (credential: GoogleCredential) => void
  onError?: () => void
}

declare global {
  interface Window {
    google?: { accounts?: { id?: IdentityApi } }
    __dentalGoogleIdentityState?: IdentityState
  }
}

/** Mount a Google button while initializing the page's GSI client only once. */
export function mountGoogleCredentialButton(
  clientId: string,
  container: HTMLElement,
  callbacks: { onSuccess: (credential: GoogleCredential) => void; onError: () => void },
  options: GoogleButtonOptions,
): () => void {
  const identity = window.google?.accounts?.id
  if (!identity) return () => undefined

  let state = window.__dentalGoogleIdentityState
  if (!state || state.clientId !== clientId) {
    state = { clientId, initialized: false, activeMount: null }
    window.__dentalGoogleIdentityState = state
  }

  const mount = Symbol('google-button-mount')
  state.activeMount = mount
  state.onSuccess = callbacks.onSuccess
  state.onError = callbacks.onError

  if (!state.initialized) {
    identity.initialize({
      client_id: clientId,
      callback: (credential) => {
        const current = window.__dentalGoogleIdentityState
        if (!current?.activeMount) return
        if (credential.credential) current.onSuccess?.(credential)
        else current.onError?.()
      },
    })
    state.initialized = true
  }
  identity.renderButton(container, options)

  return () => {
    if (state?.activeMount === mount) {
      state.activeMount = null
      state.onSuccess = undefined
      state.onError = undefined
    }
    container.replaceChildren()
  }
}
