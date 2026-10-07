# 学员注册验收单

日期：2026-10-02。状态：技术验证通过，等待 P1 统一验收。

复用 Auth 的 API、Service、Model 三层及 users.user；公开注册仅接受账号、姓名、密码和确认密码。服务端固定学员 10201、启用状态 1，不分配机构或管理角色。密码至少 8 位且最多 72 个 UTF-8 字节，BCrypt 保存；用户名唯一冲突返回 409，格式错误返回 400。

## 校验结果

- 注册创建、重复用户名、弱密码、密码不一致、CSRF、伪造角色、登录和机构接口拒绝：已有 9 项实时断言通过。
- 本轮账号补验 19 项：JSON 数组、非字符串、超长密码、非法账号、机构/角色伪造、管理权限等均按预期拒绝。只读数据库核查新学员和老师密码均为 60 位 BCrypt 摘要，未输出密码或摘要。
- 新注册账号已完成搜索→免费选课→我的学习→真实 MP4 Range 播放；收费选课仍待支付，未取得播放资格。完整回归 13 项通过。
- 真实浏览器注册页、登录、学习页面核验通过。

## 人工验收

打开 http://localhost:5174/register，创建一个未使用的账号，再登录、选取免费课程 159 并查看学习目录。重复用户名应提示冲突，密码不一致不能创建账号。登录后无法访问机构课程维护接口。

本机既有测试学员为 p1-student-1790870283，密码仅保存在 .runtime/p1-new-accounts.json；亦可自行注册。证据：.runtime/p1-registration-results.json、p1-account-final-results.json、p1-full-workflow-results.json；截图 .runtime/p1-proof/register.jpg、learning.jpg、playback.jpg。
