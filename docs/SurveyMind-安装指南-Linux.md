# SurveyMind 安装指南（Linux）

## 系统要求

- **操作系统**: 64 位 Linux（CentOS 7+/Ubuntu 18.04+/麒麟/统信 等主流发行版）
- **CPU 架构**: x86_64 (Intel/AMD) 或 aarch64 (ARM，如鲲鹏/飞腾)
- **内存**: 4GB 以上（推荐 8GB）
- **磁盘**: 1GB 可用空间
- **网络**: 需要访问 LLM API 服务（如通义千问、OpenAI 等）
- **端口**: 18088（请确保未被占用）

> 安装包已内置 Java 运行环境（JRE 21），**无需在系统中单独安装 Java**。

## 选择正确的安装包（重要）

安装包内置的 JRE 与 CPU 架构绑定，**必须与目标服务器架构一致**，否则会报 `Exec format error` 无法启动。

先在目标服务器上执行：

```bash
uname -m
```

| `uname -m` 输出 | 选用安装包 |
|---|---|
| `x86_64` | `SurveyMind-Trial-Linux-x64-v1.3.0.tar.gz` |
| `aarch64` / `arm64` | `SurveyMind-Trial-Linux-aarch64-v1.3.0.tar.gz` |

## 安装步骤

> 下文以 x64 包为例，aarch64 包将文件名中的 `x64` 替换为 `aarch64` 即可，步骤完全相同。

### 1. 解压安装包

```bash
tar xzf SurveyMind-Trial-Linux-x64-v1.3.0.tar.gz
cd SurveyMind-Trial-Linux-x64-v1.3.0
```

解压后目录结构：
```
SurveyMind-Trial-Linux-x64-v1.3.0/
├── jre/                    ← Java 运行环境（已内置，无需单独安装）
├── data/                   ← 数据目录
├── license.lic             ← 试用授权文件
├── start.sh                ← 启动脚本
├── stop.sh                 ← 停止脚本
└── surveymind-server.jar   ← 应用程序
```

### 2. 启动服务

前台启动（控制台可见日志，`Ctrl + C` 停止）：

```bash
./start.sh
```

后台常驻运行：

```bash
nohup ./start.sh > server.log 2>&1 &
```

等待日志出现如下提示即启动成功：

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

```bash
./stop.sh
```

（前台运行时也可直接在控制台按 `Ctrl + C`）

## 试用说明

- 本安装包为 **试用版**，有效期见 `license.lic` 中的授权信息
- 试用到期前 7 天，系统顶部会显示到期提醒
- 到期后系统将停止服务，已有数据不会丢失
- 如需续期或购买正式版，请联系供应商

## 常见问题

### Q: 启动后无法访问 http://localhost:18088？

1. 检查控制台 / `server.log` 是否有报错信息
2. 确认端口 18088 未被其他程序占用：`ss -lntp | grep 18088`
3. 检查防火墙（firewalld / iptables）是否放行了 18088 端口

### Q: 提示"试用已到期"？

请联系供应商获取新的 `license.lic` 文件，替换安装目录中的旧文件，然后重启服务。

### Q: 想从其他电脑访问？

默认监听所有网卡。局域网内其他电脑使用本机 IP 访问，例如 `http://192.168.1.100:18088`，并确保防火墙放行 18088 端口：

```bash
# firewalld 示例
firewall-cmd --add-port=18088/tcp --permanent && firewall-cmd --reload
```

### Q: 数据存储在哪里？

所有数据存储在安装目录的 `data/` 文件夹中。备份时复制整个 `data/` 目录即可。

### Q: 提示 `Permission denied` 无法执行脚本？

赋予执行权限：

```bash
chmod +x start.sh stop.sh
```
