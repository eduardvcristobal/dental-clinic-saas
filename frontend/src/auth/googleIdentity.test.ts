import { afterEach, describe, expect, it, vi } from 'vitest'
import { mountGoogleCredentialButton } from './googleIdentity'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('Google Identity button lifecycle', () => {
  it('initializes once when the login button moves between routes', () => {
    let callback: ((credential: { credential?: string }) => void) | undefined
    const initialize = vi.fn((config: { callback: (credential: { credential?: string }) => void }) => { callback = config.callback })
    const renderButton = vi.fn()
    vi.stubGlobal('window', { google: { accounts: { id: { initialize, renderButton } } } })
    const firstContainer = { replaceChildren: vi.fn() } as unknown as HTMLElement
    const secondContainer = { replaceChildren: vi.fn() } as unknown as HTMLElement
    const firstSuccess = vi.fn()
    const secondSuccess = vi.fn()
    const options = { type: 'standard' as const, size: 'large' as const }

    const unmountFirst = mountGoogleCredentialButton('test-client', firstContainer, {
      onSuccess: firstSuccess,
      onError: vi.fn(),
    }, options)
    unmountFirst()
    mountGoogleCredentialButton('test-client', secondContainer, {
      onSuccess: secondSuccess,
      onError: vi.fn(),
    }, options)

    expect(initialize).toHaveBeenCalledOnce()
    expect(renderButton).toHaveBeenCalledTimes(2)
    callback?.({ credential: 'synthetic-google-id-token' })
    expect(firstSuccess).not.toHaveBeenCalled()
    expect(secondSuccess).toHaveBeenCalledOnce()
  })

  it('routes an empty Google response to the active error callback', () => {
    let callback: ((credential: { credential?: string }) => void) | undefined
    const initialize = vi.fn((config: { callback: (credential: { credential?: string }) => void }) => { callback = config.callback })
    vi.stubGlobal('window', { google: { accounts: { id: { initialize, renderButton: vi.fn() } } } })
    const onError = vi.fn()
    mountGoogleCredentialButton('test-client', {} as HTMLElement, {
      onSuccess: vi.fn(), onError,
    }, {})

    callback?.({})
    expect(onError).toHaveBeenCalledOnce()
  })
})
