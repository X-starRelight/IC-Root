package com.tt23xrstudio.icroot;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;

/**
 * run 反馈的子命令级开关。
 * 
 * 子命令级开关在注册命令时定死，<b>不可动态修改</b>；
 * 实际是否发送反馈 = 全局开关（{@link ICMod#runFeedback(boolean)}）
 * && 子命令级开关。
 * 
 * 用法（第一个参数即子命令级 runFeedback）：
 * // 不包装 = 默认 true，跟随全局开关
 * d.register(Commands.literal("hello").executes(ctx -&gt; 1));
 *
 * // 子命令级 false：无论全局为何，永不发送 run 反馈
 * d.register(Commands.literal("quiet")
 *         .executes(ICFeedback.executes(false, ctx -&gt; 1)));
 *
 * // 子命令级 true：显式声明（仍需全局为 true 才发送）
 * d.register(Commands.literal("loud")
 *         .executes(ICFeedback.executes(true, ctx -&gt; 1)));
 */
public final class ICFeedback {
    private ICFeedback() {
    }

    /**
     * 包装一个命令执行，带上子命令级 runFeedback 标记。
     *
     * @param runFeedback 子命令级开关，注册期定死
     * @param command     原始命令执行体
     * @return 携带标记的 Command，供 Brigadier executes 使用
     */
    public static Command<CommandSourceStack> executes(
            boolean runFeedback, Command<CommandSourceStack> command) {
        if (command == null) {
            throw new IllegalArgumentException("command 不能为 null");
        }
        return new FlaggedCommand(runFeedback, command);
    }

    /**
     * 携带子命令级 runFeedback 标记的命令包装。
     * IC 在挂载 /ic run 命令树时通过 instanceof 识别该标记。
     */
    public static final class FlaggedCommand implements Command<CommandSourceStack> {
        private final boolean runFeedback;
        private final Command<CommandSourceStack> delegate;

        private FlaggedCommand(boolean runFeedback, Command<CommandSourceStack> delegate) {
            this.runFeedback = runFeedback;
            this.delegate = delegate;
        }

        /**
         * 子命令级 runFeedback 开关（注册期定死）。
         */
        public boolean runFeedback() {
            return runFeedback;
        }

        @Override
        public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
            return delegate.run(context);
        }
    }
}
