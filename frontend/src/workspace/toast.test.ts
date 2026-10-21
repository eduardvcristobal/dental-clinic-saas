import { describe, expect, it } from 'vitest'
import { describeError, MAX_VISIBLE_TOASTS, nextToastList, toastDuration } from './toast'

describe('toastDuration', () => {
  it('keeps errors on screen longer than confirmations', () => {
    expect(toastDuration('error')).toBeGreaterThan(toastDuration('success'))
    expect(toastDuration('info')).toBeGreaterThan(toastDuration('success'))
  })
})

describe('describeError', () => {
  it('reads the message of an Error', () => {
    expect(describeError(new Error('Choose a chair.'), 'fallback')).toBe('Choose a chair.')
  })

  it('does not leak the error class name into the message', () => {
    const error = new Error('Not allowed')
    error.name = 'ApiError'
    expect(describeError(error, 'fallback')).toBe('Not allowed')
    expect(describeError(error, 'fallback')).not.toContain('ApiError')
  })

  it('uses the fallback for values without a message', () => {
    expect(describeError(new Error('   '), 'fallback')).toBe('fallback')
    expect(describeError(undefined, 'fallback')).toBe('fallback')
    expect(describeError({}, 'fallback')).toBe('fallback')
  })

  it('accepts a plain string', () => {
    expect(describeError('Network down', 'fallback')).toBe('Network down')
  })
})

describe('nextToastList', () => {
  it('appends the new toast', () => {
    expect(nextToastList([{ id: 1 }], { id: 2 })).toEqual([{ id: 1 }, { id: 2 }])
  })

  it('drops the oldest toast once the stack is full', () => {
    const current = Array.from({ length: MAX_VISIBLE_TOASTS }, (_, index) => ({ id: index + 1 }))
    const result = nextToastList(current, { id: MAX_VISIBLE_TOASTS + 1 })
    expect(result).toHaveLength(MAX_VISIBLE_TOASTS)
    expect(result[result.length - 1].id).toBe(MAX_VISIBLE_TOASTS + 1)
    expect(result.some(toast => toast.id === 1)).toBe(false)
  })
})
