#!/usr/bin/env node
// Syntax check of generated front-end code: every .vue goes through the Vue compiler, .ts and .tsx through TypeScript, .json through JSON.parse.
//   node scripts/check-web-code.mjs <dir> <node_modules with typescript and @vue/compiler-sfc>
import { createRequire } from 'node:module'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'

const [dir, modules] = process.argv.slice(2)
const require = createRequire(join(modules, 'x.js'))
const ts = require('typescript')
const sfc = require('@vue/compiler-sfc')

const files = []
const walk = (d) => {
  for (const name of readdirSync(d)) {
    const path = join(d, name)
    if (statSync(path).isDirectory()) {
      walk(path)
    } else {
      files.push(path)
    }
  }
}
walk(dir)

const transpile = (code, fileName) =>
  ts
    .transpileModule(code, {
      fileName,
      reportDiagnostics: true,
      compilerOptions: { jsx: ts.JsxEmit.Preserve, target: ts.ScriptTarget.ESNext, module: ts.ModuleKind.ESNext }
    })
    .diagnostics.map((d) => ts.flattenDiagnosticMessageText(d.messageText, ' '))

const errors = []
for (const file of files) {
  const code = readFileSync(file, 'utf8')
  const report = (messages) => messages.forEach((m) => errors.push(file.slice(dir.length + 1) + ': ' + String(m.message ?? m).split('\n')[0]))
  if (code.includes('${') && /FreeMarker|<#|<\/#/.test(code)) {
    report(['template syntax left in the output'])
  }
  if (file.endsWith('.vue')) {
    const { descriptor, errors: parseErrors } = sfc.parse(code, { filename: file })
    report(parseErrors)
    if (descriptor.template) {
      report(sfc.compileTemplate({ id: 'x', filename: file, source: descriptor.template.content, compilerOptions: { expressionPlugins: ['typescript'] } }).errors)
    }
    for (const block of [descriptor.script, descriptor.scriptSetup]) {
      if (block) {
        report(transpile(block.content, file + (block.lang === 'tsx' ? '.tsx' : '.ts')))
      }
    }
  } else if (file.endsWith('.ts') || file.endsWith('.tsx')) {
    report(transpile(code, file))
  } else if (file.endsWith('.json')) {
    try {
      JSON.parse(code)
    } catch (e) {
      report([e.message])
    }
  }
}
console.log('files ' + files.length + ', errors ' + errors.length)
errors.slice(0, 40).forEach((e) => console.log('  ' + e))
process.exit(errors.length ? 1 : 0)
