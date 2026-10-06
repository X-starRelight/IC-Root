# 快速上手

本指南帮助你把 `ic-root` 接入自己的 Fabric Mod，注册第一条命令并在游戏内验证。

## 1. 环境要求

| 项 | 版本 |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | >= 0.19.5 |
| Fabric API | 0.160.0+26.2 |
| Java | 25 |

## 2. 引入依赖

### 2.1 本地构建引用（推荐用于同一仓库/本地开发）

先在 `ic-root` 项目根目录执行构建：

```bash
gradlew build
```

生成 `build/libs/ic-root-1.0.0.jar` 后，在你的 Mod 的 `build.gradle` 中引用：

```groovy
dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    implementation "net.fabricmc:fabric-loader:${project.loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"

    // 指向 ic-root 的构建产物，按你的目录结构调整相对路径
    implementation files("../IC-Root/build/libs/ic-root-0.1.0.jar")
}
```

> **重要：不要使用 `modImplementation`**
> Minecraft 26.2 是非混淆版本，Loom（`net.fabricmc.fabric-loom`）不做 remap，
> `modImplementation` 配置**不会被创建**，使用会报错：
> `Configuration with name 'modImplementation' not found`。
> 直接用普通 `implementation files(...)` 即可。

### 2.2 声明 Mod 依赖

在你的 `fabric.mod.json` 中声明硬依赖，缺失 ic-root 时加载器会直接报错而不是运行时崩溃：

```json
{
    "depends": {
        "fabricloader": ">=0.19.5",
        "minecraft": "~26.2",
        "java": ">=25",
        "fabric-api": "*",
        "ic-root": "*"
    }
}
```

## 3. 注册命令（三步）

在你的 `ModInitializer.onInitialize()` 中完成，**必须在命令树构建（服务器启动）之前**：

```java
package com.example.mymod;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.tt23xrstudio.icroot.ICMod;
import com.tt23xrstudio.icroot.ICSeries;

import net.fabricmc.api.ModInitializer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class MyMod implements ModInitializer {
    @Override
    public void onInitialize() {
        // 第 1 步：注册到 IC，拿到句柄（name 全表唯一，默认全局反馈开）
        ICMod mod = ICSeries.register("mymod");

        // 第 2 步：注册命令树，写法与原版 Fabric 命令完全一致
        mod.RegMod(dispatcher -> {
            // /ic run mymod ping
            dispatcher.register(Commands.literal("ping").executes(context -> {
                context.getSource().sendSuccess(
                        () -> Component.literal("Pong!"), false);
                return 1;
            }));

            // /ic run mymod add <a> <b> → 返回 a + b
            dispatcher.register(Commands.literal("add")
                    .then(Commands.argument("a", IntegerArgumentType.integer())
                            .then(Commands.argument("b", IntegerArgumentType.integer())
                                    .executes(context -> {
                                        int a = IntegerArgumentType.getInteger(context, "a");
                                        int b = IntegerArgumentType.getInteger(context, "b");
                                        return a + b;
                                    }))));
        });
    }
}
```

第 3 步无需代码：服务器启动时 ic-root 会自动把你的命令树挂载到 `/ic run mymod` 下并冻结注册表。

## 4. 游戏内验证

需要 OP 或控制台权限（`Permissions.COMMANDS_MODERATOR`）。

| 命令 | 预期消息 | 返回值 |
|---|---|---|
| `/ic check "mymod"` | `[IC-Root] Find the Mod with ID mymod.`（普通色） | 1 |
| `/ic check "nope"` | `[IC-Root] Mod with ID nope not found.`（红色） | 0 |
| `/ic list` | `[IC-Root] You have installed 1 mods that depend on IC-Root.` ↵ `mymod` | 1 |
| `/ic run mymod ping` | `Pong!` ↵ `[IC-Root] Return 1` | 1 |
| `/ic run mymod add 2 3` | `[IC-Root] Return 5` | 5 |

补全：`/ic check ` 与 `/ic run ` 后按 Tab，会列出所有已注册 Mod 名。

## 5. 下一步

- 命令完整语义与消息文案：[commands.md](commands.md)
- 控制 run 反馈的发送（两层开关）：[feedback.md](feedback.md)
- API 方法签名与生命周期：[api.md](api.md)
- 遇到问题：[faq.md](faq.md)
