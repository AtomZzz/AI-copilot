# Bank AI Assistant - 第二阶段实施指南

> 本文档指导你如何在 IDEA 中打开和运行项目

---

## 第一步：在 IDEA 中打开项目

1. **启动 IntelliJ IDEA Ultimate**
2. **File → Open**
3. **选择目录**：`C:\atom\data\IdeaProjects\AI-copilot\bank-ai-assistant`
4. **等待 Maven 导入完成**（右下角会显示进度）

---

## 第二步：配置 .env 文件加载

### 方式 A：使用 EnvFile 插件（推荐）

1. **安装插件**：
   - File → Settings → Plugins
   - 搜索 "EnvFile"
   - 安装并重启 IDEA

2. **配置 Run Configuration**：
   - Run → Edit Configurations
   - 找到 "AssistantApplication"
   - 勾选 "Enable EnvFile"
   - 添加 `.env` 文件路径

### 方式 B：手动设置环境变量

1. **Run → Edit Configurations**
2. **Environment variables** 中添加：
   ```
   DASHSCOPE_API_KEY=sk-892bf6a65ad244f49126a27a9421ccf8
   ```

---

## 第三步：验证数据库连接

确保 PostgreSQL 和 Redis 服务正在运行：

```bash
# 测试 PostgreSQL
psql -h 192.168.179.128 -U root -d bank_ai_assistant

# 测试 Redis
redis-cli -h 192.168.179.128 ping
```

如果连接失败，请检查：
1. 服务器防火墙是否开放端口
2. PostgreSQL/Redis 服务是否启动
3. 用户名密码是否正确

---

## 第四步：运行项目

1. **找到主类**：`src/main/java/com/bank/assistant/AssistantApplication.java`
2. **右键 → Run 'AssistantApplication'**
3. **观察控制台输出**，确认启动成功

预期输出：
```
Started AssistantApplication in X.XXX seconds
```

---

## 第五步：后续步骤

项目骨架已创建完成，接下来我会带你逐步实现：

1. ✅ 项目初始化（已完成）
2. ⏳ 依赖配置（pom.xml 已生成，Maven 会自动下载）
3. ⏳ 环境配置（application.yml + .env 已生成）
4.  基础设施层（PostgreSQL、Redis 连接）
5. 🔜 Tool Runtime 基础框架
6. 🔜 第一个 Tool 实现
7. 🔜 AI Runtime 基础框架
8. 🔜 API 层
9. 🔜 端到端测试

---

## 常见问题

### Q1: Maven 依赖下载失败？

**A:** 
1. 检查网络连接
2. 尝试使用国内 Maven 镜像（阿里云）
3. File → Settings → Build, Execution, Deployment → Build Tools → Maven → User settings file
4. 配置 `settings.xml` 添加阿里云镜像

### Q2: 找不到 DASHSCOPE_API_KEY？

**A:**
1. 确认 `.env` 文件存在且内容正确
2. 确认已安装 EnvFile 插件或在 Run Configuration 中设置了环境变量
3. 重启 IDEA

### Q3: 数据库连接失败？

**A:**
1. 确认 PostgreSQL 服务正在运行
2. 确认数据库 `bank_ai_assistant` 已创建
3. 确认用户名密码正确
4. 检查防火墙设置

---

## 下一步

请在 IDEA 中打开项目，确认可以正常启动后，回复 **"项目已打开"**，我会继续带你实现 Tool Runtime 和 AI Runtime。

如果遇到任何问题，随时告诉我！
