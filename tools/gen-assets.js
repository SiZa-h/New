'use strict';

/*
 * New Mod art asset generator (pure Node, no third-party libraries).
 *
 *   node tools/gen-assets.js                     generate the real assets
 *   node tools/gen-assets.js --preview <dir>     extra output: magnified texture preview + isometric model preview
 *
 * Output:
 *   assets/new-mod/textures/item/black_block_star.png  16x16  pure white glowing small ball (spherical highlight + very faint four-point star glint)
 *   assets/new-mod/models/item/black_block_star.json   3D model: one small ball + 8 orbiting micro black blocks
 *   assets/new-mod/icon.png                            128x128 mod icon (eldritch style)
 *
 * The PNG is hand-written: file signature + IHDR + IDAT(zlib) + IEND, each chunk carrying a CRC32 —— no image library needed.
 */

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

// ---------------------------------------------------------------- PNG encoding

const CRC_TABLE = (() => {
	const table = new Int32Array(256);

	for (let n = 0; n < 256; n++) {
		let c = n;

		for (let k = 0; k < 8; k++) {
			c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
		}

		table[n] = c;
	}

	return table;
})();

function crc32(buffer) {
	let c = 0xFFFFFFFF;

	for (let i = 0; i < buffer.length; i++) {
		c = CRC_TABLE[(c ^ buffer[i]) & 0xFF] ^ (c >>> 8);
	}

	return (c ^ 0xFFFFFFFF) >>> 0;
}

function chunk(type, data) {
	const length = Buffer.alloc(4);
	length.writeUInt32BE(data.length, 0);

	const typeBuffer = Buffer.from(type, 'ascii');
	const crc = Buffer.alloc(4);
	crc.writeUInt32BE(crc32(Buffer.concat([typeBuffer, data])), 0);

	return Buffer.concat([length, typeBuffer, data, crc]);
}

/** Write an RGBA buffer out as an 8-bit truecolor + alpha PNG. */
function writePNG(file, width, height, rgba) {
	const header = Buffer.alloc(13);
	header.writeUInt32BE(width, 0);
	header.writeUInt32BE(height, 4);
	header[8] = 8;      // bit depth
	header[9] = 6;      // color type: RGBA

	const stride = width * 4;
	const raw = Buffer.alloc((stride + 1) * height);

	for (let y = 0; y < height; y++) {
		raw[y * (stride + 1)] = 0;      // filter: 0 = None
		rgba.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride);
	}

	const png = Buffer.concat([
		Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]),
		chunk('IHDR', header),
		chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
		chunk('IEND', Buffer.alloc(0)),
	]);

	fs.mkdirSync(path.dirname(file), { recursive: true });
	fs.writeFileSync(file, png);
	console.log(`  ${path.relative(process.cwd(), file)}  ${width}x${height}  ${png.length} bytes`);
}

// ---------------------------------------------------------------- canvas

class Canvas {
	constructor(width, height) {
		this.width = width;
		this.height = height;
		this.data = Buffer.alloc(width * height * 4);
	}

	get(x, y) {
		const i = (y * this.width + x) * 4;
		return [this.data[i], this.data[i + 1], this.data[i + 2], this.data[i + 3]];
	}

	blend(x, y, r, g, b, a) {
		if (a <= 0 || x < 0 || y < 0 || x >= this.width || y >= this.height) {
			return;
		}

		const i = (y * this.width + x) * 4;
		const sa = Math.min(1, a);
		const da = this.data[i + 3] / 255;
		const outA = sa + da * (1 - sa);

		if (outA <= 0) {
			return;
		}

		const src = [r, g, b];

		for (let c = 0; c < 3; c++) {
			this.data[i + c] = Math.round((src[c] * sa + this.data[i + c] * da * (1 - sa)) / outA);
		}

		this.data[i + 3] = Math.round(outA * 255);
	}

	/** Generic rasterizer: intensity is decided by "the relative distance t from the center" and the angle, with 4x4 supersampling internally. */
	shade(cx, cy, radius, alpha, fn, r = 255, g = 255, b = 255) {
		const x0 = Math.floor(cx - radius) - 1;
		const x1 = Math.ceil(cx + radius) + 1;
		const y0 = Math.floor(cy - radius) - 1;
		const y1 = Math.ceil(cy + radius) + 1;

		for (let y = y0; y <= y1; y++) {
			for (let x = x0; x <= x1; x++) {
				let hits = 0;

				for (let sy = 0; sy < 4; sy++) {
					for (let sx = 0; sx < 4; sx++) {
						const px = x + (sx + 0.5) / 4 - cx;
						const py = y + (sy + 0.5) / 4 - cy;
						const t = Math.sqrt(px * px + py * py) / radius;
						const value = fn(t, Math.atan2(py, px), px, py);

						if (value > 0) {
							hits += Math.min(1, value);
						}
					}
				}

				if (hits > 0) {
					this.blend(x, y, r, g, b, alpha * hits / 16);
				}
			}
		}
	}

	glow(cx, cy, radius, alpha, power = 2.2, r = 255, g = 255, b = 255) {
		this.shade(cx, cy, radius, alpha, (t) => (t > 1 ? 0 : Math.pow(1 - t, power)), r, g, b);
	}

	star(cx, cy, outer, inner, points, sharpness, alpha, r = 255, g = 255, b = 255) {
		this.shade(cx, cy, outer, alpha, (t, angle) => {
			const wave = Math.pow(Math.abs(Math.cos(points * angle / 2)), sharpness);
			const boundary = (inner + (outer - inner) * wave) / outer;

			return t <= boundary ? 1 : 0;
		}, r, g, b);
	}

	line(x0, y0, x1, y1, width, alpha, r = 255, g = 255, b = 255) {
		const steps = Math.ceil(Math.hypot(x1 - x0, y1 - y0) * 3) + 1;

		for (let i = 0; i <= steps; i++) {
			const t = i / steps;
			this.glow(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, width, alpha, 1.0, r, g, b);
		}
	}

	rect(x0, y0, x1, y1, alpha, r = 255, g = 255, b = 255) {
		for (let y = Math.round(y0); y <= Math.round(y1); y++) {
			for (let x = Math.round(x0); x <= Math.round(x1); x++) {
				this.blend(x, y, r, g, b, alpha);
			}
		}
	}

	/** Scanline fill of a polygon (used by the isometric preview). */
	polygon(points, alpha, r = 255, g = 255, b = 255) {
		let minY = Infinity;
		let maxY = -Infinity;

		for (const [, y] of points) {
			minY = Math.min(minY, y);
			maxY = Math.max(maxY, y);
		}

		for (let y = Math.floor(minY); y <= Math.ceil(maxY); y++) {
			const crossings = [];

			for (let i = 0; i < points.length; i++) {
				const [x0, y0] = points[i];
				const [x1, y1] = points[(i + 1) % points.length];

				if ((y0 > y) !== (y1 > y)) {
					crossings.push(x0 + (x1 - x0) * (y - y0) / (y1 - y0));
				}
			}

			crossings.sort((a, b) => a - b);

			for (let i = 0; i + 1 < crossings.length; i += 2) {
				for (let x = Math.floor(crossings[i]); x <= Math.ceil(crossings[i + 1]); x++) {
					this.blend(x, y, r, g, b, alpha);
				}
			}
		}
	}
}

function mulberry32(seed) {
	return function () {
		seed |= 0;
		seed = seed + 0x6D2B79F5 | 0;
		let t = Math.imul(seed ^ seed >>> 15, 1 | seed);
		t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t;
		return ((t ^ t >>> 14) >>> 0) / 4294967296;
	};
}

const OUT = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'new-mod');

// ---------------------------------------------------------------- texture: small ball

/*
 * Pure white: RGB is always (255,255,255) and only alpha varies —— the palette is pure white,
 * and the dreamy feel comes from the soft layers of transparency.
 *
 * This gets mapped onto every slice of the sphere, so it is drawn with a "spherical" feel:
 * bright center, highlight at the upper left, edges fading out, and then a very faint
 * four-point star glint layered on top, so that up close you can tell this is a "star".
 */
function starOrb() {
	const canvas = new Canvas(16, 16);
	const c = 7.5;

	canvas.glow(c, c, 8.0, 0.88, 2.3);              // sphere main light
	canvas.glow(c - 2.1, c - 2.5, 3.4, 0.95, 2.0);  // upper-left highlight
	canvas.glow(c + 2.4, c + 2.8, 3.0, 0.35, 2.2);  // lower-right bounce light (spherical feel)
	canvas.star(c, c, 5.4, 0.8, 4, 9.0, 0.15);      // press only one **very faint** four-point star glint onto the ball's center

	return canvas;
}

// ---------------------------------------------------------------- texture: micro black block

/*
 * The texture used by the orbiting blocks (16x16).
 *
 * ⚠️ It must be an **item atlas** texture: in 26.3 the CuboidItemModelWrapper validates that
 * "the sprites used by an item model all come from a single atlas", while block textures
 * (textures/block/*) belong to the blocks atlas —— the moment an item model references one,
 * the whole model fails to bake and the item shows up in game as the black-and-purple "missing model".
 * So here we draw a separate micro block texture under textures/item/ instead of sharing the block's.
 */
function starMicroBlock() {
	const canvas = new Canvas(16, 16);
	const random = mulberry32(0xB10C);
	const base = [10, 10, 14];
	const edge = [26, 26, 34];
	const high = [54, 54, 66];

	// base color
	canvas.rect(0, 0, 15, 15, 1, base[0], base[1], base[2]);
	// subtle noise, so that a large flat area of solid color doesn't look dead
	for (let i = 0; i < 26; i++) {
		const x = Math.floor(random() * 16);
		const y = Math.floor(random() * 16);
		const bright = random() < 0.5 ? edge : high;
		canvas.rect(x, y, x, y, 1, bright[0], bright[1], bright[2]);
	}
	// a ring of bright edges: lets a 2x2 pixel micro block still read as having edges in 3D
	canvas.rect(0, 0, 15, 0, 1, high[0], high[1], high[2]);
	canvas.rect(0, 15, 15, 15, 1, edge[0], edge[1], edge[2]);
	canvas.rect(0, 0, 0, 15, 1, edge[0], edge[1], edge[2]);
	canvas.rect(15, 0, 15, 15, 1, edge[0], edge[1], edge[2]);

	return canvas;
}

// ---------------------------------------------------------------- 3D item model

/*
 * Shape: **one small ball floating in the hand, with 8 micro black blocks orbiting around it**.
 *
 *   - The ball is assembled from a "voxel sphere": 5 slices of 3 / 5 / 7 / 5 / 3 pixels wide stacked
 *     from bottom to top, and **only the faces exposed on the outside** are emitted (interior faces are
 *     left out) to avoid coplanar z-fighting;
 *   - The orbiting micro black blocks use the mod's own micro black block texture, distributed evenly
 *     around the Y axis, alternating in height and each with a bit of rotation —— like a ring of debris
 *     in orbit;
 *   - It does not inherit item/generated (otherwise vanilla would layer another flat texture on top), so
 *     the display transforms are all written out by hand, and the whole model is raised + scaled down so
 *     that it looks like it **floats above the palm**.
 */

const BALL_TEXTURE = '#orb';
const BLOCK_TEXTURE = '#block';

/** Voxel sphere: 5 slices, [from, to, additionally exposed faces]. */
const BALL_SLICES = [
	{ from: [6.5, 4.5, 6.5], to: [9.5, 5.5, 9.5], extra: ['down'] },
	{ from: [5.5, 5.5, 5.5], to: [10.5, 6.5, 10.5], extra: [] },
	{ from: [4.5, 6.5, 4.5], to: [11.5, 9.5, 11.5], extra: [] },
	{ from: [5.5, 9.5, 5.5], to: [10.5, 10.5, 10.5], extra: [] },
	{ from: [6.5, 10.5, 6.5], to: [9.5, 11.5, 9.5], extra: ['up'] },
];

/** The orbiting micro black blocks: count, radius, size, height and spin. */
const ORBIT_COUNT = 8;
const ORBIT_RADIUS = 6.4;
const ORBIT_BLOCK_SIZE = 2.0;
const ORBIT_HIGH_Y = 9.7;
const ORBIT_LOW_Y = 6.3;

const SIDES = ['north', 'east', 'south', 'west'];
const ALL_FACES = ['north', 'east', 'south', 'west', 'up', 'down'];

function faceMap(faces, texture) {
	const out = {};

	for (const face of faces) {
		out[face] = { texture };
	}

	return out;
}

function itemModel() {
	const elements = [];

	for (const slice of BALL_SLICES) {
		elements.push({
			from: slice.from.map(round),
			to: slice.to.map(round),
			shade: false,
			faces: faceMap([...SIDES, ...slice.extra], BALL_TEXTURE),
		});
	}

	for (let i = 0; i < ORBIT_COUNT; i++) {
		const angle = i * 2 * Math.PI / ORBIT_COUNT;
		const half = ORBIT_BLOCK_SIZE / 2;
		const cx = 8 + Math.cos(angle) * ORBIT_RADIUS;
		const cz = 8 + Math.sin(angle) * ORBIT_RADIUS;
		const cy = i % 2 === 0 ? ORBIT_HIGH_Y : ORBIT_LOW_Y;

		elements.push({
			from: [cx - half, cy - half, cz - half].map(round),
			to: [cx + half, cy + half, cz + half].map(round),
			shade: false,
			rotation: {
				origin: [round(cx), round(cy), round(cz)],
				axis: i % 3 === 0 ? 'x' : (i % 3 === 1 ? 'y' : 'z'),
				angle: i % 2 === 0 ? 22.5 : -22.5,
			},
			faces: faceMap(ALL_FACES, BLOCK_TEXTURE),
		});
	}

	return {
		gui_light: 'front',
		textures: {
			// both textures must come from the item atlas, otherwise the model fails to bake because of "multiple atlases"
			orb: 'new-mod:item/black_block_star',
			block: 'new-mod:item/black_block_star_block',
			particle: 'new-mod:item/black_block_star',
		},
		// there is no parent model, so the held/dropped/displayed transforms must all be written out by hand;
		// the +Y of translation is exactly that gap which makes it "float in the hand".
		display: {
			thirdperson_righthand: { rotation: [0, -90, 20], translation: [0, 7, 0.5], scale: [0.62, 0.62, 0.62] },
			thirdperson_lefthand: { rotation: [0, 90, -20], translation: [0, 7, 0.5], scale: [0.62, 0.62, 0.62] },
			firstperson_righthand: { rotation: [0, -90, 15], translation: [1.13, 5.6, 1.13], scale: [0.52, 0.52, 0.52] },
			firstperson_lefthand: { rotation: [0, 90, -15], translation: [1.13, 5.6, 1.13], scale: [0.52, 0.52, 0.52] },
			ground: { rotation: [0, 0, 0], translation: [0, 3, 0], scale: [0.5, 0.5, 0.5] },
			gui: { rotation: [0, 22.5, 0], translation: [0, 1, 0], scale: [0.9, 0.9, 0.9] },
			fixed: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [1, 1, 1] },
			head: { rotation: [0, 180, 0], translation: [0, 13, 7], scale: [1, 1, 1] },
		},
		elements,
	};
}

function round(value) {
	return Math.round(value * 100) / 100;
}

/** Pretty JSON: numeric arrays and {"texture": ...} stay on a single line. */
function formatJSON(value) {
	return JSON.stringify(value, null, '\t')
		.replace(/\[\s+([\d\s.,+-]+?)\s+\]/g, (_, inner) => `[${inner.split(/[\s,]+/).filter(Boolean).join(', ')}]`)
		.replace(/\{\s+"texture":\s+"([^"]+)"\s+\}/g, '{"texture": "$1"}') + '\n';
}

// ---------------------------------------------------------------- mod icon (eldritch)

function modIcon() {
	const size = 128;
	const canvas = new Canvas(size, size);
	const random = mulberry32(0x5EED);
	const c = size / 2;
	const black = [4, 4, 9];

	for (let y = 0; y < size; y++) {
		for (let x = 0; x < size; x++) {
			const t = Math.hypot(x - c, y - c) / (size * 0.7);
			const base = Math.round(13 * Math.pow(Math.max(0, 1 - t), 2.4));
			canvas.blend(x, y, base, base, base + 5, 1);
		}
	}

	for (let i = 0; i < 90; i++) {
		canvas.glow(random() * size, random() * size, 0.5 + random() * 0.7, 0.05 + random() * 0.12, 1.6);
	}

	for (const [tilt, alpha] of [[0.42, 0.16], [-0.30, 0.10]]) {
		for (let a = 0; a < Math.PI * 2; a += 0.012) {
			const wobble = Math.sin(a * 5) * 1.6;
			const rx = 46 + wobble;
			const ry = 20 + wobble * 0.4;
			const px = Math.cos(a) * rx;
			const py = Math.sin(a) * ry;
			const x = c + px * Math.cos(tilt) - py * Math.sin(tilt);
			const y = c + px * Math.sin(tilt) + py * Math.cos(tilt);
			canvas.glow(x, y, 0.75, alpha, 1.2);
		}
	}

	const half = 30;
	const corners = [
		[c - half + 3, c - half],
		[c + half, c - half - 3],
		[c + half - 3, c + half],
		[c - half, c + half + 3],
	];

	for (let y = 0; y < size; y++) {
		for (let x = 0; x < size; x++) {
			if (insidePolygon(x + 0.5, y + 0.5, corners)) {
				canvas.blend(x, y, black[0], black[1], black[2], 1);
			}
		}
	}

	for (let i = 0; i < corners.length; i++) {
		const [x0, y0] = corners[i];
		const [x1, y1] = corners[(i + 1) % corners.length];
		canvas.line(x0, y0, x1, y1, 0.6, 0.5);
	}

	canvas.glow(c, c, 29, 0.12, 2.2);
	canvas.star(c, c, 26, 2.6, 4, 8.0, 1.0);
	canvas.star(c, c, 16, 5.6, 8, 2.2, 0.30);
	canvas.glow(c, c, 7.0, 1.0, 1.5);

	for (let y = c - 12; y <= c + 12; y++) {
		const width = 0.9 + 1.7 * Math.max(0, 1 - Math.abs(y - c) / 12);
		canvas.rect(c - width, y, c + width, y, 1, black[0], black[1], black[2]);
	}

	for (let i = 0; i < 6; i++) {
		let angle = i * Math.PI / 3 + (random() - 0.5) * 0.5;
		let x = c + Math.cos(angle) * 20;
		let y = c + Math.sin(angle) * 20;

		for (let step = 0; step < 7; step++) {
			angle += (random() - 0.5) * 0.7;
			const nx = x + Math.cos(angle) * (3.5 + random() * 3);
			const ny = y + Math.sin(angle) * (3.5 + random() * 3);
			canvas.line(x, y, nx, ny, 0.55, 0.30 * (1 - step / 7));
			x = nx;
			y = ny;
		}
	}

	for (let i = 0; i < 22; i++) {
		const angle = random() * Math.PI * 2;
		const radius = 38 + random() * 24;
		const px = c + Math.cos(angle) * radius;
		const py = c + Math.sin(angle) * radius;
		const alpha = 0.22 + random() * 0.45;

		if (random() < 0.4) {
			const s = 0.8 + random() * 2.2;
			canvas.rect(px, py, px + s, py + s, alpha);
		} else {
			canvas.glow(px, py, 0.9 + random() * 1.1, alpha, 1.6);
		}
	}

	for (let y = 0; y < size; y++) {
		for (let x = 0; x < size; x++) {
			const t = Math.hypot(x - c, y - c) / (size * 0.6);

			if (t > 0.6) {
				canvas.blend(x, y, 0, 0, 0, Math.min(0.95, (t - 0.6) * 2.0));
			}
		}
	}

	return canvas;
}

function insidePolygon(x, y, corners) {
	let inside = false;

	for (let i = 0, j = corners.length - 1; i < corners.length; j = i++) {
		const [xi, yi] = corners[i];
		const [xj, yj] = corners[j];

		if ((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
			inside = !inside;
		}
	}

	return inside;
}

// ---------------------------------------------------------------- preview

/** Texture preview: magnified with nearest neighbor and composited onto a dark checkerboard (otherwise a white image on a white background shows nothing at all). */
function texturePreview(canvas, factor) {
	const out = new Canvas(canvas.width * factor, canvas.height * factor);
	const cell = Math.max(4, factor / 2);

	for (let y = 0; y < out.height; y++) {
		for (let x = 0; x < out.width; x++) {
			const [r, g, b, a] = canvas.get(Math.floor(x / factor), Math.floor(y / factor));
			const bg = (Math.floor(x / cell) + Math.floor(y / cell)) % 2 === 0 ? 46 : 78;

			out.blend(x, y, bg, bg, bg + 12, 1);
			out.blend(x, y, r, g, b, a / 255);
		}
	}

	return out;
}

/**
 * Model preview: isometric projection, filling in the three visible faces of each box.
 *
 * The model space x/y/z all run 0..16 (y is up). Projection:
 *   sx = (x - z) * cos30 ;  sy = (x + z) * sin30 - y
 * That is entirely enough to check a layout like "ball in the middle, blocks all around".
 */
function modelPreview(model, scale = 11) {
	const size = Math.ceil(32 * scale);
	const canvas = new Canvas(size, size);
	const cos30 = Math.cos(Math.PI / 6);
	const sin30 = 0.5;
	const project = ([x, y, z]) => [
		size / 2 + (x - z) * cos30 * scale * 0.62,
		size / 2 + ((x + z) * sin30 - y * 1.6) * scale * 0.62 + 6,
	];

	const boxes = model.elements.map((element) => ({
		element,
		min: element.from,
		max: element.to,
		depth: element.from[0] + element.from[2] - element.from[1],
	}));

	// draw the far ones first
	boxes.sort((a, b) => a.depth - b.depth);

	for (const box of boxes) {
		const [x0, y0, z0] = box.min;
		const [x1, y1, z1] = box.max;
		const corner = (x, y, z) => project([x, y, z]);
		const orb = box.element.faces.north.texture === BALL_TEXTURE;
		const base = orb ? 245 : 40;
		// top face (y1)
		canvas.polygon([corner(x0, y1, z0), corner(x1, y1, z0), corner(x1, y1, z1), corner(x0, y1, z1)], 1, base, base, base);
		// south face (z1)
		canvas.polygon([corner(x0, y0, z1), corner(x1, y0, z1), corner(x1, y1, z1), corner(x0, y1, z1)],
			1, base * 0.82, base * 0.82, base * 0.82);
		// east face (x1)
		canvas.polygon([corner(x1, y0, z0), corner(x1, y0, z1), corner(x1, y1, z1), corner(x1, y1, z0)],
			1, base * 0.66, base * 0.66, base * 0.66);
	}

	return canvas;
}

// ---------------------------------------------------------------- main flow

console.log('Generating New Mod art assets...');

const orb = starOrb();
writePNG(path.join(OUT, 'textures', 'item', 'black_block_star.png'), 16, 16, orb.data);

const micro = starMicroBlock();
writePNG(path.join(OUT, 'textures', 'item', 'black_block_star_block.png'), 16, 16, micro.data);

const icon = modIcon();
writePNG(path.join(OUT, 'icon.png'), 128, 128, icon.data);

const model = itemModel();
const modelFile = path.join(OUT, 'models', 'item', 'black_block_star.json');
fs.mkdirSync(path.dirname(modelFile), { recursive: true });
fs.writeFileSync(modelFile, formatJSON(model));
console.log(`  ${path.relative(process.cwd(), modelFile)}  ${model.elements.length} elements` +
	` (ball ${BALL_SLICES.length} slices + orbiting blocks ${ORBIT_COUNT})`);

if (process.argv[2] === '--preview') {
	const dir = process.argv[3] || path.join(__dirname, 'preview');
	const orbBig = texturePreview(orb, 16);
	const modelBig = modelPreview(model);

	writePNG(path.join(dir, 'orb-preview.png'), orbBig.width, orbBig.height, orbBig.data);
	writePNG(path.join(dir, 'model-preview.png'), modelBig.width, modelBig.height, modelBig.data);
}

console.log('Done.');
