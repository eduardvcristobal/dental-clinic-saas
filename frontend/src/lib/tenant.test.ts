import { describe, expect, it } from 'vitest'
import { normalizeSlug, slugFromHost } from './tenant'

describe('slugFromHost', () => {
  it('reads the clinic subdomain from a production host', () => {
    expect(slugFromHost('greenvalley.clinic.example.com')).toBe('greenvalley')
  })

  it('ignores reserved subdomains so the app domain never becomes a clinic code', () => {
    expect(slugFromHost('app.clinic.example.com')).toBeNull()
    expect(slugFromHost('www.clinic.example.com')).toBeNull()
    expect(slugFromHost('api.clinic.example.com')).toBeNull()
  })

  it('asks for a clinic code on localhost and short hostnames', () => {
    expect(slugFromHost('localhost')).toBeNull()
    expect(slugFromHost('clinic.local')).toBeNull()
  })

  it('never treats an IP address as a clinic subdomain', () => {
    expect(slugFromHost('127.0.0.1')).toBeNull()
    expect(slugFromHost('192.168.1.10')).toBeNull()
    expect(slugFromHost('10.0.0.1')).toBeNull()
  })

  it('ignores IPv6 literals and trailing dots', () => {
    expect(slugFromHost('[::1]')).toBeNull()
    expect(slugFromHost('::1')).toBeNull()
    expect(slugFromHost('greenvalley.clinic.example.com.')).toBe('greenvalley')
  })

  it('is case-insensitive and tolerates surrounding whitespace', () => {
    expect(slugFromHost(' GreenValley.Clinic.Example.COM ')).toBe('greenvalley')
  })
})

describe('normalizeSlug', () => {
  it('trims and lowercases the typed clinic code', () => {
    expect(normalizeSlug('  Sunrise-Dental ')).toBe('sunrise-dental')
  })
})
