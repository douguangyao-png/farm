// HBuilderX 项目转命令行构建的启动器（farmer-saas 新增）
// uni CLI 默认把源码目录当成 ./src；本项目源码在根目录，所以要设置 UNI_INPUT_DIR。
// 必须是绝对路径：用相对路径 "." 时，easycom 生成的 import 会变成裸路径（如 "sheep/components/..."），解析失败。
const path = require('node:path')

process.env.UNI_INPUT_DIR = path.resolve(__dirname, '..')
require('@dcloudio/vite-plugin-uni/bin/uni.js')
