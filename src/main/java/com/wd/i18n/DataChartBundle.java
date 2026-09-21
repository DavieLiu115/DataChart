package com.wd.i18n;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

/**
 * 插件文案统一入口（国际化）。
 *
 * <p>文案文件（都放在 {@code src/main/resources/messages/} 下）：</p>
 * <ul>
 *   <li>{@code DataChartBoundle.properties} —— 默认语言（英文），必需，缺了会在
 *       非中英文的 locale 下抛 {@code MissingResourceException}；</li>
 *   <li>{@code DataChartBoundle_zh.properties} —— 简体中文。非 ASCII 字符不要直接写中文，
 *       要按 {@code Properties} 规范写成「反斜杠 + u + 4 位十六进制」的转义形式
 *       （IntelliJ 里可用 {@code native2ascii} 或 DevKit 的转换动作生成）。</li>
 * </ul>
 *
 * <p>用 {@link DynamicBundle} 而不是裸的 {@link java.util.ResourceBundle}：
 * IDE 切换语言（Language Pack）后无需重启即可取到新语言的文案，且带缓存。</p>
 *
 * <p><b>约定：不要在 Java 代码里写死用户可见文案</b>（菜单项 / 提示 / 通知 / 对话框），
 * 一律走 {@link #message(String, Object...)}。</p>
 *
 * @author lww
 */
public final class DataChartBundle extends DynamicBundle {

	/** 资源包路径：相对 classpath 根，用点分隔（即 messages/DataChartBoundle.properties）。 */
	private static final String BUNDLE = "messages.DataChartBoundle";

	private static final DataChartBundle INSTANCE = new DataChartBundle();

	private DataChartBundle() {
		super(BUNDLE);
	}

	/**
	 * 取文案。
	 *
	 * @param key    文案 key，见 {@code DataChartBoundle.properties}（写错会在 IDE 里高亮提示）
	 * @param params 占位符参数，对应文案里的 {@code {0}}、{@code {1}}…
	 */
	public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
		return INSTANCE.getMessage(key, params);
	}
}
