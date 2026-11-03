const gold = '#e7a93a', silver = '#d7dbe0', bronze = '#b8723f', steel = '#9aa6b4';
const violet = '#6a3ff0', blue = '#2f7bf0', teal = '#12b5a3', green = '#1fae6a', red = '#e23a4e', ember = '#f2701f';
const list = {
    palette: violet, grid_on: gold, person: blue, image: teal, volume_up: ember, link: teal, cloud_sync: teal, memory: blue,
    storage: gold, info: steel, flag: green, extension: violet, star: gold, emoji_events: gold, refresh: blue, warning: red,
    delete_forever: red, bug_report: red, favorite: red, download: blue, code: steel, description: steel, analytics: blue,
    tune: steel, history: ember, map: green, check_circle: green, sports_esports: violet, school: green, bolt: gold,
    psychology: blue, auto_stories: red, shield: ember,
};
const medals = { cup: 'emoji_events', puzzle: 'extension', cap: 'school', chart: 'analytics' };
export const ICONS = [
    ...Object.entries(list).map(([svg, color]) => ({ name: 'icon_' + svg, svg, color })),
    ...Object.entries(medals).flatMap(([name, svg]) => [['gold', gold], ['silver', silver], ['bronze', bronze]].map(([metal, color]) => ({ name: `medal_${name}_${metal}`, svg, color }))),
];
