// Verifica dei token di Firebase Authentication (ID token) senza Admin SDK, come da documentazione:
// https://firebase.google.com/docs/auth/admin/verify-id-tokens#verify_id_tokens_using_a_third-party_jwt_library
// Firma RS256 con le chiavi pubbliche di Google, poi aud/iss del progetto, scadenza e uid.

const GOOGLE_JWKS_URL = 'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com'
const ALGORITHM = { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }
// Tolleranza sugli orari, per orologi non perfettamente allineati.
const CLOCK_SKEW_SECONDS = 60

export class AuthError extends Error {}

// Chiavi di Google in memoria per la durata indicata da Cache-Control (ruotano ogni poche ore).
let cached = { keys: null, expiresAt: 0 }

async function googleKey(kid) {
    if (!cached.keys || Date.now() >= cached.expiresAt) {
        const response = await fetch(GOOGLE_JWKS_URL)
        if (!response.ok) throw new Error(`chiavi di Google non disponibili: HTTP ${response.status}`)
        const maxAge = Number(/max-age=(\d+)/.exec(response.headers.get('Cache-Control') ?? '')?.[1] ?? 3600)
        const { keys } = await response.json()
        const imported = new Map()
        for (const jwk of keys) {
            imported.set(jwk.kid, await crypto.subtle.importKey('jwk', jwk, ALGORITHM, false, ['verify']))
        }
        cached = { keys: imported, expiresAt: Date.now() + maxAge * 1000 }
    }
    return cached.keys.get(kid)
}

function base64UrlBytes(text) {
    const base64 = text.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(text.length / 4) * 4, '=')
    return Uint8Array.from(atob(base64), (c) => c.charCodeAt(0))
}

function decodeJson(part) {
    return JSON.parse(new TextDecoder().decode(base64UrlBytes(part)))
}

/**
 * Restituisce { uid } se il token è un ID token valido del progetto Firebase indicato, altrimenti lancia AuthError.
 * `now` (secondi) e `getKey` (kid -> CryptoKey) si sostituiscono nei test.
 */
export async function verifyFirebaseToken(token, projectId, { now = Date.now() / 1000, getKey = googleKey } = {}) {
    if (!projectId) throw new Error('FIREBASE_PROJECT_ID non configurato')
    const parts = typeof token === 'string' ? token.split('.') : []
    if (parts.length !== 3) throw new AuthError('token assente o malformato')

    let header, payload, signature
    try {
        header = decodeJson(parts[0])
        payload = decodeJson(parts[1])
        signature = base64UrlBytes(parts[2])
    } catch {
        throw new AuthError('token non decodificabile')
    }
    if (header.alg !== 'RS256' || typeof header.kid !== 'string') throw new AuthError('algoritmo o chiave non validi')

    const key = await getKey(header.kid)
    if (!key) throw new AuthError('chiave sconosciuta')
    const signed = new TextEncoder().encode(`${parts[0]}.${parts[1]}`)
    if (!(await crypto.subtle.verify(ALGORITHM, key, signature, signed))) throw new AuthError('firma non valida')

    if (payload.aud !== projectId) throw new AuthError('progetto sbagliato (aud)')
    if (payload.iss !== `https://securetoken.google.com/${projectId}`) throw new AuthError('emittente sbagliato (iss)')
    if (typeof payload.sub !== 'string' || payload.sub.length === 0 || payload.sub.length > 128) {
        throw new AuthError('uid non valido')
    }
    if (!(payload.exp > now)) throw new AuthError('token scaduto')
    if (!(payload.iat <= now + CLOCK_SKEW_SECONDS)) throw new AuthError('token emesso nel futuro')
    if (!(payload.auth_time <= now + CLOCK_SKEW_SECONDS)) throw new AuthError('accesso nel futuro')
    return { uid: payload.sub }
}
