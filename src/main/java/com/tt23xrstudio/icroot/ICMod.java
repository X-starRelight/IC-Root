package com.tt23xrstudio.icroot;

import java.util.function.Consumer;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;

/**
 * 已注册 Mod 的句柄，由 {@link ICSeries#register(String)} 创建。
 * 
 * 通过两个 {@code RegMod} 重载把自己的命令树交给 IC：
 * 命令树最终挂载到 {@code /ic run <name>} 下，不污染全局命令空间。
 */
public final class ICMod {
    private final String name;
    /** 该 Mod 的子命令树，根节点 children 会被挂载到 /ic run <name> 下 */
    private CommandDispatcher<CommandSourceStack> dispatcher;
    /**
     * 全局 run 反馈开关：控制该 Mod 下<b>全部</b>子命令的 run 反馈。
     * false 时谁都不可发送；true 时仍需子命令级开关为 true 才可发送。
     * volatile 以支持运行时动态开关。
     */
    private volatile boolean runFeedback;

    ICMod(String name, boolean runFeedback) {
        this.name = name;
        this.runFeedback = runFeedback;
    }

    /**
     * 返回注册时使用的 Mod 名。
     */
    public String name() {
        return name;
    }

    /**
     * 全局 run 反馈开关（可动态调用）。
     * 
     * false：该 Mod 下全部子命令的 run 反馈一律不发送；
     * true：子命令级开关为 true（或未标记）时才发送。
     *
     * @param enabled 新的全局开关状态
     * @return this，支持链式调用
     */
    public ICMod runFeedback(boolean enabled) {
        this.runFeedback = enabled;
        return this;
    }

    /**
     * 查询当前全局 run 反馈开关。
     */
    public boolean isRunFeedbackEnabled() {
        return runFeedback;
    }

    /**
     * 重载 1：直接传入已注册好命令树的 dispatcher。
     * 
     * 用户自行创建并注册命令树，再整体交给 IC 保存。
     *
     * @param dispatcher 已包含该 Mod 命令树的 dispatcher
     */
    public void RegMod(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (dispatcher == null) {
            throw new IllegalArgumentException("dispatcher 不能为 null");
        }
        // 后者覆盖前者
        this.dispatcher = dispatcher;
    }

    /**
     * 重载 2：传入 lambda，由 IC 提供子 dispatcher。
     * 
     * 写法与 Fabric 原版注册命令一致，示例：
     * mod.RegMod(d -&gt; d.register(literal("bar").executes(ctx -&gt; 1)));
     *
     * @param registrar 接收子 dispatcher 的回调
     */
    public void RegMod(Consumer<CommandDispatcher<CommandSourceStack>> registrar) {
        if (registrar == null) {
            throw new IllegalArgumentException("registrar 不能为 null");
        }
        // 尚未创建则新建；已存在则在其上继续追加
        if (this.dispatcher == null) {
            this.dispatcher = new CommandDispatcher<>();
        }
        registrar.accept(this.dispatcher);
    }

    /**
     * 内部读取：命令树构建时挂载用。
     */
    CommandDispatcher<CommandSourceStack> dispatcher() {
        return dispatcher;
    }
}
