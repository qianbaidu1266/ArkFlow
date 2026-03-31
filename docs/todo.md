# LangGraph4J Engine 开发计划

## 一、工作流执行功能 (高优先级)

### 1.1 执行面板
- [ ] 点击运行按钮弹出执行面板（模态框）
- [ ] 显示工作流名称和描述
- [ ] 输入参数配置区域
  - [ ] 根据开始节点的 inputVariables 动态生成输入表单
  - [ ] 支持 JSON 编辑器输入
- [ ] 执行按钮和取消按钮
- [ ] 执行状态显示（执行中/成功/失败）

### 1.2 执行结果展示
- [ ] 执行成功/失败状态
- [ ] 执行耗时显示
- [ ] 输出结果展示
  - [ ] JSON 格式化显示
- [ ] 错误信息展示
- [ ] 执行历史记录查看

### 1.3 后端对接
- [ ] 前端调用 `/api/workflows/:id/execute` 接口
- [ ] 处理异步执行结果
- [ ] 执行超时处理

---

## 二、多租户系统 (中优先级)

### 2.1 数据库设计
```sql
-- 用户表
CREATE TABLE users (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),
    avatar_url VARCHAR(500),
    status VARCHAR(20) DEFAULT 'active',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    last_login_at BIGINT
);

-- 空间表
CREATE TABLE spaces (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    owner_id VARCHAR(64) NOT NULL,
    type VARCHAR(20) DEFAULT 'personal',  -- personal, team
    status VARCHAR(20) DEFAULT 'active',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    FOREIGN KEY (owner_id) REFERENCES users(id)
);

-- 空间成员表
CREATE TABLE space_members (
    id VARCHAR(64) PRIMARY KEY,
    space_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) DEFAULT 'member',  -- owner, admin, member, viewer
    joined_at BIGINT NOT NULL,
    UNIQUE(space_id, user_id),
    FOREIGN KEY (space_id) REFERENCES spaces(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 修改 workflows 表
ALTER TABLE workflows ADD COLUMN space_id VARCHAR(64);
ALTER TABLE workflows ADD CONSTRAINT fk_workflow_space FOREIGN KEY (space_id) REFERENCES spaces(id);
```

### 2.2 用户认证系统
- [ ] 用户注册
  - [ ] 用户名/邮箱/密码注册
  - [ ] 邮箱验证
- [ ] 用户登录
  - [ ] 用户名/邮箱登录
  - [ ] JWT Token 生成
  - [ ] Token 刷新机制
- [ ] 用户登出
- [ ] 密码找回
- [ ] 第三方登录（可选）

### 2.3 空间管理
- [ ] 默认个人空间（注册时自动创建）
- [ ] 创建新空间
- [ ] 空间列表展示
- [ ] 空间切换
- [ ] 空间设置
  - [ ] 空间名称/描述修改
  - [ ] 空间删除

### 2.4 成员管理
- [ ] 邀请成员加入空间
- [ ] 成员角色管理
  - [ ] owner: 所有权限
  - [ ] admin: 管理成员和工作流
  - [ ] member: 编辑工作流
  - [ ] viewer: 只读访问
- [ ] 移除成员
- [ ] 成员列表展示

### 2.5 权限控制
- [ ] API 层权限验证
- [ ] 前端路由权限控制
- [ ] 工作流操作权限
  - [ ] 创建/编辑/删除
  - [ ] 执行
  - [ ] 查看

---

## 三、工作流状态管理 (低优先级)

### 3.1 工作流状态
- [ ] 状态定义
  - draft: 草稿
  - published: 已发布
  - archived: 已归档
- [ ] 状态切换
- [ ] 状态图标显示

### 3.2 发布功能
- [ ] 发布工作流
- [ ] 发布前验证
  - [ ] 节点配置完整性
  - [ ] 连线完整性
  - [ ] 入口点设置
- [ ] 发布说明

---

## 四、其他功能优化

### 4.1 快捷键系统
- [ ] Ctrl/Cmd + S: 保存
- [ ] Ctrl/Cmd + Z: 撤销
- [ ] Ctrl/Cmd + Y: 重做
- [ ] Ctrl/Cmd + C: 复制节点
- [ ] Ctrl/Cmd + V: 粘贴节点
- [ ] Delete/Backspace: 删除选中项
- [ ] Ctrl/Cmd + A: 全选

### 4.2 节点增强
- [ ] 条件分支多出口支持
- [ ] 节点搜索功能
- [ ] 节点注释
- [ ] 节点分组

### 4.3 变量系统
- [ ] 变量提示 `{{variable}}`
- [ ] 变量验证
- [ ] 变量面板

### 4.4 导入导出
- [ ] JSON 导入
- [ ] JSON 导出
- [ ] 工作流模板

---

## 五、技术债务

### 5.1 前端
- [ ] 添加单元测试
- [ ] 添加 E2E 测试
- [ ] 性能优化
  - [ ] 虚拟滚动（大量节点）
  - [ ] 懒加载

### 5.2 后端
- [ ] 添加单元测试
- [ ] API 文档（Swagger）
- [ ] 日志系统完善
- [ ] 错误处理统一

---

## 进度追踪

| 功能模块 | 优先级 | 状态 | 预计完成 |
|---------|-------|------|---------|
| 执行面板 | 高 | 待开始 | - |
| 执行结果展示 | 高 | 待开始 | - |
| 用户认证 | 中 | 待开始 | - |
| 空间管理 | 中 | 待开始 | - |
| 权限控制 | 中 | 待开始 | - |
| 工作流状态 | 低 | 待开始 | - |

---

*最后更新: 2026-03-16*
