package com.wd.util;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThrowableComputable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 同步执行一次读操作（等价于老写法 {@code ReadAction.compute(...)}）。
 *
 * <p><b>为什么需要这层包装</b>（2026-09-28，2026.3 的 Plugin Verifier 报 "8 usages of deprecated API"）：
 * 平台把 {@code ReadAction.compute(ThrowableComputable)} 标成了 {@code @Deprecated}，
 * 其 javadoc 给的替代是 {@code ReadAction.computeBlocking(ThrowableComputable)}
 * （语义 = "明确不可取消的读操作"，正是我们这些调用点的用法）。</p>
 *
 * <p>难点在于本插件要兼容 223 ~ 263+：{@code computeBlocking} 是新加的
 * （本机 2024.3 的 {@code ReadAction} 里还没有，只有 {@code compute}），
 * 而 {@code compute} 在老版本里是唯一入口 —— <b>直接引用任何一个都不行</b>。
 * 于是这里用反射按顺序挑：
 * {@code computeBlocking}（新，未废弃）→ {@code compute}（旧，263 起废弃）→
 * {@code Application#runReadAction}（最老，兜底）。</p>
 *
 * <p>副作用（正是我们要的）：字节码里<b>没有任何</b>这些成员的硬引用，
 * 所以 verifier 在哪个版本都不会再报它们。</p>
 */
public final class ReadActions {

	private static final Logger LOG = Logger.getInstance(ReadActions.class);

	/** 新入口（未废弃）：{@code ReadAction.computeBlocking(ThrowableComputable)}。 */
	private static final Method COMPUTE_BLOCKING =
			findStaticMethod(ReadAction.class, "computeBlocking", ThrowableComputable.class);

	/** 旧入口：{@code ReadAction.compute(ThrowableComputable)}，2026.3 起 {@code @Deprecated}（内部就委托给 computeBlocking）。 */
	private static final Method COMPUTE =
			findStaticMethod(ReadAction.class, "compute", ThrowableComputable.class);

	/** 最老的写法，兜底：{@code Application.runReadAction(ThrowableComputable)}。 */
	private static final Method RUN_READ_ACTION =
			findStaticMethod(Application.class, "runReadAction", ThrowableComputable.class);

	private ReadActions() {
	}

	/**
	 * 在读操作中同步执行 {@code computable}（阻塞、不可取消，等价于老的 {@code ReadAction.compute}）。
	 *
	 * <p>三个入口都取不到时（平台把它们全删了的极端情况）会打日志并<b>直接执行</b> ——
	 * 能跑就跑，不额外抛异常，避免把"平台改了 API"变成用户侧的崩溃。</p>
	 *
	 * @param computable 读操作体
	 * @return 读操作体的返回值
	 * @throws E 读操作体抛出的异常<b>原样</b>抛出（与 {@code ReadAction.compute} 语义一致）
	 */
	public static <T, E extends Throwable> T compute(@NotNull ThrowableComputable<T, E> computable) throws E {
		if (COMPUTE_BLOCKING != null) {
			return invoke(COMPUTE_BLOCKING, null, computable);
		}
		if (COMPUTE != null) {
			return invoke(COMPUTE, null, computable);
		}
		if (RUN_READ_ACTION != null) {
			return invoke(RUN_READ_ACTION, ApplicationManager.getApplication(), computable);
		}
		LOG.warn("平台未提供任何 ReadAction 入口（computeBlocking / compute / runReadAction 均缺失），直接执行读操作体");
		return computable.compute();
	}

	/**
	 * 反射调用读操作入口。
	 *
	 * <p>注意：反射会把读操作体抛出的异常包成 {@link InvocationTargetException}，
	 * 这里必须拆包原样抛出，否则调用方看到的异常类型会变（catch 块、日志都会失真）。</p>
	 */
	@SuppressWarnings("unchecked")
	private static <T, E extends Throwable> T invoke(@NotNull Method method, @Nullable Object target,
			@NotNull ThrowableComputable<T, E> computable) throws E {
		try {
			return (T) method.invoke(target, computable);
		} catch (InvocationTargetException e) {
			throw (E) e.getCause();
		} catch (IllegalAccessException | RuntimeException e) {
			throw new IllegalStateException("调用 " + method + " 失败", e);
		}
	}

	/** 反射取公开静态方法；方法不存在或非静态时返回 {@code null}（调用方据此降级）。 */
	@Nullable
	private static Method findStaticMethod(@NotNull Class<?> owner, @NotNull String name,
			@NotNull Class<?>... parameterTypes) {
		try {
			Method method = owner.getMethod(name, parameterTypes);
			return Modifier.isStatic(method.getModifiers()) ? method : null;
		} catch (Throwable t) {
			return null;
		}
	}
}
