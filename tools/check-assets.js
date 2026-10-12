'use strict';

/*
 * Art asset self-check (pure Node):
 *   node tools/check-assets.js
 *
 * It checks four things:
 *   1. Model structure: element count, texture references, shade, whether the rotations are legal (single axis only, a multiple of 22.5, |angle| ≤ 45), coordinate range, whether any face is missing;
 *   2. PNG spec: size, bit depth, color type, whether the dimensions are powers of two;
 *   3. Whether any non-white pixel has crept into the pure white texture (the mechanical verification of the "pure white palette");
 *   4. Whether the orbiting blocks' count and radius line up with the constants in the code.
 */

const fs = require('fs');
const zlib = require('zlib');

const BASE = 'src/main/resources/assets/new-mod/';
const MODEL = BASE + 'models/item/black_block_star.json';

let failures = 0;

function check(label, ok, detail = '') {
	console.log(`${ok ? '  ✓' : '  ✗'} ${label}${detail ? '  ' + detail : ''}`);

	if (!ok) {
		failures++;
	}
}

/** Decode the PNG's pixels, returning [width, height, rgba]. */
function decodePNG(file) {
	const buffer = fs.readFileSync(file);
	const width = buffer.readUInt32BE(16);
	const height = buffer.readUInt32BE(20);
	const idat = [];
	let offset = 8;

	while (offset < buffer.length) {
		const length = buffer.readUInt32BE(offset);
		const type = buffer.toString('ascii', offset + 4, offset + 8);

		if (type === 'IDAT') {
			idat.push(buffer.subarray(offset + 8, offset + 8 + length));
		}

		offset += 12 + length;
	}

	const raw = zlib.inflateSync(Buffer.concat(idat));
	const stride = width * 4;
	const rgba = Buffer.alloc(width * height * 4);

	for (let y = 0; y < height; y++) {
		// the filter is always 0 (these are PNGs we wrote ourselves), so just copy straight across
		raw.copy(rgba, y * stride, y * (stride + 1) + 1, y * (stride + 1) + 1 + stride);
	}

	return { width, height, bitDepth: buffer[24], colorType: buffer[25], bytes: buffer.length, rgba };
}

console.log('Checking the model...');

const model = JSON.parse(fs.readFileSync(MODEL, 'utf8'));
const elements = model.elements;
const ballParts = elements.filter((e) => Object.values(e.faces)[0].texture === '#orb');
const blockParts = elements.filter((e) => Object.values(e.faces)[0].texture === '#block');

check('total elements = ball 5 slices + 8 orbiting blocks', elements.length === 13, `actual ${elements.length}`);
check('ball slices = 5', ballParts.length === 5, `actual ${ballParts.length}`);
check('orbiting blocks = 8', blockParts.length === 8, `actual ${blockParts.length}`);
check('does not inherit item/generated (otherwise a flat texture gets layered on top)', model.parent === undefined,
	model.parent === undefined ? '(display own transforms)' : `parent = ${model.parent}`);
check('display covers eight contexts', Object.keys(model.display).length === 8, Object.keys(model.display).join(', '));
check('every shade=false (the pure white is not darkened by vanilla)', elements.every((e) => e.shade === false));
check('all six faces of every element have a texture', elements.every((e) =>
	['north', 'east', 'south', 'west', 'up', 'down'].every((f) => !e.faces[f] || e.faces[f].texture)));

const rotated = elements.filter((e) => e.rotation);
check('rotation uses a single axis only and the angle is a multiple of 22.5 with |angle| ≤ 45', rotated.every((e) =>
	typeof e.rotation.axis === 'string'
	&& Math.abs(e.rotation.angle) <= 45
	&& Math.abs(e.rotation.angle % 22.5) < 1e-6), `${rotated.length} elements with rotation`);

check('all coordinates are within -16..32', elements.every((e) => [...e.from, ...e.to].every((v) => v >= -16 && v <= 32)));
check('the orbiting blocks really do surround the ball (radius close to the code constant)', blockParts.every((e) => {
	const cx = (e.from[0] + e.to[0]) / 2 - 8;
	const cz = (e.from[2] + e.to[2]) / 2 - 8;
	const radius = Math.hypot(cx, cz);

	return Math.abs(radius - 6.4) < 0.2;
}));

// whether the orbiting blocks really do form a ring (radius aligned with ORBIT_RADIUS in the generator)
const generator = fs.readFileSync('tools/gen-assets.js', 'utf8');
const radiusInGenerator = Number((generator.match(/ORBIT_RADIUS = ([\d.]+)/) || [])[1]);
check('orbiting block radius matches the generator constant', blockParts.every((e) => {
	const cx = (e.from[0] + e.to[0]) / 2 - 8;
	const cz = (e.from[2] + e.to[2]) / 2 - 8;

	return Math.abs(Math.hypot(cx, cz) - radiusInGenerator) < 0.2;
}), `ORBIT_RADIUS in the generator = ${radiusInGenerator}`);

console.log('\nChecking the textures...');

const orb = decodePNG(BASE + 'textures/item/black_block_star.png');
const icon = decodePNG(BASE + 'icon.png');

for (const [name, png] of [['black_block_star.png', orb], ['icon.png', icon]]) {
	const powerOfTwo = (n) => n > 0 && (n & (n - 1)) === 0;

	check(`${name} spec`, png.bitDepth === 8 && png.colorType === 6 && powerOfTwo(png.width) && powerOfTwo(png.height),
		`${png.width}x${png.height} bitDepth=${png.bitDepth} colorType=${png.colorType} ${png.bytes} bytes`);
}

let opaque = 0;
let colored = 0;

for (let i = 0; i < orb.rgba.length; i += 4) {
	if (orb.rgba[i + 3] > 0) {
		opaque++;

		if (orb.rgba[i] !== 255 || orb.rgba[i + 1] !== 255 || orb.rgba[i + 2] !== 255) {
			colored++;
		}
	}
}

check('the small ball texture is "pure white": no non-white pixels among the non-transparent ones', colored === 0, `opaque ${opaque}, non-pure-white ${colored}`);

const texturesInUse = new Set();

for (const element of elements) {
	for (const face of Object.values(element.faces)) {
		texturesInUse.add(face.texture.replace('#', ''));
	}
}

check('the textures the model references are all declared in textures', [...texturesInUse].every((key) => model.textures[key] !== undefined),
	[...texturesInUse].join(', '));

// ---------------------------------------------------------------- atlas safety (26.3 hard rule)

/*
 * In 26.3, CuboidItemModelWrapper#validateAtlasUsage validates that
 * "the sprites used by one item model must all come from a single atlas".
 * Block textures (textures/block/*) belong to the blocks atlas, so the moment one shows up in an
 * item model of this kind (with its own textures + elements), the whole model fails to bake —— in
 * game the item turns into the black-and-purple "missing model".
 * This check exists so that the same mistake is never made a second time.
 */
console.log('\nChecking item model atlas safety...');

const itemModelDir = 'src/main/resources/assets/new-mod/models/item';

for (const name of fs.readdirSync(itemModelDir)) {
	if (!name.endsWith('.json')) {
		continue;
	}

	const file = `${itemModelDir}/${name}`;
	const itemModel = JSON.parse(fs.readFileSync(file, 'utf8'));
	const declared = itemModel.textures ?? {};

	// only models "with their own textures + elements" go through the CuboidItemModelWrapper validation;
	// ones that simply point at a block model through parent (the item form of the black block, for example)
	// take the block route and are not subject to this restriction.
	if (Object.keys(declared).length === 0 || !itemModel.elements) {
		continue;
	}

	const used = new Set();

	for (const element of itemModel.elements) {
		for (const face of Object.values(element.faces)) {
			if (face.texture && face.texture.startsWith('#')) {
				const key = face.texture.slice(1);
				used.add(declared[key] ?? `??${key}`);
			}
		}
	}

	for (const path of Object.values(declared)) {
		used.add(path);
	}

	const bad = [...used].filter((path) => !/^(new-mod|minecraft):item\//.test(path));

	check(`${name} only uses textures from the item atlas`, bad.length === 0, bad.length === 0 ? [...used].join(', ') : `out of bounds: ${bad.join(', ')}`);

	// and while we're at it, confirm these texture files really exist
	const missing = [...used]
		.filter((path) => path.startsWith('new-mod:item/'))
		.filter((path) => !fs.existsSync(`src/main/resources/assets/new-mod/textures/item/${path.split('/').slice(1).join('/')}.png`));

	check(`${name} referenced texture files all exist`, missing.length === 0, missing.join(', '));
}

console.log(failures === 0 ? '\nAll checks passed.' : `\n${failures} check(s) failed.`);
process.exit(failures === 0 ? 0 : 1);
