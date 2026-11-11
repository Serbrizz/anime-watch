// Proxy fra AnimeWatch e Gemini (Google AI Studio, piano gratuito): la chiave è un segreto del Worker e non arriva mai
// all'app. Accetta solo utenti autenticati dell'app (ID token di Firebase), con un limite di richieste per utente;
// modello e lunghezza massima della risposta li decide il Worker, non il client.
// Verso l'app parla il formato chat/completions di OpenAI (quello che l'app conosce) e lo traduce per Gemini.

import { AuthError, verifyFirebaseToken } from './firebase-auth.js'

const GEMINI_BASE = 'https://generativelanguage.googleapis.com/v1beta/models/'
const MAX_BODY_BYTES = 64 * 1024
const MAX_MESSAGES = 10
const ROLES = new Set(['system', 'user', 'assistant'])

function error(status, message) {
    // Stessa forma degli errori di OpenAI.
    return Response.json({ error: { message } }, { status })
}

// Modelli in ordine di preferenza: le quote gratuite sono per modello, quindi se il primo è sovraccarico (503) o ha
// finito la quota (429) il secondo di solito risponde.
function models(env) {
    return String(env.GEMINI_MODELS ?? '').split(',').map((m) => m.trim()).filter(Boolean)
}

const RETRY_WITH_NEXT = new Set([429, 500, 503])

function clamp(value, min, max, fallback) {
    return Number.isFinite(value) ? Math.min(max, Math.max(min, value)) : fallback
}

const ROUTES = { '/chat/completions': 'POST', '/status': 'GET' }

// Preflight dell'app: dice se la chiave è valida e il modello esiste, senza consumare la quota (è una lettura di
// metadati). Gemini non espone la quota residua: se è finita lo scopre la prima richiesta, che risponde 429.
async function status(env) {
    let available = false
    try {
        const upstream = await fetch(GEMINI_BASE + models(env)[0], {
            headers: { 'x-goog-api-key': env.GEMINI_API_KEY },
        })
        available = upstream.ok
        if (!upstream.ok) console.warn(`Gemini status: HTTP ${upstream.status} ${(await upstream.text()).slice(0, 300)}`)
    } catch (e) {
        console.warn(`Gemini status: ${e}`)
    }
    return Response.json({ available })
}

async function chat(env, body) {
    const messages = Array.isArray(body?.messages) ? body.messages : []
    if (messages.length === 0 || messages.length > MAX_MESSAGES) return error(400, 'invalid messages')
    const clean = messages.map((m) => ({ role: String(m?.role), content: String(m?.content ?? '') }))
    if (clean.some((m) => !ROLES.has(m.role))) return error(400, 'invalid role')

    const system = clean.filter((m) => m.role === 'system').map((m) => m.content).join('\n')
    const contents = clean
        .filter((m) => m.role !== 'system')
        .map((m) => ({ role: m.role === 'assistant' ? 'model' : 'user', parts: [{ text: m.content }] }))
    if (contents.length === 0) return error(400, 'invalid messages')
    // Schema della risposta in formato OpenAI (response_format json_schema): Gemini è obbligato a rispettarlo.
    // Senza, la modalità JSON garantisce solo la sintassi (un id è arrivato come "21827 | Mob Psycho 100").
    const schema = body?.response_format?.type === 'json_schema' ? body.response_format.json_schema?.schema : null

    const request = JSON.stringify({
        ...(system && { systemInstruction: { parts: [{ text: system }] } }),
        contents,
        generationConfig: {
            temperature: clamp(Number(body.temperature), 0, 1.5, 0.3),
            maxOutputTokens: Number(env.MAX_TOKENS) || 4000,
            // L'unico client chiede sempre JSON: in modalità JSON Gemini non può restituire testo malformato.
            responseMimeType: 'application/json',
            ...(schema && typeof schema === 'object' && { responseJsonSchema: schema }),
            // Scegliere da una lista chiusa non richiede ragionamento: poco pensiero = risposta in pochi secondi.
            thinkingConfig: { thinkingLevel: env.GEMINI_THINKING || 'low' },
        },
    })

    let upstream
    let json
    let model
    for (model of models(env)) {
        upstream = await fetch(`${GEMINI_BASE}${model}:generateContent`, {
            method: 'POST',
            headers: { 'x-goog-api-key': env.GEMINI_API_KEY, 'Content-Type': 'application/json' },
            body: request,
        })
        json = await upstream.json().catch(() => null)
        if (upstream.ok) break
        // 429 = quota gratuita finita (si azzera ogni giorno), 503 = modello sovraccarico: il motivo sta nel messaggio.
        console.warn(`Gemini ${model}: HTTP ${upstream.status} ${(json?.error?.message ?? '').slice(0, 300)}`)
        if (!RETRY_WITH_NEXT.has(upstream.status)) break
    }
    if (!upstream.ok) return error(upstream.status, json?.error?.message ?? `HTTP ${upstream.status}`)

    const candidate = json?.candidates?.[0]
    // Le parti `thought` sono il ragionamento, non la risposta.
    const content = (candidate?.content?.parts ?? []).filter((p) => !p.thought).map((p) => p.text ?? '').join('')
    const usage = json?.usageMetadata
    console.log(
        `Gemini: ${json?.modelVersion ?? model} finish=${candidate?.finishReason} ` +
            `content=${content.length} usage=${JSON.stringify(usage)}`,
    )
    if (!content) return error(502, `risposta vuota (${candidate?.finishReason ?? json?.promptFeedback?.blockReason})`)

    return Response.json({
        model: json?.modelVersion ?? model,
        choices: [
            {
                index: 0,
                message: { role: 'assistant', content },
                finish_reason: String(candidate?.finishReason ?? 'STOP').toLowerCase(),
            },
        ],
        usage: {
            prompt_tokens: usage?.promptTokenCount,
            completion_tokens: usage?.candidatesTokenCount,
            total_tokens: usage?.totalTokenCount,
        },
    })
}

export default {
    async fetch(request, env) {
        const url = new URL(request.url)
        const method = ROUTES[url.pathname]
        if (!method) return error(404, 'not found')
        if (request.method !== method) return error(405, 'method not allowed')
        if (!env.GEMINI_API_KEY || models(env).length === 0) return error(500, 'proxy non configurato')

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

        // Il limite è per uid: un utente che insiste non consuma la quota gratuita di tutti.
        if (env.PER_USER) {
            const { success } = await env.PER_USER.limit({ key: user.uid })
            if (!success) return error(429, 'too many requests')
        }

        if (url.pathname === '/status') return status(env)

        const text = await request.text()
        if (new TextEncoder().encode(text).length > MAX_BODY_BYTES) return error(413, 'request too large')
        let body
        try {
            body = JSON.parse(text)
        } catch {
            return error(400, 'invalid json')
        }
        return chat(env, body)
    },
}
