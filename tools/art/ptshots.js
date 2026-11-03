const sq = 0.058;
const at = (file, rank) => ({ x: (file - 3.5) * sq, z: (3.5 - rank) * sq });
const tile = { w: 1200, h: 844, samples: 240 };
const lamp = { x: -0.42, y: 0.36, z: 0.2, w: 0.34, h: 0.34, power: 9, color: '#ffe6cc' };
const strip = (color, power = 26) => ({ x: 0.42, y: 0.42, z: -0.5, w: 0.1, h: 0.5, power, color });
// Night: a black sweep, a soft warm lamp, a thin strip behind in the topic's colour. Black blends into any dark theme.
const night = (edge = '#ffcf9e', extra = {}) => ({ floor: '#040404', floorRough: 0.42, floorCoat: 0.12, env: 0.01, exposure: 1, lights: [lamp, strip(edge)], ...extra });
// Day: a white sweep and a huge soft window. White blends into any light theme.
const day = (extra = {}) => ({
    floor: '#ffffff', floorRough: 0.5, floorCoat: 0.25, bg: '#ffffff', env: 0.3, exposure: 0.95,
    lights: [{ x: -0.3, y: 0.7, z: 0.3, w: 1, h: 1, power: 2.3, color: '#fff7ee' }, { x: 0.5, y: 0.3, z: 0.3, w: 0.6, h: 0.6, power: 0.7, color: '#ffffff' }], ...extra,
});
const swapped = (pieces) => pieces.map((p) => (p.mat ? p : { ...p, c: p.c === 'black' ? 'white' : 'black' }));
/** A scene twice: white pieces forward at night, black pieces forward by day, for contrast. */
const both = (name, spec, edge, nightExtra = {}, dayExtra = {}) => [
    { ...tile, ...spec, name: name + '_night', ...night(edge, nightExtra) },
    { ...tile, ...spec, name: name + '_day', pieces: swapped(spec.pieces), ...day(dayExtra) },
];
const family = [
    { p: 'pawn', x: -0.135, z: 0.02 }, { p: 'knight', x: -0.08, z: 0, ry: 2.0 }, { p: 'bishop', x: -0.025, z: 0.015 },
    { p: 'rook', x: 0.03, z: -0.005 }, { p: 'queen', x: 0.088, z: 0.012 }, { p: 'king', x: 0.148, z: -0.002 },
];
const position = [
    { p: 'king', ...at(6, 0) }, { p: 'rook', ...at(5, 0) }, { p: 'queen', ...at(3, 1) }, { p: 'knight', ...at(5, 2), ry: 1.4 }, { p: 'bishop', ...at(2, 3) },
    { p: 'pawn', ...at(0, 1) }, { p: 'pawn', ...at(1, 1) }, { p: 'pawn', ...at(4, 3) }, { p: 'pawn', ...at(6, 1) }, { p: 'pawn', ...at(7, 2) },
    { p: 'king', c: 'black', ...at(6, 7) }, { p: 'rook', c: 'black', ...at(3, 7) }, { p: 'queen', c: 'black', ...at(2, 5) }, { p: 'knight', c: 'black', ...at(5, 5), ry: -1.4 }, { p: 'bishop', c: 'black', ...at(4, 6) },
    { p: 'pawn', c: 'black', ...at(0, 6) }, { p: 'pawn', c: 'black', ...at(1, 5) }, { p: 'pawn', c: 'black', ...at(3, 4) }, { p: 'pawn', c: 'black', ...at(6, 6) }, { p: 'pawn', c: 'black', ...at(7, 5) },
];
const dim = { exposure: 0.62 };
const pool = (tx, tz) => ({ lights: [{ spot: true, x: tx - 0.12, y: 0.55, z: tz + 0.2, tx, ty: 0, tz, power: 2.2, angle: 0.3, color: '#ffe6c8' }, strip('#ffcf9e', 12)] });
export const SHOTS = [
    ...both('king', { w: 1200, h: 1200, samples: 240, pieces: [{ p: 'king', mat: 'gold' }], cam: { x: 0.04, y: 0.07, z: 0.4, tx: 0, ty: 0.052, tz: 0, fov: 19, fStop: 16 } }, '#ffcf9e'),
    ...both('basics', { pieces: family, cam: { x: 0.03, y: 0.1, z: 0.78, tx: 0.01, ty: 0.05, tz: 0, fov: 19, fStop: 18 } }, '#ffb764'),
    ...both('openings', {
        pieces: [{ p: 'knight', x: 0.03, z: 0.02, ry: 2.05 }, { p: 'pawn', c: 'black', x: -0.075, z: -0.07 }, { p: 'pawn', c: 'black', x: 0.12, z: -0.08 }],
        cam: { x: -0.05, y: 0.08, z: 0.46, tx: 0.015, ty: 0.042, tz: 0, fov: 20, fStop: 11 },
    }, '#35d9a0'),
    ...both('tactics', {
        pieces: [{ p: 'knight', x: 0.0, z: 0.04, ry: 2.3 }, { p: 'queen', c: 'black', x: 0.1, z: -0.06 }, { p: 'rook', c: 'black', x: -0.1, z: -0.07 }],
        cam: { x: 0.0, y: 0.1, z: 0.52, tx: 0.0, ty: 0.044, tz: 0, fov: 20, fStop: 12 },
    }, '#9a6bff'),
    ...both('middlegame', {
        pieces: [{ p: 'rook', x: 0.05, z: -0.02 }, { p: 'pawn', x: -0.02, z: 0.04 }, { p: 'pawn', x: -0.08, z: -0.01 }, { p: 'bishop', c: 'black', x: 0.13, z: -0.13 }],
        cam: { x: -0.1, y: 0.09, z: 0.52, tx: 0.015, ty: 0.045, tz: 0, fov: 19, fStop: 12 },
    }, '#58a8ff'),
    ...both('endgames', {
        pieces: [{ p: 'king', c: 'black', x: -0.03, z: -0.02 }, { p: 'pawn', x: 0.06, z: 0.03 }],
        cam: { x: 0.02, y: 0.055, z: 0.46, tx: 0.012, ty: 0.05, tz: 0, fov: 19, fStop: 12 },
    }, '#ff4d5e'),
    ...both('blunders', {
        board: true,
        pieces: [{ p: 'queen', x: 0.02, y: 0.0205, z: 0.03, rz: -1.5, ry: 3.7 }, { p: 'pawn', c: 'black', ...at(2, 5) }],
        cam: { x: -0.04, y: 0.12, z: 0.42, tx: 0.015, ty: 0.03, tz: 0, fov: 20, fStop: 11 },
    }, '#ff7a2e', dim, { exposure: 0.8 }),
    ...both('battle', {
        board: true,
        pieces: [{ p: 'knight', x: -0.046, z: 0.0, ry: 2.05 }, { p: 'knight', c: 'black', x: 0.046, z: 0.0, ry: -1.09 }, { p: 'pawn', ...at(1, 6) }, { p: 'pawn', c: 'black', ...at(6, 6) }],
        cam: { x: 0.0, y: 0.075, z: 0.5, tx: 0, ty: 0.04, tz: 0, fov: 19, fStop: 12 },
    }, '#ffcf9e', dim, { exposure: 0.8 }),
    ...both('explore', {
        board: true, pieces: [{ p: 'bishop', ...at(3, 2) }],
        cam: { x: 0.12, y: 0.17, z: 0.44, tx: -0.01, ty: 0.02, tz: 0.03, fov: 22, fStop: 16 },
    }, '#ffcf9e', { ...pool(-0.03, 0.09), exposure: 0.8 }, { exposure: 0.8 }),
    ...both('position', {
        board: true, pieces: position,
        cam: { x: 0.0, y: 0.58, z: 0.42, tx: 0, ty: 0.0, tz: 0.0, fov: 24, fStop: 32 },
    }, '#ffcf9e', dim, { exposure: 0.8 }),
];

// The earned pieces: the camera moves in on the shorter ones so each fills the frame the same way.
const ranks = [['pawn', 'bronze', 0.054, 0.3], ['knight', 'bronze', 0.075, 2.05], ['bishop', 'silver', 0.086, 0.3], ['rook', 'silver', 0.061, 0.3], ['queen', 'gold', 0.091, 0.3], ['king', 'gold', 0.095, 0.3]];
for (const [p, mat, tall, ry] of ranks) {
    const z = 0.4 * (tall + 0.018) / 0.113;
    const cam = { x: 0.04 * z / 0.4, y: tall * 0.74, z, tx: 0, ty: tall * 0.55, tz: 0, fov: 19, fStop: 16 };
    const [nightShot, dayShot] = both('rank_' + p, { w: 1200, h: 1200, samples: 80, pieces: [{ p, mat, ry }], cam }, '#ffcf9e');
    if (mat === 'silver') dayShot.pieces = [{ p, mat: 'pewter', ry }];
    SHOTS.push(nightShot, dayShot);
}

// By day the king stays black, or it would vanish into the white page.
SHOTS.find((s) => s.name === 'endgames_day').pieces = [{ p: 'king', c: 'black', x: -0.03, z: -0.02 }, { p: 'pawn', x: 0.06, z: 0.03 }];
