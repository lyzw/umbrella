# growth-planet-admin · 成长星球运营后台（里程碑 A）

Vue 3 + Vite + Element Plus。对接后端 `growth-planet` 的 `/api/admin/**` 独立后台接口（与 C 端 `/api/mini/**` 物理隔离）。

## 启动

```bash
npm install
npm run dev        # http://localhost:5180，/api 代理到 http://localhost:19100
```

后端先启动（growth-planet，dev profile）。首个超级管理员账号由后端 `console.bootstrap.*` 配置播种（缺省 `admin.zhou / admin123`，仅开发环境）。

## 已实现页面（里程碑 A 地基切片）

| 页面 | 路由 | 说明 |
|---|---|---|
| 登录 | `#/login` | Admin JWT，登录/登出均记审计 |
| 工作台 | `#/workbench` | KPI 卡片 + 待办列表（M0） |
| 后台账号 | `#/accounts` | 账号 CRUD、启停、重置密码（M2），按钮级权限（`后台账号:view/create/edit`） |
| 角色权限矩阵 | `#/role-perms` | 6 角色 × 7 操作矩阵只读视图（M2） |

## 关键约定

- 响应信封 `{ code, message, data, requestId }`，`code=0` 成功；非 0 弹业务 message。
- token 存 `localStorage`（`gp_admin_token`），每次请求带 `Authorization: Bearer`；401 自动清 token 回登录页。
- 权限点格式 `资源:操作`，如 `后台账号:view`；SA 超管前端全通过（后端同样旁路）。
