# Improved Commands - Root

![Logo](./icon.png)

Improved Commands - Root（IC-Root）是一个 Fabric Mod，为 Improved Commands 系列生态提供统一的命令入口 `/ic`：各 Mod 把自己的命令注册到 IC，通过 `/ic run <name>` 统一调用，并附带注册表查询、返回值驱动的自动化支持。

## 基本信息

| 项 | 值 |
|---|---|
| Mod ID | `ic-root` |
| 包名 | `com.tt23xrstudio.icroot` |
| 开发者 | tt23xrstudio |
| Minecraft | 26.2 |
| Fabric Loader | >= 0.19.0 |
| Fabric API | 0.160.0+26.2 |
| Java | 25 |
| 构建 | Gradle + fabric-loom |

## 特性

- **统一入口**：所有 Mod 命令挂载到 `/ic run <name>` 下，不污染全局命令空间，Mod 间不冲突
- **注册表查询**：`/ic check` 查询（返回 1/0）、`/ic list` 列出（返回数量），支持 Tab 补全
- **返回值驱动**：所有命令返回值可供命令方块、函数、自动化读取
- **两层反馈开关**：全局（运行时可动态改）+ 子命令级（注册期定死），按需控制 run 反馈消息
- **递归保护**：`/ic run` 嵌套深度限制 8 层，超限拦截并提示
- **权限控制**：限制为 OP / 控制台 / 命令方块等具备 moderator 权限的执行源，`run` 不提升权限
- **生命周期冻结**：命令树一次性构建，注册表冻结后不可增改（与 Fabric/原版机制一致）

## 效果速览

```text
/ic check example
# [IC-Root] Find the Mod with ID example.                （返回 1）

/ic list
# [IC-Root] You have installed 2 mods that depend on IC-Root.
# example, foo                                            （返回 2）

/ic run example add 2 3
# [IC-Root] Return 5                                      （返回 5）
```

## 快速上手

### 环境要求

| 项 | 版本 |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | >= 0.19.0 |
| Fabric API | 0.160.0+26.2 |
| Java | 25 |

### 构建

```bash
gradlew build
```

### 引入依赖

先在本项目根目录构建，再在你的 Mod 的 `build.gradle` 中用普通 `implementation files(...)` 引用 `build/libs/` 下的 jar：

```groovy
dependencies {
    implementation files("../IC-Root/build/libs/<ic-root 的 jar>")
}
```

> **注意：不要使用 `modImplementation`** —— Minecraft 26.2 为非混淆版本，Loom 不做 remap，`modImplementation` 配置不存在会直接报错。完整依赖配置见 [getting-started.md](docs_icroot/getting-started.md)。

### 注册你的命令（三步）

在 `ModInitializer.onInitialize()` 中完成，**必须在命令树构建（服务器启动）之前**：

```java
// 第 1 步：注册到 IC，拿到句柄（name 全表唯一）
ICMod mod = ICSeries.register("mymod");

// 第 2 步：注册命令树，写法与原版 Fabric 命令一致
mod.RegMod(dispatcher -> {
    dispatcher.register(Commands.literal("ping").executes(ctx -> 1));
});
```

第 3 步无需代码：服务器启动时 IC 自动把你的命令树挂载到 `/ic run mymod` 下并冻结注册表。

游戏内验证（需 OP 或控制台权限）：`/ic run mymod ping` → `[IC-Root] Return 1`。

完整接入流程见 [getting-started.md](docs_icroot/getting-started.md)。

## 项目结构

```text
IC-Root/
├── src/main/java/com/tt23xrstudio/icroot/
│   ├── ICRootMod.java     # 入口：构建 /ic 命令树并冻结注册表
│   ├── ICSeries.java      # 注册表：register / isRegistered / 冻结
│   ├── ICMod.java         # Mod 句柄：RegMod 注册命令树、全局反馈开关
│   └── ICFeedback.java    # 子命令级反馈开关（注册期定死）
├── docs_icroot/           # 使用文档
├── IC-Root-Example/       # 示例 Mod（演示完整接入）
├── build.gradle           # Fabric Loom 构建配置
└── .github/workflows/     # CI 自动构建
```

## 文档导航

| 文档 | 内容 |
|---|---|
| [docs_icroot/getting-started.md](docs_icroot/getting-started.md) | 从零接入：环境、依赖、注册第一条命令、游戏内验证 |
| [docs_icroot/commands.md](docs_icroot/commands.md) | 命令参考：check / list / run 的语义、消息、返回值、权限 |
| [docs_icroot/feedback.md](docs_icroot/feedback.md) | 反馈开关：全局 + 子命令级两层规则与真值表 |
| [docs_icroot/api.md](docs_icroot/api.md) | API 参考：ICSeries / ICMod / ICFeedback 签名与生命周期 |
| [docs_icroot/faq.md](docs_icroot/faq.md) | 常见问题：注册冻结、modImplementation 报错、无输出排查 |
| [IC-Root-Example/README.md](IC-Root-Example/README.md) | 示例 Mod 的构建与游戏内验证 |

## 许可证

本项目采用 [GNU GPL-3.0](LICENSE) 许可证。
