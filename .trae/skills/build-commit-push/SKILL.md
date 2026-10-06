---
name: build-commit-push
description: Baibaoge Android 项目的编译验证、提交并推送到 GitHub 的标准流程。用户说"上传git"、"提交代码"、"推送到github"、"commit并push"时使用。不要用于只构建不提交或未要求推送的场景。
---

# build-commit-push（百宝格项目）

在 `c:\Users\12765\AndroidStudioProjects\Baibaoge` 中执行「编译 → 提交 → 推送」固定流程。

## 流程

1. **查看改动**：`git status --short`，据此归纳本次提交内容。
2. **编译验证（必须先过）**：
   ```powershell
   .\gradlew :app:assembleDebug -q
   ```
   - 工作目录固定为项目根，Shell 是 PowerShell 5，**命令分隔用 `;` 不能用 `&&`**。
   - 编译失败：读错误，修复后重新编译，直到 BUILD SUCCESSFUL，不要带病提交。
   - `-q` 成功时无输出，即视为通过；超时设 600000ms。
3. **暂存与提交**：
   ```powershell
   git add -A
   git commit -m "<标题>" -m "<要点列表>"
   ```
   - 提交信息用中文，标题用 conventional 前缀（feat/fix/docs/chore/refactor），描述实际功能而非泛泛"更新代码"。
   - 第二个 `-m` 用 `- ` 开头的要点列表，按功能模块归纳；涉及部署/配置修复也列入。
   - LF→CRLF 警告是 Windows 正常现象，无需处理。
4. **推送**：
   ```powershell
   git push origin main
   ```
   - 远程 `origin` = https://github.com/yangderry/baibaoge.git，凭据已由系统管理器托管，无需交互。
   - 网络超时时可重试；不要 force push、不要改历史。
5. 完成后向用户报告 commit 短哈希和文件变更统计。

## 规则

- 用户没明确要求提交/推送时，不执行 git 写操作。
- 不提交 `local.properties`、`.idea/`、`build/`（.gitignore 已排除，不要改动忽略规则）。
- 若改动包含值得沉淀的项目决策或踩坑，提交后追加更新 project_memory.md（记忆目录中该项目的文件）。
- 一次推送涵盖多个阶段功能时，标题用 `feat: 阶段X + 阶段Y ...` 概括，正文分条列出。
