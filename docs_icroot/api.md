# Java API 参考

包名：`com.tt23xrstudio.icroot`

| 类 | 职责 |
|---|---|
| `ICSeries` | 注册表与管理器（静态） |
| `ICMod` | 已注册 Mod 的句柄 |
| `ICFeedback` | 子命令级 run 反馈开关 |

---

## ICSeries

注册表与管理器，全部为静态方法。

```java
public final class ICSeries {
    public static ICMod register(String name);
    public static ICMod register(String name, boolean runFeedback);
    public static boolean isRegistered(String name);
    public static List<String> getRegisteredNames();
    public static ICMod get(String name);
}
```

### register(String name)

注册一个 Mod，全局 run 反馈默认开启（等价于 `register(name, true)`）。

- **参数** `name`：普通字符串，全表唯一（不是 Identifier）。
- **返回**：新创建的 `ICMod` 句柄。
- **仅允许在初始化阶段调用**；命令树构建完成（注册表冻结）后调用抛出 `IllegalStateException`。
- 重名抛出 `IllegalStateException`；空名抛出 `IllegalArgumentException`。

### register(String name, boolean runFeedback)

同上，并在注册时指定全局 run 反馈开关（详见 [feedback.md](feedback.md)）。

### isRegistered(String name)

查询是否已注册。只读，不改变状态。

### getRegisteredNames()

返回所有已注册 Mod 名的只读快照（按注册顺序，保证 `/ic run` 下字面量节点顺序稳定）。

### get(String name)

按名称获取 `ICMod`；未注册返回 `null`。

---

## ICMod

`ICSeries.register` 的返回值，代表一个已注册的 Mod 句柄。

```java
public final class ICMod {
    public String name();
    public void RegMod(CommandDispatcher<CommandSourceStack> dispatcher);
    public void RegMod(Consumer<CommandDispatcher<CommandSourceStack>> registrar);
    public ICMod runFeedback(boolean enabled);
    public boolean isRunFeedbackEnabled();
}
```

### name()

返回注册时使用的 Mod 名。

### RegMod — 两个重载

把该 Mod 的命令树交给 IC；最终挂载到 `/ic run <name>` 下，不污染全局命令空间，Mod 之间不冲突。

**重载 1：直接传入 dispatcher**

```java
ICMod mod = ICSeries.register("foo");

CommandDispatcher<CommandSourceStack> d = new CommandDispatcher<>();
d.register(Commands.literal("bar").executes(ctx -> 1));
mod.RegMod(d);
```

用户自行创建并注册命令树，整体交给 IC 保存。

**重载 2：传入 lambda（常用）**

```java
mod.RegMod(dispatcher -> {
    dispatcher.register(Commands.literal("bar")
            .then(Commands.argument("n", IntegerArgumentType.integer())
                    .executes(ctx -> {
                        int n = IntegerArgumentType.getInteger(ctx, "n");
                        return n;
                    })));
});
```

IC 提供（内部创建）子 dispatcher，用户在其中注册，写法与原版 Fabric 注册命令完全一致。

**共同语义**

- 两个重载写入同一字段，**后者覆盖前者**。
- 仅允许在注册表冻结前调用（初始化阶段）。
- 未调用 `RegMod`（或未注册任何命令）的 Mod 不会出现在 `/ic run` 下。

### runFeedback(boolean) / isRunFeedbackEnabled()

全局 run 反馈开关，控制该 Mod 下全部子命令；支持运行时动态修改。详见 [feedback.md](feedback.md)。

- `runFeedback(boolean enabled)`：设置并返回 `this`（可链式）。
- `isRunFeedbackEnabled()`：查询当前状态。

---

## ICFeedback

子命令级 run 反馈开关的包装器。

```java
public final class ICFeedback {
    public static Command<CommandSourceStack> executes(
            boolean runFeedback, Command<CommandSourceStack> command);
}
```

### executes(boolean runFeedback, Command command)

包装一个命令执行，带上子命令级 runFeedback 标记（**注册期定死，不可动态修改**）。

- **参数 1** `runFeedback`：子命令级开关。`false` = 无论全局为何永不发送；`true` = 显式允许（仍需全局为 `true`）。
- **参数 2** `command`：原始命令执行体。
- **返回**：携带标记的 `Command`，直接传给 Brigadier 的 `executes(...)`。

未包装的命令视为 `true`（跟随全局）。

```java
mod.RegMod(d -> {
    // 默认：跟随全局
    d.register(Commands.literal("normal").executes(ctx -> 1));

    // 永不发送 run 反馈
    d.register(Commands.literal("quiet")
            .executes(ICFeedback.executes(false, ctx -> 42)));
});
```

---

## 生命周期

```text
1. 各 Mod 初始化阶段（onInitialize）
   ├── ICSeries.register(name[, runFeedback])   → 拿到 ICMod
   └── ICMod.RegMod(...)                        → 挂载命令树数据
                 │
2. 服务器启动，Fabric 触发 CommandRegistrationCallback
   ├── IC 构建 /ic 命令树（check / list / run）
   ├── 各 ICMod 的命令树挂载到 /ic run <name> 下
   └── ICSeries.freeze()                        → 注册表冻结
                 │
3. 运行时
   ├── register / RegMod 一律抛 IllegalStateException
   ├── mod.runFeedback(...) 仍可动态调用（只影响反馈，不影响命令树）
   └── 命令执行、补全、返回值正常工作
```

**为什么冻结**：客户端补全与服务端命令树必须一致；与 Fabric/原版命令注册机制一致，不支持运行时动态替换命令。

---

## 进阶示例

```java
public class AdvancedMod implements ModInitializer {
    @Override
    public void onInitialize() {
        // 注册时关闭全局反馈（适合纯自动化 Mod）
        ICMod auto = ICSeries.register("auto", false);
        auto.RegMod(d -> {
            d.register(Commands.literal("status").executes(ctx -> 5));
        });

        // 默认全局开，内部命令单独静默
        ICMod mixed = ICSeries.register("mixed");
        mixed.RegMod(d -> {
            d.register(Commands.literal("public").executes(ctx -> 1));
            d.register(Commands.literal("internal")
                    .executes(ICFeedback.executes(false, ctx -> 2)));
        });

        // 运行时动态切换全局反馈
        mixed.runFeedback(false);   // 关
        mixed.runFeedback(true);    // 开

        // 查询
        boolean ok = ICSeries.isRegistered("mixed");
        List<String> names = ICSeries.getRegisteredNames();
        ICMod ref = ICSeries.get("mixed");
    }
}
```

游戏内效果：

```text
/ic run mixed public   → [IC-Root] Return 1
/ic run mixed internal → 无 Return 消息（子命令级 false），返回 2
/ic run auto status    → 无 Return 消息（全局 false），返回 5
```
