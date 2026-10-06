package com.tt23xrstudio.icroot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IC 注册表与管理器。
 * 
 * 各 Mod 在初始化阶段调用 {@link #register(String)} 获取 {@link ICMod}，
 * 再通过 {@link ICMod#RegMod} 注册自己的命令树。
 * 命令树构建完成后注册表冻结，运行时不再允许新增。
 */
public final class ICSeries {
    /** 按注册顺序保存，保证 /ic run 下字面量节点顺序稳定 */
    private static final Map<String, ICMod> REGISTRY = new LinkedHashMap<>();
    /** 是否已冻结（命令树构建完成后置为 true） */
    private static volatile boolean frozen = false;

    private ICSeries() {
    }

    /**
     * 注册一个 Mod 到 IC，全局 run 反馈默认开启。
     *
     * @param name 普通字符串名称（非 Identifier），全表唯一
     * @return 新创建的 ICMod 句柄
     * @throws IllegalStateException 重名或注册表已冻结时抛出
     */
    public static ICMod register(String name) {
        return register(name, true);
    }

    /**
     * 注册一个 Mod 到 IC，并指定全局 run 反馈开关。
     *
     * @param name        普通字符串名称（非 Identifier），全表唯一
     * @param runFeedback 全局 run 反馈开关，控制该 Mod 全部子命令
     * @return 新创建的 ICMod 句柄
     * @throws IllegalStateException 重名或注册表已冻结时抛出
     */
    public static ICMod register(String name, boolean runFeedback) {
        if (frozen) {
            throw new IllegalStateException(
                    "IC 注册表已冻结，禁止在命令树构建后注册: " + name);
        }
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("注册名不能为空");
        }
        if (REGISTRY.containsKey(name)) {
            throw new IllegalStateException("Mod 名重复注册: " + name);
        }
        ICMod mod = new ICMod(name, runFeedback);
        REGISTRY.put(name, mod);
        return mod;
    }

    /**
     * 查询指定名称是否已注册。
     */
    public static boolean isRegistered(String name) {
        return REGISTRY.containsKey(name);
    }

    /**
     * 返回所有已注册 Mod 名（只读快照，保持注册顺序）。
     */
    public static List<String> getRegisteredNames() {
        return Collections.unmodifiableList(new ArrayList<>(REGISTRY.keySet()));
    }

    /**
     * 按名称获取已注册的 ICMod。
     *
     * @return 对应的 ICMod，未注册时返回 null
     */
    public static ICMod get(String name) {
        return REGISTRY.get(name);
    }

    /**
     * 冻结注册表。仅供 ICRootMod 在命令树构建完成后调用。
     */
    static void freeze() {
        frozen = true;
    }

    /**
     * 注册表是否已冻结。
     */
    static boolean isFrozen() {
        return frozen;
    }
}
