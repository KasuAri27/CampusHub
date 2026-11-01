import fs from 'node:fs'
import path from 'node:path'
import { spawnSync } from 'node:child_process'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'

// 检查全部源文件，而不仅是当前路由可达的文件，防止残留错误代码逃过构建。
let count = 0
const problems = []
function visit(directory) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const file = path.join(directory, entry.name)
    if (entry.isDirectory()) { visit(file); continue }
    if (!/\.(vue|js)$/.test(file)) continue
    count++
    try {
      if (file.endsWith('.js')) {
        const result = spawnSync(process.execPath, ['--check', file], { encoding: 'utf8' })
        if (result.status !== 0) throw new Error(result.stderr)
      } else {
        const { descriptor, errors } = parse(fs.readFileSync(file, 'utf8'), { filename: file })
        if (errors.length) throw new Error(errors.map(String).join('\n'))
        if (descriptor.script || descriptor.scriptSetup) compileScript(descriptor, { id: file })
        if (descriptor.template) {
          const result = compileTemplate({ source: descriptor.template.content, id: file, filename: file })
          if (result.errors.length) throw new Error(result.errors.map(String).join('\n'))
        }
      }
    } catch (error) { problems.push(`${file}: ${error.message}`) }
  }
}
visit('src')
if (problems.length) { console.error(problems.join('\n')); process.exitCode = 1 }
else console.log(`已检查 ${count} 个源文件`)
