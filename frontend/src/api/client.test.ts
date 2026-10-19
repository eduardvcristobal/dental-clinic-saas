import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, refreshSession, setAccessToken, setSessionExpiredHandler } from './client'
import type { Session } from './client'

const session: Session = {
  accessToken: 'new-access-token',
  user: { id: 'user-1', email: 'staff@example.invalid', fullName: 'Test Staff', role: 'OWNER' },
  clinic: { id: 'clinic-1', slug: 'test-clinic', name: 'Test Clinic' },
}

afterEach(() => {
  vi.unstubAllGlobals()
  setAccessToken(null)
  setSessionExpiredHandler(null)
})

describe('session API client', () => {
  it('coalesces concurrent refresh requests to one cookie-backed call', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(session), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    const [first, second] = await Promise.all([refreshSession(), refreshSession()])

    expect(first?.accessToken).toBe('new-access-token')
    expect(second?.accessToken).toBe('new-access-token')
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'POST', credentials: 'include' })
  })

  it('restores an access token after a 401 and retries the protected request once', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response('', { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(session), { status: 200 }))
      .mockImplementationOnce(async (_input: RequestInfo | URL, init?: RequestInit) => {
        expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer new-access-token')
        return new Response(JSON.stringify({ ok: true }), { status: 200 })
      })
    vi.stubGlobal('fetch', fetchMock)

    await expect(api<{ ok: boolean }>('/api/private')).resolves.toEqual({ ok: true })
    expect(fetchMock).toHaveBeenCalledTimes(3)
  })

  it('clears the session and signals expiry when refresh is rejected', async () => {
    const expired = vi.fn()
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response('', { status: 401 }))
      .mockResolvedValueOnce(new Response('', { status: 401 }))
    vi.stubGlobal('fetch', fetchMock)
    setAccessToken('expired-token')
    setSessionExpiredHandler(expired)

    await expect(api('/api/private')).rejects.toMatchObject({ status: 401 })
    expect(expired).toHaveBeenCalledOnce()
  })
})
