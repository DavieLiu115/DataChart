package com.wd.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

/**
 * 插件文案统一入口（国际化）。
 *
 * <p>文案文件（都在 {@code src/main/resources/messages/} 下）：</p>
 * <ul>
 *   <li>{@code DataChartBoundle.properties} —— 默认语言（英文），必需；</li>
 *   <li>{@code DataChartBoundle_zh.properties} —— 简体中文。非 ASCII 字符不要直接写中文，
 *       要按 {@code Properties} 规范写成「反斜杠 + u + 4 位十六进制」的转义形式。</li>
 * </ul>
 *
 * <p><b>语言由插件自己决定</b>（见 {@link DataChartLanguage}），不跟随 IDE 语言：
 * 用户可以在工具栏上切换中英文，并通过 {@code PropertiesComponent} 持久化。
 * 因此这里不用 {@code DynamicBundle}（它按 IDE locale 解析，且结果被平台缓存，改不动），
 * 而是自己按 {@link DataChartLanguage#getLocale()} 解析 {@link ResourceBundle} 并缓存，
 * 切换语言时由 {@link #invalidate()} 丢弃缓存。</p>
 *
 * <p><b>约定：不要在 Java 代码里写死用户可见文案</b>（菜单项 / 提示 / 通知 / 对话框），
 * 一律走 {@link #message(String, Object...)}。</p>
 *
 * @author lww
 */
public final class DataChartBundle {

	/** 资源包路径：相对 classpath 根，用点分隔（即 messages/DataChartBoundle.properties）。 */
	private static final String BUNDLE = "messages.DataChartBoundle";

	/**
	 * 解析策略：<b>只认显式传入的 locale，不回退 JVM 默认 locale</b>。
	 *
	 * <p>关键点：默认的 {@code ResourceBundle.Control#getFallbackLocale} 会追加「JVM 默认 locale」
	 * 作为候选，于是中文系统上请求 {@code Locale.ENGLISH} 也会拿到 {@code _zh} 资源包 ——
	 * 那样插件里的语言开关就失效了。这里返回 null 把它掐掉。</p>
	 *
	 * <p>候选链保持不变（{@code zh_CN → zh → root}），所以中文能命中
	 * {@code DataChartBoundle_zh.properties}，英文命中 base 文件。</p>
	 */
	private static final ResourceBundle.Control CONTROL = new ResourceBundle.Control() {
		@Override
		public Locale getFallbackLocale(String baseName, Locale locale) {
			return null;
		}
	};

	private static volatile ResourceBundle bundle;
	private static volatile Locale loadedLocale;

	private DataChartBundle() {
	}

	/**
	 * 取文案。
	 *
	 * @param key    文案 key，见 {@code DataChartBoundle.properties}（写错会在 IDE 里高亮提示）
	 * @param params 占位符参数，对应文案里的 {@code {0}}、{@code {1}}…
	 */
	public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
		String pattern = resolve().getString(key);
		if (params == null || params.length == 0) {
			return pattern;
		}
		return MessageFormat.format(pattern, params);
	}

	/**
	 * 丢弃已解析的资源包缓存（语言切换后由 {@link DataChartLanguage} 调用）。
	 */
	static void invalidate() {
		bundle = null;
	}

	/**
	 * 取当前语言对应的资源包（带缓存；缓存失效时重新解析）。
	 */
	private static ResourceBundle resolve() {
		Locale locale = DataChartLanguage.getLocale();
		ResourceBundle cached = bundle;
		if (cached != null && locale.equals(loadedLocale)) {
			return cached;
		}
		return reload(locale);
	}

	private static synchronized ResourceBundle reload(Locale locale) {
		// 双检：可能已被其它线程刷新
		Locale current = DataChartLanguage.getLocale();
		ResourceBundle cached = bundle;
		if (cached != null && current.equals(loadedLocale)) {
			return cached;
		}
		ResourceBundle fresh = ResourceBundle.getBundle(BUNDLE, locale,
				DataChartBundle.class.getClassLoader(), CONTROL);
		loadedLocale = current;
		bundle = fresh;
		return fresh;
	}
}
