# 常见问题（FAQ）

## 注册相关

### 运行时调用 register 抛出「IC 注册表已冻结」？

注册表在服务器启动、命令树构建完成后冻结（与 Fabric/原版命令注册机制一致，不支持运行时动态替换）。

- `ICSeries.register(...)` 和 `ICMod.RegMod(...)` **只能在 Mod 初始化阶段**（`onInitialize`）调用。
- 冻结后调用抛出 `IllegalStateException: IC 注册表已冻结...`。
- 这是为了保证客户端补全与服务端命令树一致——运行时改命令树会导致客户端提示与实际执行不符。

### 调用 register 抛出「Mod 名重复注册」？

`name` 在全表唯一。检查是否有两个 Mod 用了同一个名字，或同一 Mod 重复注册。

### 调用了 register 但 /ic run 下没有我的命令？

确认调用了 `ICMod.RegMod(...)` 且至少注册了一个命令节点。未调用 `RegMod`（或 dispatcher 为空）的 Mod 不会挂载任何子节点。

注意：`RegMod` 也必须在注册表冻结前（初始化阶段）完成。

### RegMod 的两个重载怎么选？

- **lambda 重载**（`RegMod(dispatcher -> {...})`）：常用，IC 内部创建子 dispatcher，写法与原版 Fabric 一致，推荐。
- **dispatcher 重载**（`RegMod(d)`）：已有现成 dispatcher 时使用。

两者写入同一字段，**后者覆盖前者**，一个 Mod 只用一种即可。

---

## 依赖与构建

### 构建报错 `Configuration with name 'modImplementation' not found`

Minecraft 26.2 是**非混淆版本**，Loom（`net.fabricmc.fabric-loom`）不做 remap，不会创建 `modImplementation` 等 mod* remap 配置。

解决：改用普通文件依赖：

```groovy
dependencies {
    implementation files("../IC-Root/build/libs/ic-root-0.1.0.jar")
}
```

### 依赖 ic-root 的构建顺序

```bash
# 1. 先构建 ic-root
cd IC-Root && gradlew build

# 2. 再构建你的 Mod / 示例
cd IC-Root-Example && gradlew build
```

`implementation files(...)` 指向的是 `ic-root` 的构建产物，产物不存在会编译失败。

---

## 命令与消息

### /ic list / check 没有任何输出？

ic-root 已为所有子命令添加聊天反馈（见 [commands.md](commands.md)）。若仍无输出，按顺序排查：

1. **权限**：`/ic` 整棵树要求 `Permissions.COMMANDS_MODERATOR`（OP/控制台）。权限不足时命令**不可见也不可执行**，补全也不会出现。先 `/op 你的名字` 或在控制台测试。
2. **Mod 是否加载**：日志中搜索 `IC 命令树已构建`，确认 ic-root 已加载。
3. **旧版本 jar**：确认游戏加载的是新构建的 `ic-root-1.0.0.jar`。

### 递归超限提示是什么？

`[IC-Root] Recursion depth limit (8) reached. You cannot do this.`

这是**递归保护**：若某 Mod 的命令内部又调用 `/ic run`（一层套一层），嵌套深度达到 8 时 IC 会拦截本次执行、返回 0 并显示该红色提示，防止无限递归卡死服务器。

- 阈值 `MAX_RUN_DEPTH = 8`，定义在 `ICRootMod` 中的常量，可按需修改后重新构建。
- 该提示受反馈开关控制（见 [feedback.md](feedback.md)）。

### Return {Return} 消息不发送？

`/ic run` 的反馈受两层开关控制：`实际发送 = 全局开关 && 子命令级开关`。

- 全局开关：`ICSeries.register(name, false)` 或 `mod.runFeedback(false)`。
- 子命令级：`ICFeedback.executes(false, ...)` 包装的命令永不发送。

详见 [feedback.md](feedback.md)。

### run 命令权限会提升吗？

不会。`/ic run` 保留原执行源，不提升权限；目标命令自身的权限检查仍然生效。例如非 OP 无法通过 `/ic run` 绕过目标命令的权限限制（且 `/ic` 根节点本身就要求 moderator 权限）。

---

## 返回值

### 返回值在哪里看？

命令返回值不是聊天消息，供以下场景读取：

- **命令方块**：比较/存储命令结果
- **函数（function）**：`execute if score` 等配合
- **自动化/脚本**：通过 RCON 或命令执行 API 读取返回值

`/ic check` 专为自动化设计：已注册 `1`、未注册 `0`。
