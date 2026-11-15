// Play a friend (Phase 13): a white and a black knight face to face on the board, as two friends
// across a game. Same lights and two renders as trainshots.js; the pieces keep their colours by day.
const sq = 0.058;
const at = (file, rank) => ({ x: (file - 3.5) * sq, z: (3.5 - rank) * sq });
const tile = { w: 1200, h: 844, samples: 240 };
const lamp = { x: -0.42, y: 0.36, z: 0.2, w: 0.34, h: 0.34, power: 9, color: '#ffe6cc' };
const strip = (color, power = 26) => ({ x: 0.42, y: 0.42, z: -0.5, w: 0.1, h: 0.5, power, color });
const night = (edge) => ({ floor: '#040404', floorRough: 0.42, floorCoat: 0.12, env: 0.01, exposure: 0.8, lights: [lamp, strip(edge)] });
const day = () => ({
    floor: '#ffffff', floorRough: 0.5, floorCoat: 0.25, bg: '#ffffff', env: 0.3, exposure: 0.8,
    lights: [{ x: -0.3, y: 0.7, z: 0.3, w: 1, h: 1, power: 2.3, color: '#fff7ee' }, { x: 0.5, y: 0.3, z: 0.3, w: 0.6, h: 0.6, power: 0.7, color: '#ffffff' }],
});
const both = (name, spec, edge) => [
    { ...tile, ...spec, name: name + '_night', ...night(edge) },
    { ...tile, ...spec, name: name + '_day', ...day() },
];
const b = (p, file, rank, extra = {}) => ({ p, c: 'black', ...at(file, rank), ...extra });
const w = (p, file, rank, extra = {}) => ({ p, ...at(file, rank), ...extra });

// The knights on d4 and e4, turned towards each other (black pieces are modelled the other way round,
// so the same turn faces the other way); a pawn of each colour behind them.
const KNIGHT_TURN = Math.PI / 2;
export const SHOTS = [
    ...both('friend', {
        board: true,
        pieces: [w('knight', 3, 3, { ry: KNIGHT_TURN }), b('knight', 4, 3, { ry: KNIGHT_TURN }), w('pawn', 2, 2), b('pawn', 5, 5)],
        cam: { x: 0.02, y: 0.06, z: 0.3, tx: 0.0, ty: 0.035, tz: 0.0, fov: 26, fStop: 9 },
    }, '#7ee0b0'),
];
