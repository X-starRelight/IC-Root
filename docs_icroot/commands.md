# 命令参考

IC-Root 提供统一入口命令 `/ic`，包含三个子命令：`check`、`list`、`run`。

所有子命令都需要 **OP / 控制台 / 命令方块 / 具备 moderator 权限的函数**（`Permissions.COMMANDS_MODERATOR`）才能执行与补全。

---

## /ic check \<name\>

查询某个 Mod 是否已注册到 IC。

### 用法

```text
/ic check <name>
```

- `<name>` 为字符串参数（无特殊字符时可不加引号）。
- 支持 Tab 补全，候选为所有已注册 Mod 名。

### 行为

| 情况 | 消息 | 消息类型 | 返回值 |
|---|---|---|---|
| 已注册 | `[IC-Root] Find the Mod with ID {ID}.` | `sendSuccess` | `1` |
| 未注册 | `[IC-Root] Mod with ID {ID} not found.` | `sendFailure`（红色） | `0` |

### 示例

```text
/ic check example     → [IC-Root] Find the Mod with ID example.        返回 1
/ic check nope        → [IC-Root] Mod with ID nope not found.          返回 0
```

---

## /ic list

列出所有已注册到 IC 的 Mod。

### 用法

```text
/ic list
```

无参数，无需补全。

### 行为

| 情况 | 消息 | 消息类型 | 返回值 |
|---|---|---|---|
| 有注册 | 第 1 行：`[IC-Root] You have installed {X} mods that depend on IC-Root.`<br>第 2 行：Mod 名单，一行逗号分隔（如 `example, foo`） | `sendSuccess` | `X`（数量） |
| 无注册 | `[IC-Root] You haven't installed any mods that depend on IC-Root` | `sendFailure`（红色） | `0` |

### 示例

```text
/ic list
# [IC-Root] You have installed 2 mods that depend on IC-Root.
# example, foo
# 返回 2
```

完整名单也可通过 Java API `ICSeries.getRegisteredNames()` 程序化读取。

---

## /ic run \<name\> \<command\> \[args...\]

执行某个已注册 Mod 通过 `ICMod.RegMod` 注册的命令。

### 用法

```text
/ic run <name> <command> [args...]
```

- `<name>`：已注册的 Mod 名，字面量节点，Tab 自动补全。
- `<command>`：该 Mod 命令树中的字面量节点，由其自身提供补全。
- `[args...]`：目标命令的参数，由 Brigadier 正常解析（类型、补全、权限均与原版一致）。

### 行为

| 情况 | 消息 | 消息类型 | 返回值 |
|---|---|---|---|
| 目标命令执行完，且反馈允许 | `[IC-Root] Return {Return}`（{Return} 为目标命令返回值） | `sendSuccess` | 目标命令返回值（原样透传） |
| 递归超限（深度 >= 8） | `[IC-Root] Recursion depth limit (8) reached. You cannot do this.` | `sendFailure`（红色） | `0` |
| `/ic run <name>` 无后续命令 | 无消息 | — | `0` |

`Return {Return}` 消息受**反馈开关**控制，是否发送见 [feedback.md](feedback.md)；递归超限提示同样受该开关控制。

### 示例

```text
/ic run example hello        → 问候消息 + [IC-Root] Return 1       返回 1
/ic run example add 2 3      → [IC-Root] Return 5                  返回 5
/ic run example quiet        → 仅命令本体消息（子命令级静默）        返回 42
```

### 挂载机制说明

- 各 Mod 的命令注册在独立的子 dispatcher 上，**不会污染全局命令空间**，Mod 之间不会冲突。
- 命令树在服务器启动的命令注册回调中一次性挂载到 `/ic run <name>` 下，之后注册表冻结，运行时不可新增或替换（与 Fabric/原版命令注册机制一致）。

---

## 返回值汇总

返回值均为**命令返回值**，供命令方块、函数、宏或自动化调用读取：

| 命令 | 返回值 |
|---|---|
| `/ic check <name>` | 已注册 `1`，未注册 `0` |
| `/ic list` | 已注册数量（无注册为 `0`） |
| `/ic run <name> <command> [args...]` | 目标命令的返回值；递归超限 `0`；无子命令 `0` |

## 消息文案一览

| 消息 | 方法 |
|---|---|
| `[IC-Root] Find the Mod with ID {ID}.` | `sendSuccess` |
| `[IC-Root] Mod with ID {ID} not found.` | `sendFailure` |
| `[IC-Root] You have installed {X} mods that depend on IC-Root.` + 名单 | `sendSuccess` |
| `[IC-Root] You haven't installed any mods that depend on IC-Root` | `sendFailure` |
| `[IC-Root] Return {Return}` | `sendSuccess` |
| `[IC-Root] Recursion depth limit (8) reached. You cannot do this.` | `sendFailure` |
