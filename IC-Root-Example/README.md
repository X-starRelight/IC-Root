# IC Root Example

演示 Mod：展示其他 Mod 如何调用 `ic-root` 的 Java API 注册命令，以及反馈开关的两层用法。

## 前置

1. 先在父目录 `IC-Root` 执行 `gradlew build`，生成 `build/libs/ic-root-1.0.0.jar`
2. 本项目通过 `implementation files("../build/libs/ic-root-1.0.0.jar")` 引用
   （MC 26.2 为非混淆版本，Loom 不 remap，无需 `modImplementation`）

## 构建

```bash
gradlew build
```

## 注册示例

```java
// 全局 run 反馈开关（默认 true，可注册后再用 mod.runFeedback(boolean) 动态改）
ICMod mod = ICSeries.register("example", true);

mod.RegMod(dispatcher -> {
    // 未包装 = 子命令级默认 true，反馈跟随全局
    dispatcher.register(Commands.literal("hello").executes(ctx -> 1));

    // 子命令级 false：注册期定死，永不发送 run 反馈
    dispatcher.register(Commands.literal("quiet")
            .executes(ICFeedback.executes(false, ctx -> 42)));
});
```

反馈生效规则：`实际发送 = 全局开关 && 子命令级开关`。

## 游戏内验证

| 命令 | 预期消息 | 返回值 |
|---|---|---|
| `/ic check "example"` | `[IC-Root] Find the Mod with ID example.`（Success） | 1 |
| `/ic check "nope"` | `[IC-Root] Mod with ID nope not found.`（Failure，红色） | 0 |
| `/ic list` | `[IC-Root] You have installed 1 mods...` ↵ `example`（Success） | 1 |
| `/ic run example hello` | 问候 + `[IC-Root] Return 1` | 1 |
| `/ic run example add 2 3` | `[IC-Root] Return 5` | 5 |
| `/ic run example quiet` | 仅命令本体消息，**无** Return 反馈 | 42 |
| `/ic run example explicit` | `[IC-Root] Return 7` | 7 |

需要 OP / 控制台权限（`Permissions.COMMANDS_MODERATOR`）。
