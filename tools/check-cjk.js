'use strict';

/*
 * Guard for the "all Java comments are English" rule.
 *
 *   node tools/check-cjk.js
 *
 * Scans src/main/java for CJK characters and prints every offending line together with the
 * characters found, so a stray Chinese comment (or a leftover translation) is easy to spot.
 * Exits with code 1 when anything is found, so it can be wired into CI.
 *
 * Note: the player-facing localisation files under src/main/resources keep their Chinese on
 * purpose (lang/zh_cn.json is a translation file, not a comment) — they are not scanned here.
 */

const fs = require('fs');
const path = require('path');

const ROOT = 'src/main/java';
const CJK = /[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]/;

const files = [];

(function walk(dir) {
	for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
		const full = path.join(dir, entry.name);

		if (entry.isDirectory()) {
			walk(full);
		} else if (entry.name.endsWith('.java')) {
			files.push(full);
		}
	}
})(ROOT);

let offendingLines = 0;
let cleanFiles = 0;

for (const file of files) {
	const lines = fs.readFileSync(file, 'utf8').split('\n');
	const hits = [];

	for (let i = 0; i < lines.length; i++) {
		if (!CJK.test(lines[i])) {
			continue;
		}

		const found = [...new Set([...lines[i]].filter((char) => CJK.test(char)))];
		hits.push(`  ${i + 1}: [${found.join('')}] ${lines[i].trim().slice(0, 110)}`);
	}

	if (hits.length === 0) {
		cleanFiles++;
		continue;
	}

	offendingLines += hits.length;
	console.log(`${file}  (${hits.length} line(s))`);
	console.log(hits.join('\n'));
}

console.log(`\nclean: ${cleanFiles}/${files.length} files; lines containing CJK: ${offendingLines}`);
process.exit(offendingLines === 0 ? 0 : 1);
