// Proxy fra AnimeWatch e OpenRouter: la chiave OpenRouter è un segreto del Worker e non arriva mai all'app.
// Accetta solo utenti autenticati dell'app (ID token di Firebase), con un limite di richieste per utente;
// modello e lunghezza massima della risposta li decide il Worker, non il client.

import { AuthError, verifyFirebaseToken } from './firebase-auth.js'

const OPENROUTER_URL = 'https://openrouter.ai/api/v1/chat/completions'
const MAX_BODY_BYTES = 64 * 1024
const MAX_MESSAGES = 10
const ROLES = new Set(['system', 'user', 'assistant'])

function error(status, message) {
    // Stessa forma degli errori di OpenRouter/OpenAI.
    return Response.json({ error: { message } }, { status })
}

function clamp(value, min, max, fallback) {
    return Number.isFinite(value) ? Math.min(max, Math.max(min, value)) : fallback
}

export default {
    async fetch(request, env) {
        const url = new URL(request.url)
        if (url.pathname !== '/chat/completions') return error(404, 'not found')
        if (request.method !== 'POST') return error(405, 'method not allowed')
        if (!env.OPENROUTER_API_KEY) return error(500, 'proxy non configurato')

        const authorization = request.headers.get('Authorization') ?? ''
        let user
        try {
            user = await verifyFirebaseToken(authorization.replace(/^Bearer\s+/i, ''), env.FIREBASE_PROJECT_ID)
        } catch (e) {
            if (e instanceof AuthError) {
                console.warn(`401: ${e.message}`)
                return error(401, 'unauthorized')
            }
            throw e
        }

        // Il limite è per uid: un utente che insiste non consuma il credito di tutti.
        if (env.PER_USER) {
            const { success } = await env.PER_USER.limit({ key: user.uid })
            if (!success) return error(429, 'too many requests')
        }

        const text = await request.text()
        if (new TextEncoder().encode(text).length > MAX_BODY_BYTES) return error(413, 'request too large')
        let body
        try {
            body = JSON.parse(text)
        } catch {
            return error(400, 'invalid json')
        }
        const messages = Array.isArray(body?.messages) ? body.messages : []
        if (messages.length === 0 || messages.length > MAX_MESSAGES) return error(400, 'invalid messages')
        const clean = messages.map((m) => ({ role: String(m?.role), content: String(m?.content ?? '') }))
        if (clean.some((m) => !ROLES.has(m.role))) return error(400, 'invalid role')

        const upstream = await fetch(OPENROUTER_URL, {
            method: 'POST',
            headers: {
                Authorization: `Bearer ${env.OPENROUTER_API_KEY}`,
                'Content-Type': 'application/json',
                'X-Title': 'AnimeWatch',
            },
            body: JSON.stringify({
                model: env.OPENROUTER_MODEL,
                messages: clean,
                temperature: clamp(Number(body.temperature), 0, 1.5, 0.3),
                max_tokens: Number(env.MAX_TOKENS) || 1500,
            }),
        })
        if (!upstream.ok) console.warn(`OpenRouter: HTTP ${upstream.status}`)
        return new Response(upstream.body, {
            status: upstream.status,
            headers: { 'Content-Type': upstream.headers.get('Content-Type') ?? 'application/json' },
        })
    },
}
