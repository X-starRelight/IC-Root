# 反馈开关

`/ic run` 的反馈消息（`[IC-Root] Return {Return}` 与递归超限提示）由**两层开关**控制。

## 生效规则

```text
实际发送 = 全局开关 && 子命令级开关
```

- **全局开关**：按 Mod 维度，控制该 Mod 下**全部**子命令；注册时可配置，运行时**可动态修改**。
- **子命令级开关**：按单条命令维度，注册命令时**定死**，**不可动态修改**。

> `check` 与 `list` 的消息**不受**反馈开关控制，始终发送。

## 真值表

| 全局开关 | 子命令级开关 | 实际发送 |
|---|---|---|
| `false` | 任意 | 否 |
| `true` | `false` | 否 |
| `true` | `true` | 是 |
| `true` | 未标记（默认） | 是 |

## 全局开关

### 注册时配置

```java
// 默认开启（等价于 register(name, true)）
ICMod mod = ICSeries.register("foo");

// 注册时直接关闭：该 Mod 全部子命令都不发 run 反馈
ICMod mod2 = ICSeries.register("bar", false);
```

### 运行时动态开关

```java
// 关闭后，该 Mod 谁都不可发送 run 反馈
mod.runFeedback(false);

// 重新开启（仍需子命令级开关为 true 或未标记）
mod.runFeedback(true);

// 查询当前状态
boolean enabled = mod.isRunFeedbackEnabled();
```

`runFeedback(boolean)` 返回 `this`，支持链式调用。开关字段为 `volatile`，支持运行时随时修改，立即对下一次命令执行生效。

### 语义

- 全局为 `false`：**谁都不可发送**（覆盖一切子命令级设置）。
- 全局为 `true`：子命令级为 `true`（或未标记）的命令可发送。

## 子命令级开关

注册命令时通过 `ICFeedback.executes(boolean, command)` 包装，第一个参数即子命令级开关：

```java
mod.RegMod(d -> {
    // 未包装 = 默认 true，反馈跟随全局开关
    d.register(Commands.literal("hello").executes(ctx -> {
        // ...
        return 1;
    }));

    // 子命令级 false：无论全局为何，永不发送 run 反馈
    d.register(Commands.literal("quiet").executes(ICFeedback.executes(false, ctx -> {
        // ...
        return 42;
    })));

    // 子命令级 true：显式声明（仍需全局为 true 才发送）
    d.register(Commands.literal("explicit").executes(ICFeedback.executes(true, ctx -> {
        // ...
        return 7;
    })));
});
```

### 语义

- 子命令级 `false`：**无论全局配置为什么，都不发送**。
- 子命令级 `true`：可以发送，但**仅在全局为 `true` 时**才实际发送。
- 未包装的命令视为 `true`（跟随全局）。
- 该开关在**注册期定死**，之后无法通过任何 API 修改。

## 典型用法

```java
// 场景 1：完全静默（自动化调用，不想刷聊天框）
ICMod auto = ICSeries.register("auto", false);          // 全局关

// 场景 2：大部分命令有反馈，个别内部命令静默
ICMod mixed = ICSeries.register("mixed");               // 全局开（默认）
mixed.RegMod(d -> {
    d.register(Commands.literal("status").executes(ctx -> 1));  // 跟随全局 → 发送
    d.register(Commands.literal("internal")
            .executes(ICFeedback.executes(false, ctx -> 1)));   // 永不发送
});

// 场景 3：白天开、晚上关（运行时动态控制）
mixed.runFeedback(isNightTime);   // 任意时机调用
```

## 递归超限提示

递归超限的 Failure 提示 `[IC-Root] Recursion depth limit (8) reached. You cannot do this.`
与 `Return {Return}` 使用**同一套**反馈开关判定——即也受两层开关控制。
若全局与子命令级均允许发送，深度达到 8 时会看到该红色提示并返回 `0`。
