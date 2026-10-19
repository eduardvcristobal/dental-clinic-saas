import { createServer } from 'node:http'
import { generateKeyPairSync, sign } from 'node:crypto'

// Local-only identity provider for authenticated development E2E; never accepts or stores real credentials.
const host = '127.0.0.1'
const port = Number(process.env.M25_JWKS_PORT || 17890)
const audience = process.env.M25_TEST_GOOGLE_CLIENT_ID || 'm25-live-test-client'
const kid = 'm25-local-e2e'
const { publicKey, privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 })
const publicJwk = { ...publicKey.export({ format: 'jwk' }), kid, use: 'sig', alg: 'RS256' }
const b64 = (value) => Buffer.from(JSON.stringify(value)).toString('base64url')

createServer((req, res) => {
  res.setHeader('Cache-Control', 'no-store')
  if (req.method === 'GET' && req.url === '/keys') {
    res.writeHead(200, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify({ keys: [publicJwk] }))
    return
  }
  if (req.method === 'POST' && req.url === '/token') {
    let body = ''
    req.on('data', (chunk) => { body += chunk })
    req.on('end', () => {
      try {
        const { email } = JSON.parse(body)
        if (typeof email !== 'string' || !email.endsWith('@m25.test')) throw new Error('invalid test email')
        const now = Math.floor(Date.now() / 1000)
        const head = b64({ alg: 'RS256', typ: 'JWT', kid })
        const payload = b64({ iss: 'https://accounts.google.com', aud: audience, sub: `m25-${email}`, email, email_verified: true, iat: now, exp: now + 180 })
        const signingInput = `${head}.${payload}`
        const token = `${signingInput}.${sign('RSA-SHA256', Buffer.from(signingInput), privateKey).toString('base64url')}`
        res.writeHead(200, { 'Content-Type': 'application/json' })
        res.end(JSON.stringify({ idToken: token }))
      } catch {
        res.writeHead(400)
        res.end()
      }
    })
    return
  }
  res.writeHead(404)
  res.end()
}).listen(port, host)
