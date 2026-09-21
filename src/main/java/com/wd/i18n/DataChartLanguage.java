package com.wd.i18n;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.messages.Topic;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/**
 * 插件语言开关（**独立于 IDE 自身的语言**）。
 *
 * <p>需求（2026-09-21）：工具栏提供一个语言按钮，默认英文，点击切中文、再点切回英文；
 * 且退出 IDE 后下次进来保持上次的选择。</p>
 *
 * <p>实现要点：</p>
 * <ul>
 *   <li>默认英文 —— 读取不到设置（首次使用）时按英文处理，不跟随 IDE locale；</li>
 *   <li>持久化用 {@link PropertiesComponent#getInstance()}（<b>应用级</b>，写入 IDE 配置目录），
 *       所以重启后仍然生效；</li>
 *   <li>切换后 {@link DataChartBundle#invalidate()} 清掉已解析的资源包，
 *       并把 {@link #CHANGED} 广播出去，让已打开的视图刷新自己的文案。</li>
 * </ul>
 *
 * @author lww
 */
public final class DataChartLanguage {

	/**
	 * 语言切换事件（订阅方收到后刷新自己的界面文案）。
	 *
	 * <p>用 {@code Topic<Runnable>}：发布即执行，无需自定义监听接口。
	 * 订阅时记得 {@code messageBus.connect(disposable)}，视图销毁会自动退订。</p>
	 */
	public static final Topic<Runnable> CHANGED =
			Topic.create("DataChart.language.changed", Runnable.class);

	/** 持久化 key（{@link PropertiesComponent}，应用级） */
	private static final String KEY = "DataChart.language";

	private static final String VALUE_EN = "en";
	private static final String VALUE_ZH = "zh";

	/**
	 * 语言按钮上的文字。
	 *
	 * <p>语言选择器用「该语言自己怎么写」来标注（与 IDE 的 Language 设置一致），
	 * 因此这两个常量<b>有意不进资源包</b>：切到中文时显示「中文」，切到英文时显示「EN」。</p>
	 */
	public static final String DISPLAY_EN = "EN";
	public static final String DISPLAY_ZH = "中文";

	/** 默认语言（需求：默认英文） */
	private static final Locale LOCALE_EN = Locale.ENGLISH;
	/** 中文：资源包文件名是 {@code DataChartBoundle_zh.properties}，{@code zh_CN} 的候选链会命中它 */
	private static final Locale LOCALE_ZH = Locale.SIMPLIFIED_CHINESE;

	/** 当前语言缓存（volatile：可能被 EDT 之外的线程读取，如后台同步回调） */
	private static volatile Locale current;

	private DataChartLanguage() {
	}

	/**
	 * 当前语言（默认英文）。
	 */
	@NotNull
	public static Locale getLocale() {
		Locale cached = current;
		return cached != null ? cached : load();
	}

	/**
	 * 是否中文。
	 */
	public static boolean isChinese() {
		return LOCALE_ZH.getLanguage().equals(getLocale().getLanguage());
	}

	/**
	 * 中英互切：持久化 → 清资源包缓存 → 广播刷新。
	 */
	public static void toggle() {
		setLocale(isChinese() ? LOCALE_EN : LOCALE_ZH);
	}

	/**
	 * 设置语言（非中即英，其它语言一律按英文处理）。
	 */
	public static void setLocale(@NotNull Locale locale) {
		Locale normalized = LOCALE_ZH.getLanguage().equals(locale.getLanguage()) ? LOCALE_ZH : LOCALE_EN;
		current = normalized;
		persist(normalized);
		DataChartBundle.invalidate();
		// 通知已打开的视图刷新文案（同一次 toggle 只广播一次）
		ApplicationManager.getApplication().getMessageBus()
				.syncPublisher(CHANGED).run();
	}

	/**
	 * 从配置读取语言；读取失败按英文处理。
	 */
	private static synchronized Locale load() {
		Locale cached = current;
		if (cached != null) {
			return cached;
		}
		String stored = null;
		try {
			stored = PropertiesComponent.getInstance().getValue(KEY);
		} catch (Exception ignored) {
			// 读不到就当没设置过（例如单元测试/无 IDE 环境）
		}
		Locale locale = VALUE_ZH.equalsIgnoreCase(stored) ? LOCALE_ZH : LOCALE_EN;
		current = locale;
		return locale;
	}

	private static void persist(Locale locale) {
		try {
			PropertiesComponent.getInstance().setValue(KEY,
					LOCALE_ZH.getLanguage().equals(locale.getLanguage()) ? VALUE_ZH : VALUE_EN);
		} catch (Exception ignored) {
			// 写不进去只影响"下次记住"，不影响本次切换
		}
	}
}
