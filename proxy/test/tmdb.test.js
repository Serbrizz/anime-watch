import assert from 'node:assert/strict'
import { test } from 'node:test'
import { compactSearch, compactShow } from '../src/tmdb.js'

test('la scheda tiene solo i campi usati, con tutte le stagioni in ordine', () => {
    const episode = (season, number, extra = {}) => ({
        season_number: season,
        episode_number: number,
        air_date: '2023-09-29',
        name: `Puntata ${number}`,
        runtime: 25,
        still_path: `/s${season}e${number}.jpg`,
        vote_average: 8.5,
        vote_count: 10,
        overview: 'trama lunga',
        crew: [{ name: 'regista' }],
        guest_stars: [{ name: 'ospite' }],
        ...extra,
    })
    const first = {
        id: 209867,
        name: 'Frieren',
        overview: 'Trama',
        first_air_date: '2023-09-29',
        credits: { cast: [] },
        'season/1': { season_number: 1, overview: '', episodes: [episode(1, 1), episode(1, 2, { vote_count: 0 })] },
        'season/0': { season_number: 0, overview: 'Speciali', episodes: [episode(0, 1, { air_date: '' })] },
    }
    const second = { id: 209867, 'season/20': { season_number: 20, episodes: [] } }

    const show = compactShow([first, second])
    assert.deepEqual(show.seasons.map((s) => s.number), [0, 1, 20])
    assert.equal(show.seasons[0].overview, 'Speciali')
    assert.equal(show.seasons[1].overview, null)
    assert.deepEqual(show.seasons[1].episodes[0], {
        season: 1, number: 1, airDate: '2023-09-29', name: 'Puntata 1', runtime: 25, still: '/s1e1.jpg', vote: 8.5,
    })
    assert.equal(show.seasons[1].episodes[1].vote, null, 'senza voti il voto medio non vale')
    assert.equal(show.seasons[0].episodes[0].airDate, null)
    assert.equal(show.overview, 'Trama')
    assert.equal(show.credits, undefined)
})

test('la ricerca restituisce al massimo 10 risultati compatti', () => {
    const results = Array.from({ length: 15 }, (_, i) => ({
        id: i, name: `N${i}`, original_name: `O${i}`, first_air_date: '', genre_ids: [16], origin_country: ['JP'],
        overview: 'x', popularity: 1,
    }))
    const compact = compactSearch({ results })
    assert.equal(compact.results.length, 10)
    assert.deepEqual(compact.results[0], {
        id: 0, name: 'N0', originalName: 'O0', firstAirDate: null, genreIds: [16], originCountry: ['JP'],
    })
})
