// Serves the art folder on localhost, opens a page in headless Edge, saves what the page posts back.
// usage: node render.js <page and query> [window size, e.g. 1080,1080]
const http = require('http');
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');

const root = __dirname;
const out = path.join(root, 'out');
fs.mkdirSync(out, { recursive: true });
const page = process.argv[2];
const size = process.argv[3] || '1080,1080';
const types = { '.html': 'text/html', '.js': 'text/javascript', '.tza': 'application/octet-stream', '.gltf': 'model/gltf+json', '.jpg': 'image/jpeg', '.png': 'image/png', '.webp': 'image/webp' };
let edge;

const finish = (code) => { if (edge) edge.kill(); setTimeout(() => process.exit(code), 500); };
const watchdog = setTimeout(() => { console.log('TIMEOUT'); finish(1); }, 10000000);

const server = http.createServer((req, res) => {
    const [route, query] = req.url.split('?');
    if (req.method === 'POST') {
        const chunks = [];
        req.on('data', (c) => chunks.push(c));
        req.on('end', () => {
            const body = Buffer.concat(chunks);
            if (route === '/log') console.log(body.toString());
            if (route === '/save') {
                const name = path.basename(new URLSearchParams(query).get('name'));
                fs.writeFileSync(path.join(out, name), Buffer.from(body.toString().split(',')[1], 'base64'));
                console.log('saved', name);
            }
            res.writeHead(200);
            res.end();
            if (route === '/done') { clearTimeout(watchdog); finish(0); }
        });
        return;
    }
    const file = path.join(root, decodeURIComponent(route));
    if (!file.startsWith(root)) { res.writeHead(403); return res.end(); }
    fs.readFile(file, (err, data) => {
        if (err) { res.writeHead(404); return res.end(); }
        res.writeHead(200, { 'Content-Type': types[path.extname(file)] || 'application/octet-stream' });
        res.end(data);
    });
});

server.listen(8931, '127.0.0.1', () => {
    edge = spawn('C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe', [
        '--headless=new', '--no-first-run', '--no-default-browser-check', '--disable-extensions',
        ...(process.env.GPU ? ['--use-angle=d3d11', '--ignore-gpu-blocklist', '--enable-gpu-rasterization', '--enable-unsafe-webgpu', '--enable-features=Vulkan,WebGPU'] : ['--enable-unsafe-swiftshader', '--use-angle=swiftshader', '--ignore-gpu-blocklist']),
        `--user-data-dir=${path.join(root, 'edge-profile')}`, `--window-size=${size}`,
        `http://127.0.0.1:8931/${page}`,
    ], { stdio: 'ignore' });
});
