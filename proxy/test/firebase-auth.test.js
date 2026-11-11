import assert from 'node:assert/strict'
import { test } from 'node:test'
import { AuthError, verifyFirebaseToken } from '../src/firebase-auth.js'

const PROJECT = 'anime-watch-873b9'
const NOW = 1_800_000_000
const ALGORITHM = { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]) }

const good = await crypto.subtle.generateKey(ALGORITHM, true, ['sign', 'verify'])
const other = await crypto.subtle.generateKey(ALGORITHM, true, ['sign', 'verify'])
const getKey = async (kid) => (kid === 'k1' ? good.publicKey : undefined)

const b64url = (bytes) => Buffer.from(bytes).toString('base64url')
const json = (value) => b64url(new TextEncoder().encode(JSON.stringify(value)))

async function token({ header = {}, claims = {}, key = good.privateKey } = {}) {
    const h = json({ alg: 'RS256', kid: 'k1', typ: 'JWT', ...header })
    const p = json({
        aud: PROJECT,
        iss: `https://securetoken.google.com/${PROJECT}`,
        sub: 'uid-123',
        iat: NOW - 100,
        auth_time: NOW - 1000,
        exp: NOW + 3000,
        ...claims,
    })
    const signature = await crypto.subtle.sign(ALGORITHM, key, new TextEncoder().encode(`${h}.${p}`))
    return `${h}.${p}.${b64url(new Uint8Array(signature))}`
}

const verify = (t) => verifyFirebaseToken(t, PROJECT, { now: NOW, getKey })

test('accetta un token valido e restituisce lo uid', async () => {
    assert.deepEqual(await verify(await token()), { uid: 'uid-123' })
})

const rejected = {
    'firma di un\'altra chiave': () => token({ key: other.privateKey }),
    'chiave sconosciuta': () => token({ header: { kid: 'k2' } }),
    'algoritmo diverso da RS256': () => token({ header: { alg: 'HS256' } }),
    'altro progetto (aud)': () => token({ claims: { aud: 'altro-progetto' } }),
    'altro emittente (iss)': () => token({ claims: { iss: 'https://securetoken.google.com/altro-progetto' } }),
    'token scaduto': () => token({ claims: { exp: NOW - 1 } }),
    'emesso nel futuro': () => token({ claims: { iat: NOW + 3600 } }),
    'uid vuoto': () => token({ claims: { sub: '' } }),
    'payload modificato dopo la firma': async () => {
        const [h, , s] = (await token()).split('.')
        return `${h}.${json({ aud: PROJECT, iss: `https://securetoken.google.com/${PROJECT}`, sub: 'altro', iat: NOW, auth_time: NOW, exp: NOW + 10 })}.${s}`
    },
    'token malformato': async () => 'non.un-token',
    'token assente': async () => '',
}

for (const [name, make] of Object.entries(rejected)) {
    test(`rifiuta: ${name}`, async () => {
        await assert.rejects(verify(await make()), AuthError)
    })
}
