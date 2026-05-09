# PeerLock Git 规范

## 分支策略

- `main`：稳定分支，只用于发布。
- `develop`：开发分支，所有新功能先合并到这里。

临时分支：
- `feat/<模块>`：新功能
- `fix/<模块>`：Bug 修复
- 大型任务可拆分为 `feat/<模块>/<子功能>`

## 开发流程

### 1. 新功能
```bash
git checkout -b feat/<模块> develop
# 多次提交，每次一个原子改动
git commit -m "feat: 简短描述"
```

### 2. 合并
```bash
git checkout develop
git merge --no-ff feat/<模块>
git branch -d feat/<模块>
```

### 3. 发布
版本号：`v主.次.修订`（如 v0.1.0）

```bash
git checkout main
git merge --no-ff --no-commit develop

# 排除开发文档
git reset HEAD docs/superpowers CLAUDE.md 2>/dev/null || true
rm -rf docs/superpowers CLAUDE.md 2>/dev/null || echo "无需清理"

git commit -m "release: v0.1.0 描述"
git tag -a v0.1.0 -m "v0.1.0"
git push origin main --tags
```

### 4. 紧急修复
```bash
git checkout main
git checkout -b fix/<模块>/v0.1.0
# 修复...
git commit -m "fix: 描述"
git checkout develop && git merge --no-ff fix/<模块>/v0.1.0
git checkout main && git merge --no-ff fix/<模块>/v0.1.0
git push origin main develop
```

## 提交信息规范

格式：`类型: 中文描述`

类型：
- `feat`：新功能
- `fix`：Bug 修复
- `refactor`：重构
- `docs`：文档
- `chore`：构建/依赖

**禁止在提交信息中出现 `Co-authored-by` 等署名痕迹。**

## 注意事项

- 项目侧载分发，`main` 仅保留源代码，排除 `docs/superpowers/`、`CLAUDE.md`。
- 涉及 Device Owner 权限的代码，合并前需在多款 ROM 上验证。