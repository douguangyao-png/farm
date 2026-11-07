// HBuilderX 项目转命令行构建的启动器（farmer-saas 新增）
// uni CLI 默认把源码目录当成 ./src；本项目源码在根目录，所以要设置 UNI_INPUT_DIR。
// 必须是绝对路径：用相对路径 "." 时，easycom 生成的 import 会变成裸路径（如 "sheep/components/..."），解析失败。
//
// --mode demo（部署到子路径 /farm/m/）：uni 的 H5 路由前缀只读 manifest.json 的 h5.router.base，
// 没有环境变量可改，所以构建期间临时改写 manifest，进程退出时还原。
const path = require('node:path')
const fs = require('node:fs')

const root = path.resolve(__dirname, '..')
process.env.UNI_INPUT_DIR = root

const SUB_PATH_MODES = { demo: '/farm/m/', real: '/farm-real/m/' }
const modeIdx = process.argv.indexOf('--mode')
const subBase = modeIdx > 0 ? SUB_PATH_MODES[process.argv[modeIdx + 1]] : undefined

if (subBase) {
  const file = path.join(root, 'manifest.json')
  const original = fs.readFileSync(file, 'utf-8')
  const marker = '"base": "/"'
  if (!original.includes(marker)) throw new Error(`manifest.json 里找不到 ${marker}，无法设置子路径`)
  fs.writeFileSync(file, original.replace(marker, `"base": "${subBase}"`))
  const restore = () => fs.writeFileSync(file, original)
  process.on('exit', restore)
  for (const sig of ['SIGINT', 'SIGTERM']) process.on(sig, () => process.exit(130))
  process.env.UNI_H5_BASE = subBase
}

require('@dcloudio/vite-plugin-uni/bin/uni.js')
