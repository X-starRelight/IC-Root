package com.tt23xrstudio.icexample;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.tt23xrstudio.icroot.ICFeedback;
import com.tt23xrstudio.icroot.ICMod;
import com.tt23xrstudio.icroot.ICSeries;

import net.fabricmc.api.ModInitializer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IC 示例 Mod。
 * <p>
 * 演示如何在初始化阶段通过 {@link ICSeries} 注册到 ic-root，
 * 并用 {@code RegMod} lambda 重载挂载自己的命令树，以及反馈开关的两层用法。
 * 注册完成后可在游戏中使用：
 * <ul>
 *   <li>{@code /ic check "example"} → Success 消息，返回 1</li>
 *   <li>{@code /ic list} → Success 消息（含名单），返回 1</li>
 *   <li>{@code /ic run example hello} → 问候 + {@code [IC-Root] Return 1}</li>
 *   <li>{@code /ic run example add 2 3} → {@code [IC-Root] Return 5}</li>
 *   <li>{@code /ic run example quiet} → 只执行命令本体，永不发 Return 反馈</li>
 * </ul>
 */
public class ICExampleMod implements ModInitializer {
    public static final String MOD_ID = "ic-root-example";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("IC Root Example 初始化中...");

        // 第一步：注册到 IC，拿到句柄（必须在命令树构建/注册表冻结之前）。
        // 第二个参数为全局 run 反馈开关：控制该 Mod 下全部子命令，
        // 注册后也可通过 mod.runFeedback(boolean) 动态开关。
        ICMod mod = ICSeries.register("example", true);

        // 动态开关演示（当前为开启，等价于 register("example", true)）：
        // mod.runFeedback(false);  // 关闭后谁都不可发送 run 反馈
        // mod.runFeedback(true);   // 重新开启

        // 第二步：用 lambda 重载注册命令树（与原版 Fabric 写法一致）
        mod.RegMod(dispatcher -> {
            // /ic run example hello
            // 未用 ICFeedback 包装 = 子命令级默认 true，反馈跟随全局开关
            dispatcher.register(Commands.literal("hello").executes(context -> {
                context.getSource().sendSuccess(
                        () -> Component.literal("Hello from IC Root Example!"), false);
                return 1;
            }));

            // /ic run example add <a> <b> → 返回 a + b
            // 演示 [IC-Root] Return {Return} 反馈（全局开 + 未标记 → 发送）
            dispatcher.register(Commands.literal("add")
                    .then(Commands.argument("a", IntegerArgumentType.integer())
                            .then(Commands.argument("b", IntegerArgumentType.integer())
                                    .executes(context -> {
                                        int a = IntegerArgumentType.getInteger(context, "a");
                                        int b = IntegerArgumentType.getInteger(context, "b");
                                        return a + b;
                                    }))));

            // /ic run example quiet
            // 子命令级 false：注册期定死，无论全局开关为何，永不发送 run 反馈
            dispatcher.register(Commands.literal("quiet")
                    .executes(ICFeedback.executes(false, context -> {
                        context.getSource().sendSuccess(
                                () -> Component.literal("Quiet command ran (no Return feedback)."),
                                false);
                        return 42;
                    })));

            // /ic run example explicit
            // 子命令级 true：显式声明（仍需全局为 true 才发送）
            dispatcher.register(Commands.literal("explicit")
                    .executes(ICFeedback.executes(true, context -> 7)));
        });

        LOGGER.info("IC Root Example 已注册到 IC，name = {}，全局 run 反馈 = {}",
                mod.name(), mod.isRunFeedbackEnabled());
    }
}
