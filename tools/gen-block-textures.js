'use strict';

/*
 * Solid block texture generator (pure Node, no third-party libraries).
 *
 *   node tools/gen-block-textures.js
 *
 * Why this exists: every "plain coloured block" needs the same 16x16 picture — a flat interior plus a one pixel
 * border that is slightly darker, so the block edges stay readable in game. Drawing those by hand (or in an
 * editor) for every new material is the kind of chore that leads to inconsistent borders, so the pattern is
 * described once here.
 *
 * The palette is paired on purpose: the white block is the **exact inverse** of the black block.
 *
 *   black block   interior #1C1C20 (28,28,32)   border #121214 (18,18,20)
 *   white block   interior #E3E3DF (227,227,223) border #EDEDEB (237,237,235)
 *
 * The blue-ish tinge of the black block is kept (its blue channel is 4 above red/green); the white block keeps
 * the same amount on the other side, so the two stay neutral instead of the white one drifting yellow.
 *
 * Output: src/main/resources/assets/new-mod/textures/block/<name>.png
 */

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

// ---------------------------------------------------------------- PNG encoding (same hand-written writer as gen-assets.js)

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
		chunk('IEND', Buffer.alloc(0))
	]);

	fs.mkdirSync(path.dirname(file), { recursive: true });
	fs.writeFileSync(file, png);

	return png.length;
}

// ---------------------------------------------------------------- the pattern

const SIZE = 16;

/** The inverse of a colour: each channel mirrored around 128, which is exactly `255 - value`. */
function inverse(rgb) {
	return rgb.map((channel) => 255 - channel);
}

/**
 * One flat colour with a one pixel border around the whole tile.
 *
 * @param {number[]} interior RGB of the inner 14x14 area
 * @param {number[]} border   RGB of the outer ring
 */
function borderedTile(interior, border) {
	const rgba = Buffer.alloc(SIZE * SIZE * 4);

	for (let y = 0; y < SIZE; y++) {
		for (let x = 0; x < SIZE; x++) {
			const edge = x === 0 || y === 0 || x === SIZE - 1 || y === SIZE - 1;
			const [r, g, b] = edge ? border : interior;
			const offset = (y * SIZE + x) * 4;

			rgba[offset] = r;
			rgba[offset + 1] = g;
			rgba[offset + 2] = b;
			rgba[offset + 3] = 255;
		}
	}

	return rgba;
}

// ---------------------------------------------------------------- blocks

const BLACK_INTERIOR = [28, 28, 32];
const BLACK_BORDER = [18, 18, 20];

/** name -> RGBA buffer. Keep this list in step with the solid blocks in content/block/ModBlocks. */
const TILES = {
	black_block: borderedTile(BLACK_INTERIOR, BLACK_BORDER),
	white_block: borderedTile(inverse(BLACK_INTERIOR), inverse(BLACK_BORDER))
};

const OUT = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'new-mod', 'textures', 'block');

for (const [name, data] of Object.entries(TILES)) {
	const file = path.join(OUT, `${name}.png`);
	const bytes = writePNG(file, SIZE, SIZE, data);
	console.log(`wrote ${path.relative(path.join(__dirname, '..'), file)} (${bytes} bytes)`);
}

console.log(`\nwhite block interior ${inverse(BLACK_INTERIOR).join(',')} / border ${inverse(BLACK_BORDER).join(',')}`);
