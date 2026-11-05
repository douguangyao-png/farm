# 发布改为「推到 GitHub → 121 服务器拉代码、测试、构建」

- 类型：工程
- 档位：B
- 风险：高（共享生产服务器：要装 git、在上面跑前端构建，构建会占几 GB 内存和多个 CPU 核，可能让实训中心、资衡短时间变慢；代码会放到 GitHub 私有仓库，属于外发）
- 状态：草稿
- 批准：待批准
- 关联：docs/plans/deploy-演示环境-121.40.221.126-2026-11-05.md（现有发布方式、目录、回滚）；用户 2026-11-05：「所有的测试，不要在本地运行。去服务器运行测试」「是否可以上传到git上，然后121服务器，从git上下载？」

## 1. 调研

- **目标：** 发布时不再从本机传 30 MB 的构建产物，只传代码改动；测试、构建都在服务器上做。
- **影响面：** 本机 → 服务器的网络；服务器上的实训中心（training.service）、资衡（ziheng-*）在构建期间的 CPU 和内存；`/opt/farmer-saas/` 目录；GitHub 账号。清单来自部署方案第 1 节和下面的实测。
- **同类：** 现在的 `deploy/server-test.sh` 已经在服务器 `/opt/farmer-saas/ci/` 跑 mock 测试，这次把它扩展成「拉代码 + 测试 + 构建 + 替换」。
- **改法：** 本机 `git push` 到 GitHub 私有仓库；服务器用只读 deploy key `git pull` 到 `/opt/farmer-saas/src`，在那里装依赖、跑测试、构建三个前端和 mock 服务，然后按现有方式替换 `web/`、`mock-server/` 并重启。

证据（2026-11-05 实测）：
- [实测] 本机到服务器：ping 平均 327 ms、丢包 20%；上传约 14 KB/s（第三次发布 30 MB 的前端包传了约 20 分钟），从服务器下载约 400 KB/s。
- [实测] 本机 → github.com 1.0 s 能通；本机 → gitee.com 15 s 超时。服务器 → github.com 0.75 s、gitee.com 1.1 s 都能通。
- [实测] 服务器自己访问 npmmirror 很快（mock 依赖 154 个包 4 秒装完）；没有装 git；内存 14 GB 可用 12 GB，8 核。
- [实测] 仓库打包大小 85 MB（主要是三个前端的上游代码），之后每次推送通常只有几十 KB。
- [实测] 本机构建后台时内存不够被系统中止过一次（本机 31.7 GB，IDEA / Java 常驻）。

## 2. 设计

**仓库：** GitHub 私有仓库（账号和仓库名由用户定，建议 `farmer-saas`）。本机 `gh auth login` 由用户自己完成（不代输密码）。

**服务器目录**（在现有 `/opt/farmer-saas/` 下新增）：

```
/opt/farmer-saas/
  src/            git clone 的工作区（只读 deploy key 拉取），依赖装在各自的 node_modules
  ci/             现有的 mock 测试目录，改为指向 src/ 后删除
  web/ mock-server/ docs/ releases/   不变
/root/.ssh/farm_deploy_ed25519        只读 deploy key（只对这一个仓库，不能推送）
```

**一次发布的流程**（`deploy/deploy.sh` 改写；本机只做 push 和远程触发）：

1. 本机：`git push`，然后 `ssh` 执行服务器上的 `deploy/server-build.sh <提交号> [all|mock|admin|m|steward]`。
2. 服务器：`git fetch && git checkout <提交号>`；依赖变了才重装（`npm ci` / `pnpm install --frozen-lockfile`，走 npmmirror）。
3. 服务器：mock 的 `tsc` + `vitest`、后台 `ts:check`（顺便补上目前没做的前端类型检查），有失败就停。
4. 服务器：按需构建（`nice -n 10`，一次只跑一个，后台构建限 `--max_old_space_size=6144`），产物检查和现在一样。
5. 服务器：旧版本打包进 `releases/`，替换 `web/` 或 `mock-server/`，重启 `farm-mock`；本机跑公网回归。

**资源约束：** 构建前检查可用内存，低于 8 GB 就不构建并提示；构建用 `nice`，不和别的重任务并发。

**否决的方案：**
- Gitee：本机推不上去（实测 15 s 超时）。
- 在 121 上放裸仓库、本机直接推：第一次要把 85 MB 走现在这条慢线路（约 1.5 小时）。
- 继续本机构建、只传产物：就是现在的慢和本机内存问题。

## 3. 任务

- [ ] T1 GitHub：用户确认账号和仓库名、本机 `gh auth login`；创建私有仓库并推送 main
  - 验证：`git ls-remote origin` 能看到 main 的提交号，与本机 `git rev-parse HEAD` 一致；仓库在 GitHub 上是 Private
- [ ] T2 服务器：`dnf install -y git`；生成只读 deploy key 并加到仓库；clone 到 `/opt/farmer-saas/src`
  - 验证：`git -C /opt/farmer-saas/src rev-parse HEAD` 与本机一致；用 deploy key `git push` 被拒（证明只读）；`/`、`/training/`、`/ziheng/` 状态码不变
- [ ] T3 服务器：装三个前端和 mock 的依赖；写 `deploy/server-build.sh`；把 `server-test.sh` 改为调用它的测试步骤
  - 验证：服务器上 mock `vitest` 全部通过、后台 `ts:check` 跑完并给出错误数（目前已知 10 个都在未改动的上游模块）；三个前端构建成功，产物检查通过；构建期间 `free -g` 可用内存不低于 4 GB，`/training/` 响应正常
- [ ] T4 改写本机 `deploy/deploy.sh`：push + 远程触发 + 公网回归；用它发一次真实版本
  - 验证：本机这一步耗时从约 20 分钟降到几分钟内；发布后公网回归同现在；浏览器打开三个端的登录页正常

## 4. 回滚

- 发布方式回滚：继续用旧的本机构建方式（`git checkout` 到改写前的 `deploy/deploy.sh`）。服务器上的 `web/`、`mock-server/` 结构不变，不需要额外操作。
- 某次发布出问题：从 `/opt/farmer-saas/releases/` 解压上一个包覆盖（现有方式，见部署方案第 4 节）。
- 撤销服务器改动：`rm -rf /opt/farmer-saas/src /root/.ssh/farm_deploy_ed25519`；在 GitHub 仓库里删掉 deploy key；`dnf remove -y git`。
- 撤销 GitHub：用户在 GitHub 上删除仓库（删除只能用户自己做）。

## 5. 记录

- 待用户确认：①本方案；②GitHub 账号和仓库名；③本机执行 `! gh auth login`。
