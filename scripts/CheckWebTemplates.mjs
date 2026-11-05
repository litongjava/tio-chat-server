// Parse generated front-end files with the Vue and TypeScript compilers.
// usage: node CheckWebTemplates.mjs <dir> <path of @vue/compiler-sfc> <path of typescript>
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { createRequire } from 'node:module';

const [dir, sfcPath, tsPath] = process.argv.slice(2);
const require = createRequire(import.meta.url);
const sfc = require(sfcPath);
const ts = require(tsPath);
const files = [];
const walk = (d) => {
  for (const name of readdirSync(d)) {
    const path = join(d, name);
    if (statSync(path).isDirectory()) { walk(path); } else { files.push(path); }
  }
};
walk(dir);
const checkTs = (code, fileName) => {
  const out = ts.transpileModule(code, { fileName, reportDiagnostics: true, compilerOptions: { target: 'ESNext', jsx: 'preserve' } });
  return (out.diagnostics || []).map((d) => ts.flattenDiagnosticMessageText(d.messageText, ' '));
};
let bad = 0;
for (const file of files) {
  const code = readFileSync(file, 'utf8');
  let errors = [];
  if (file.endsWith('.vue')) {
    const parsed = sfc.parse(code, { filename: file });
    errors = parsed.errors.map((e) => e.message || String(e));
    if (parsed.descriptor.template) {
      const compiled = sfc.compileTemplate({ source: parsed.descriptor.template.content, filename: file, id: 'x' });
      errors.push(...compiled.errors.map((e) => e.message || String(e)));
    }
    for (const block of [parsed.descriptor.script, parsed.descriptor.scriptSetup]) {
      if (block) { errors.push(...checkTs(block.content, file + '.tsx')); }
    }
  } else if (/\.tsx?$/.test(file)) {
    errors = checkTs(code, file);
  } else if (file.endsWith('.json')) {
    try { JSON.parse(code); } catch (e) { errors = [e.message]; }
  }
  if (errors.length) {
    bad++;
    console.log('BAD ' + file.slice(dir.length + 1) + ': ' + errors.slice(0, 3).join(' | '));
  }
}
console.log(`files ${files.length}, with errors ${bad}`);
process.exit(bad ? 1 : 0);
