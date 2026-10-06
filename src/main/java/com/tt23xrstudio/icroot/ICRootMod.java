package com.tt23xrstudio.icroot;

import java.lang.reflect.Field;
import java.util.List;
import java.util.StringJoiner;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IC Root 模入口。
 * 
 * 在 Fabric 命令注册回调中统一构建 {@code /ic} 命令树，
 * 构建完成后冻结 {@link ICSeries} 注册表（与原版/Fabric 注册机制一致，
 * 运行时不可动态替换）。
 */
public class ICRootMod implements ModInitializer {
    public static final String MOD_ID = "ic-root";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** /ic run 递归深度阈值，超过直接返回 0 */
    private static final int MAX_RUN_DEPTH = 8;
    /** 当前线程的 /ic run 调用深度 */
    private static final ThreadLocal<Integer> RUN_DEPTH = ThreadLocal.withInitial(() -> 0);
    /** Brigadier CommandNode#command 字段（protected，无 setter，反射写入） */
    private static final Field COMMAND_FIELD;

    static {
        try {
            COMMAND_FIELD = CommandNode.class.getDeclaredField("command");
            COMMAND_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public void onInitialize() {
        LOGGER.info("IC Root 初始化中...");
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(buildIcCommand());
            // 命令树构建完成，冻结注册表，此后禁止 register
            ICSeries.freeze();
            LOGGER.info("IC 命令树已构建，注册表已冻结，已注册 Mod 数: {}",
                    ICSeries.getRegisteredNames().size());
        });
    }

    /**
     * 构建完整 /ic 命令树。
     */
    private static LiteralArgumentBuilder<CommandSourceStack> buildIcCommand() {
        LiteralArgumentBuilder<CommandSourceStack> ic = Commands.literal("ic")
                // 权限：仅 OP / 控制台 / 命令方块等具备 moderator 权限的执行源
                .requires(source -> source.permissions()
                        .hasPermission(Permissions.COMMANDS_MODERATOR));

        ic.then(buildCheckCommand());
        ic.then(buildListCommand());
        ic.then(buildRunCommand());
        return ic;
    }

    /**
     * /ic check <name>：已注册返回 1，未注册返回 0。
     * 成功发送 Success 消息，失败发送 Failure 消息（红色）。
     */
    private static LiteralArgumentBuilder<CommandSourceStack> buildCheckCommand() {
        return Commands.literal("check")
                .then(Commands.argument("name", StringArgumentType.string())
                        // 补全：提供所有已注册 Mod 名
                        .suggests((context, builder) -> {
                            for (String registered : ICSeries.getRegisteredNames()) {
                                builder.suggest(registered);
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            if (ICSeries.isRegistered(name)) {
                                // 找到：Success 消息 + 返回 1
                                context.getSource().sendSuccess(
                                        () -> Component.literal(
                                                "[IC-Root] Find the Mod with ID " + name + "."),
                                        false);
                                return 1;
                            }
                            // 未找到：Failure 消息（红色）+ 返回 0
                            context.getSource().sendFailure(
                                    Component.literal(
                                            "[IC-Root] Mod with ID " + name + " not found."));
                            return 0;
                        }));
    }

    /**
     * /ic list：返回已注册 Mod 数量。
     * 有注册：Success 消息（数量 + 一行逗号分隔的名单）；
     * 无注册：Failure 消息（红色），返回 0。
     */
    private static LiteralArgumentBuilder<CommandSourceStack> buildListCommand() {
        return Commands.literal("list")
                .executes(context -> {
                    List<String> names = ICSeries.getRegisteredNames();
                    if (names.isEmpty()) {
                        // 无注册：Failure 消息（红色）+ 返回 0
                        context.getSource().sendFailure(Component.literal(
                                "[IC-Root] You haven't installed any mods that depend on IC-Root"));
                        return 0;
                    }
                    // 有注册：Success 消息，第 1 行数量，第 2 行逗号分隔名单
                    StringJoiner joiner = new StringJoiner(", ");
                    for (String name : names) {
                        joiner.add(name);
                    }
                    String message = "[IC-Root] You have installed " + names.size()
                            + " mods that depend on IC-Root.\n" + joiner;
                    context.getSource().sendSuccess(
                            () -> Component.literal(message), false);
                    return names.size();
                });
    }

    /**
     * /ic run <name> ...：每个已注册 ICMod 一个字面量子节点，
     * 下挂其通过 RegMod 注册的命令树；挂载前统一包装 executes
     * 以注入递归深度计数与 run 反馈。
     */
    private static LiteralArgumentBuilder<CommandSourceStack> buildRunCommand() {
        LiteralArgumentBuilder<CommandSourceStack> run = Commands.literal("run");
        boolean[] anyMounted = {false};

        for (String name : ICSeries.getRegisteredNames()) {
            ICMod mod = ICSeries.get(name);
            if (mod == null || mod.dispatcher() == null) {
                // 未调用 RegMod，无可挂载的命令树
                continue;
            }

            LiteralArgumentBuilder<CommandSourceStack> nameNode = Commands.literal(name);
            // 把子 dispatcher 根节点的 children 挂到 /ic run <name> 下，
            // 挂载前包装其中所有 executes，注入递归深度计数与 run 反馈
            for (CommandNode<CommandSourceStack> child : mod.dispatcher().getRoot().getChildren()) {
                wrapCommandsForRecursion(child, mod);
                nameNode.then(child);
            }

            // /ic run <name> 结尾无后续命令时的兜底：静默返回 0
            nameNode.executes(context -> 0);
            run.then(nameNode);
            anyMounted[0] = true;
        }

        // 尚未注册任何命令树时，/ic run 仍可解析但无子节点
        if (!anyMounted[0]) {
            run.executes(context -> 0);
        }

        return run;
    }

    /**
     * 递归包装：替换节点及其所有子孙节点上的 executes，
     * 在执行原命令前后维护 {@link #RUN_DEPTH} 计数，
     * 并在命令完成后按开关发送 run 反馈。
     * 
     * 幂等：{@code CommandRegistrationCallback} 在每次开世界/开服、每次
     * {@code /reload} 都会触发（{@code Commands} 重建），而 mod dispatcher 的
     * 节点是共享的长生命周期对象。已包装过的节点（{@link RunWrappedCommand}）
     * 直接跳过，防止包装层逐次叠加导致深度计数虚高、误报递归超限。
     * 
     * 反馈生效规则：实际发送 = 全局开关（{@link ICMod#isRunFeedbackEnabled()}）
     * && 子命令级开关（{@link ICFeedback.FlaggedCommand}，未包装默认 true）。
     * 
     * 递归场景：目标命令内部再次调用 {@code /ic run}，形成递归；
     * 深度超过 {@link #MAX_RUN_DEPTH} 时发送 Failure 提示并返回 0。
     *
     * @param node 待包装的命令节点
     * @param mod  该命令树所属的 ICMod（读取全局反馈开关）
     */
    private static void wrapCommandsForRecursion(CommandNode<CommandSourceStack> node, ICMod mod) {
        Command<CommandSourceStack> original = node.getCommand();
        // 幂等：已包装则跳过（仍递归 children——RegMod 不受冻结限制，
        // mod 可能在首次挂载后追加新节点，新节点仍需包装）
        if (original != null && !(original instanceof RunWrappedCommand)) {
            setCommand(node, new RunWrappedCommand(mod, original));
        }
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            wrapCommandsForRecursion(child, mod);
        }
    }

    /**
     * /ic run 深度护栏包装：执行原命令前后维护 {@link #RUN_DEPTH} 计数，
     * 超限拦截，并按反馈开关发送 run 反馈。
     * 
     * 以具名类存在，供 {@link #wrapCommandsForRecursion} 通过
     * {@code instanceof} 识别已包装节点，保证重复注册时包装幂等。
     */
    private static final class RunWrappedCommand implements Command<CommandSourceStack> {
        private final ICMod mod;
        /** 真正的原始命令（可能是 {@link ICFeedback.FlaggedCommand}） */
        private final Command<CommandSourceStack> original;

        private RunWrappedCommand(ICMod mod, Command<CommandSourceStack> original) {
            this.mod = mod;
            this.original = original;
        }

        @Override
        public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
            int depth = RUN_DEPTH.get();
            if (depth >= MAX_RUN_DEPTH) {
                // 递归超限：按反馈开关发送 Failure 提示（红色），返回 0
                if (isFeedbackAllowed(mod, original)) {
                    context.getSource().sendFailure(Component.literal(
                            "[IC-Root] Recursion depth limit (" + MAX_RUN_DEPTH
                                    + ") reached. You cannot do this."));
                }
                return 0;
            }
            RUN_DEPTH.set(depth + 1);
            try {
                int result = original.run(context);
                // 命令执行完，按反馈开关发送 Return 消息
                if (isFeedbackAllowed(mod, original)) {
                    context.getSource().sendSuccess(
                            () -> Component.literal("[IC-Root] Return " + result), false);
                }
                return result;
            } finally {
                // finally 中递减，保证异常时不泄漏计数
                RUN_DEPTH.set(depth);
            }
        }
    }

    /**
     * 判断 run 反馈是否允许发送：
     * 全局开关 && 子命令级开关。
     *
     * @param mod      所属 ICMod，读取动态全局开关
     * @param original 原始命令；若为 {@link ICFeedback.FlaggedCommand} 则读取其注册期标记，
     *                 未包装则视为 true（跟随全局）
     */
    private static boolean isFeedbackAllowed(ICMod mod, Command<CommandSourceStack> original) {
        if (!mod.isRunFeedbackEnabled()) {
            // 全局关闭：谁都不可发送
            return false;
        }
        if (original instanceof ICFeedback.FlaggedCommand flagged) {
            // 子命令级标记：注册期定死
            return flagged.runFeedback();
        }
        // 未标记：默认 true，跟随全局
        return true;
    }

    /**
     * 通过反射写入 CommandNode#command（Brigadier 该字段为 protected 且无 setter）。
     */
    private static void setCommand(CommandNode<CommandSourceStack> node,
                                   Command<CommandSourceStack> command) {
        try {
            COMMAND_FIELD.set(node, command);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("无法包装命令节点: " + node.getName(), e);
        }
    }
}
