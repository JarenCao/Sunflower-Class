# Sunflower-Class

- [开发清单](docs/DEVELOPMENT_CHECKLIST.md)
- [需求文档](docs/REQUIREMENTS.md)
- [前端技术方案](docs/FRONTEND_TECH_PROPOSAL.md)

## 前端应用

`frontend-admin/` 是机构与审核工作台，`frontend-student/` 是学员门户。需要 Node.js 20.19+ 或 22.12+。分别在两个目录执行 `npm ci`、`npm run dev`；默认端口为 5173 和 5174。两端连接真实后端，不使用浏览器本地存储或演示数据。运行环境及功能边界见 [真实联调说明](docs/LIVE_INTEGRATION.md)。配置和已接通接口见 [机构端说明](frontend-admin/README.md) 与 [学员端说明](frontend-student/README.md)。

- [本次优化验收](docs/ACCEPTANCE_20260925.md)
- [五位状态编码与迁移](docs/STATUS_CODES.md)

## 代码注释与格式

关键业务规则使用中文注释；Java 使用 4 个空格缩进，Vue、TypeScript 和 CSS 使用 2 个空格缩进。组件属性和较长表达式按统一规则换行。

在仓库根目录执行以下命令（根目录的 npm 依赖仅用于代码格式化，两个前端仍各自安装依赖）：

```sh
npm ci
npm run format
npm run format:check
```

`format` 整理前后端源码，`format:check` 只检查格式。规则见 `.prettierrc.json`，自动跳过构建产物、依赖和临时文件。
