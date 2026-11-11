// Percorsi TMDB del proxy: la chiave (segreto TMDB_API_KEY) resta qui. Non si inoltrano richieste qualsiasi: due
// percorsi fissi, e le risposte ridotte ai campi che l'app usa (con cast e troupe una serie supera i 400 KB).

const TMDB_BASE = 'https://api.themoviedb.org/3/'
const LANGUAGE = 'it-IT'
// TMDB accetta al massimo 20 voci in append_to_response; le stagioni che non esistono le ignora.
const SEASONS_PER_CALL = 20
const MAX_SEASONS = 60

async function tmdb(env, path, params) {
    const url = new URL(path, TMDB_BASE)
    url.searchParams.set('api_key', env.TMDB_API_KEY)
    url.searchParams.set('language', LANGUAGE)
    for (const [k, v] of Object.entries(params ?? {})) url.searchParams.set(k, String(v))
    const response = await fetch(url)
    const json = await response.json().catch(() => null)
    if (!response.ok) {
        // Il motivo resta nei log; all'app basta sapere che non c'è (404) o che TMDB non ha risposto (502).
        console.warn(`TMDB ${path}: HTTP ${response.status} ${json?.status_message ?? ''}`)
        throw new TmdbError(response.status === 404 ? 404 : 502)
    }
    return json
}

export class TmdbError extends Error {
    constructor(status) {
        super(`TMDB ${status}`)
        this.status = status
    }
}

/** Risultati di una ricerca di serie, compatti. */
export function compactSearch(json) {
    return {
        results: (json?.results ?? []).slice(0, 10).map((r) => ({
            id: r.id,
            name: r.name ?? null,
            originalName: r.original_name ?? null,
            firstAirDate: r.first_air_date || null,
            genreIds: r.genre_ids ?? [],
            originCountry: r.origin_country ?? [],
        })),
    }
}

/** Scheda di una serie con le puntate di tutte le stagioni (anche la 0, gli speciali), compatta. */
export function compactShow(parts) {
    const show = parts[0]
    const seasons = []
    for (const part of parts) {
        for (const [key, season] of Object.entries(part)) {
            if (!key.startsWith('season/') || !season || !Array.isArray(season.episodes)) continue
            seasons.push({
                number: season.season_number,
                overview: season.overview || null,
                episodes: season.episodes.map((e) => ({
                    season: e.season_number,
                    number: e.episode_number,
                    airDate: e.air_date || null,
                    name: e.name || null,
                    runtime: e.runtime ?? null,
                    still: e.still_path ?? null,
                    vote: e.vote_count > 0 ? e.vote_average : null,
                })),
            })
        }
    }
    seasons.sort((a, b) => a.number - b.number)
    return {
        id: show.id,
        name: show.name ?? null,
        overview: show.overview || null,
        firstAirDate: show.first_air_date || null,
        seasons,
    }
}

function seasonList(from, to) {
    const list = []
    for (let n = from; n < to; n++) list.push(`season/${n}`)
    return list.join(',')
}

export async function search(env, url) {
    const query = (url.searchParams.get('query') ?? '').trim().slice(0, 200)
    if (!query) return Response.json({ error: { message: 'missing query' } }, { status: 400 })
    const year = Number(url.searchParams.get('year'))
    const params = { query, include_adult: 'false' }
    if (Number.isInteger(year) && year > 1900 && year < 3000) params.first_air_date_year = year
    return Response.json(compactSearch(await tmdb(env, 'search/tv', params)))
}

export async function show(env, id) {
    const parts = [await tmdb(env, `tv/${id}`, { append_to_response: seasonList(0, SEASONS_PER_CALL) })]
    // Le serie lunghe (One Piece) hanno più di 19 stagioni: le altre in una seconda chiamata, e così via.
    const count = Number(parts[0].number_of_seasons) || 0
    for (let from = SEASONS_PER_CALL; from <= count && from < MAX_SEASONS; from += SEASONS_PER_CALL) {
        parts.push(await tmdb(env, `tv/${id}`, { append_to_response: seasonList(from, from + SEASONS_PER_CALL) }))
    }
    return Response.json(compactShow(parts))
}
