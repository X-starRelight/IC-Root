# IC-Root 使用文档

Improved Commands - Root（IC-Root）是一个 Fabric Mod，为 MC Mod 生态提供统一的命令入口 `/ic`：各 Mod 把自己的命令注册到 IC，通过 `/ic run <name>` 统一调用，并附带注册表查询、返回值驱动的自动化支持。

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

## 特性

- **统一入口**：所有 Mod 命令挂载到 `/ic run <name>` 下，不污染全局命令空间，Mod 间不冲突
- **注册表**：`/ic check` 查询（返回 1/0）、`/ic list` 列出（返回数量），支持 Tab 补全
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

## 最小示例

```java
ICMod mod = ICSeries.register("mymod");

mod.RegMod(dispatcher -> {
    dispatcher.register(Commands.literal("ping").executes(ctx -> 1));
});
```

游戏内：`/ic run mymod ping` → `[IC-Root] Return 1`

## 文档导航

| 文档 | 内容 |
|---|---|
| [getting-started.md](getting-started.md) | **从零接入**：环境要求、引入依赖、注册第一条命令、游戏内验证 |
| [commands.md](commands.md) | **命令参考**：`check` / `list` / `run` 的语义、消息文案、返回值、权限 |
| [feedback.md](feedback.md) | **反馈开关**：全局 + 子命令级两层规则、真值表、典型用法 |
| [api.md](api.md) | **API 参考**：`ICSeries` / `ICMod` / `ICFeedback` 签名、生命周期、进阶示例 |
| [faq.md](faq.md) | **常见问题**：注册冻结、modImplementation 报错、无输出排查、递归超限 |

## 阅读路径

- **第一次接入**：[getting-started.md](getting-started.md) → [commands.md](commands.md)
- **控制反馈消息**：[feedback.md](feedback.md)
- **查阅 API**：[api.md](api.md)
- **遇到报错/无输出**：[faq.md](faq.md)

## 项目结构

```text
IC-Root/
├── 设计.md                # 内部设计文档
├── docs_icroot/           # 本文档目录
│   ├── README.md          # 你在这里
│   ├── getting-started.md
│   ├── commands.md
│   ├── feedback.md
│   ├── api.md
│   └── faq.md
├── src/main/java/com/tt23xrstudio/icroot/
│   ├── ICRootMod.java     # 入口：构建 /ic 命令树
│   ├── ICSeries.java      # 注册表
│   ├── ICMod.java         # Mod 句柄
│   └── ICFeedback.java    # 子命令级反馈开关
└── IC-Root-Example/       # 示例 Mod（演示完整接入）
```
