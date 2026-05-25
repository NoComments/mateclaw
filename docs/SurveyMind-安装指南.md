# SurveyMind 安装指南

## 系统要求

- **操作系统**: Windows 10 / 11（64 位）
- **内存**: 4GB 以上（推荐 8GB）
- **磁盘**: 1GB 可用空间
- **网络**: 需要访问 LLM API 服务（如通义千问、OpenAI 等）
- **端口**: 18088（请确保未被占用）

## 安装步骤

### 1. 解压安装包

将 `SurveyMind-Trial-v1.3.0.zip` 解压到任意目录（建议路径不包含中文和空格）。

解压后目录结构：
```
SurveyMind-Trial-v1.3.0/
├── jre/                    ← Java 运行环境（已内置，无需单独安装）
├── data/                   ← 数据目录
├── license.lic             ← 试用授权文件
├── start.bat               ← 启动脚本
├── stop.bat                ← 停止脚本
└── surveymind-server.jar   ← 应用程序
```

### 2. 启动服务

双击 `start.bat`，等待控制台出现如下提示即启动成功：

```
Started MateClawApplication in X.XX seconds
```

> **注意**: 首次启动需要初始化数据库，耗时约 30 秒，请耐心等待。

### 3. 访问系统

打开浏览器（推荐 Chrome / Edge），访问：

```
http://localhost:18088
```

### 4. 登录

使用默认管理员账号登录：

- **用户名**: `admin`
- **密码**: `admin123`

> 建议首次登录后修改默认密码。

### 5. 配置 AI 模型

登录后，进入 **设置 → 模型管理**，添加至少一个 LLM 服务商：

| 服务商 | 需要的凭证 | 获取方式 |
|--------|-----------|---------|
| 通义千问 (DashScope) | API Key | https://dashscope.console.aliyun.com/ |
| OpenAI | API Key | https://platform.openai.com/api-keys |
| 深度求索 (DeepSeek) | API Key | https://platform.deepseek.com/ |

配置完成后即可开始使用 AI 助手功能。

## 停止服务

方式一：在命令行窗口按 `Ctrl + C`

方式二：双击 `stop.bat`

## 试用说明

- 本安装包为 **试用版**，有效期见 `license.lic` 中的授权信息
- 试用到期前 7 天，系统顶部会显示到期提醒
- 到期后系统将停止服务，已有数据不会丢失
- 如需续期或购买正式版，请联系供应商

## 常见问题

### Q: 启动后无法访问 http://localhost:18088？

1. 检查控制台是否有报错信息
2. 确认端口 18088 未被其他程序占用：在 CMD 中执行 `netstat -ano | findstr 18088`
3. 检查 Windows 防火墙是否阻止了访问

### Q: 提示"试用已到期"？

请联系供应商获取新的 `license.lic` 文件，替换安装目录中的旧文件，然后重启服务。

### Q: 想从其他电脑访问？

默认仅本机可访问。如需局域网内其他电脑访问，使用本机 IP 地址替代 `localhost`，例如 `http://192.168.1.100:18088`，并确保 Windows 防火墙放行 18088 端口。

### Q: 数据存储在哪里？

所有数据存储在安装目录的 `data/` 文件夹中。备份时复制整个 `data/` 目录即可。
