package com.wd.model;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.Feature;
import com.intellij.application.options.CodeStyle;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * .datachart 的 JSON 文本工具：统一「格式化输出」的风格。
 *
 * <p>2026-09-24 新增：编辑器加上 Text Tab 后，.datachart 需要多行可读的 JSON
 * （紧凑单行既不便阅读，也不利于 git diff）。fastjson 的 {@code PrettyFormat} 用 \t 缩进、
 * 冒号后也不带空格，都跟 IDE 的 JSON 风格不一致，这里统一后处理。</p>
 *
 * <p>缩进宽度<b>跟随 IDE 中 JSON 的代码风格设置</b>（{@link #resolveIndentSize}），
 * 这样「插件保存出的 JSON」与「用户在 Text Tab 按 Ctrl+Alt+L 的格式化结果」一致，
 * 不会出现两边格式来回抖的情况。</p>
 *
 * @author lww
 */
public final class ChartJsonUtil {

	/** 兜底缩进宽度：与新建文件模板 {@code fileTemplates/DataChart.datachart.ft} 一致 */
	public static final int DEFAULT_INDENT_SIZE = 2;

	/**
	 * fastjson PrettyFormat 输出中的行首缩进（\t）。
	 *
	 * <p>JSON 字符串值里的换行与制表符都会被转义成 {@code \n} / {@code \t} 两个字符，
	 * 不会出现真实换行，因此「行首的连续 \t」必然是结构缩进，可以安全替换。</p>
	 */
	private static final Pattern INDENT_PATTERN = Pattern.compile("(?m)^\t+");

	private ChartJsonUtil() {
	}

	/**
	 * 取 IDE 中 JSON 语言的缩进宽度（Editor → Code Style → JSON 的设置）。
	 *
	 * @return 缩进宽度；取不到时返回 {@link #DEFAULT_INDENT_SIZE}
	 */
	public static int resolveIndentSize(@Nullable Project project) {
		if (project != null) {
			try {
				CodeStyleSettings settings = CodeStyle.getSettings(project);
				CommonCodeStyleSettings jsonSettings = settings.getCommonSettings(JsonLanguage.INSTANCE);
				if (jsonSettings != null) {
					CommonCodeStyleSettings.IndentOptions options = jsonSettings.getIndentOptions();
					if (options != null && options.INDENT_SIZE > 0) {
						return options.INDENT_SIZE;
					}
				}
			} catch (Throwable ignored) {
				// 极端情况下（JSON 模块不可用等）退回默认值，不能让保存失败
			}
		}
		return DEFAULT_INDENT_SIZE;
	}

	/**
	 * 把模型对象序列化为格式化 JSON（多行 + {@code indentSize} 个空格缩进）。
	 */
	public static String toPrettyJson(Object model, int indentSize) {
		return format(JSON.toJSONString(model, true), indentSize);
	}

	/**
	 * 把 JSON 文本重排为格式化文本（<b>只改空白，不改字段顺序与取值</b>）。
	 *
	 * <p>用于把历史遗留的紧凑单行 .datachart 迁移成格式化版本：
	 * 走 {@code parseObject} → {@code toJSONString(pretty)}，数值字面量保持原样。</p>
	 *
	 * <p>⚠️ 必须带 {@link Feature#OrderedField}：fastjson 的 {@code JSONObject} 默认基于 HashMap，
	 * 不加这个 Feature 会把字段顺序打乱，与模型序列化的顺序不一致 —— 后果是每次打开文件
	 * 都会因「重排结果 ≠ 原文」而再次写盘、字段顺序来回跳。</p>
	 *
	 * @return 格式化后的文本；文本不是合法 JSON 时返回 {@code null}（调用方须保持原样，避免误改内容）
	 */
	public static String prettifyText(String rawJson, int indentSize) {
		if (rawJson == null || rawJson.isEmpty()) {
			return null;
		}
		try {
			Object parsed = JSON.parseObject(rawJson, Feature.OrderedField);
			if (parsed == null) {
				return null;
			}
			return format(JSON.toJSONString(parsed, true), indentSize);
		} catch (Exception e) {
			return null;
		}
	}

	/** 统一后处理：\t → {@code indentSize} 个空格缩进 + 冒号后补空格。 */
	private static String format(String fastjsonPretty, int indentSize) {
		return spaceAfterColon(normalizeIndent(fastjsonPretty, indentSize));
	}

	/**
	 * fastjson 的 \t 缩进 → 指定宽度的空格缩进。
	 */
	private static String normalizeIndent(String prettyJson, int indentSize) {
		if (prettyJson == null) {
			return null;
		}
		String unit = " ".repeat(Math.max(1, indentSize));
		Matcher matcher = INDENT_PATTERN.matcher(prettyJson);
		return matcher.replaceAll(r -> unit.repeat(r.group().length()));
	}

	/**
	 * 给键值分隔的冒号补空格：fastjson 输出 {@code "k":v}，
	 * 而模板与 IDEA 的 JSON 风格（Ctrl+Alt+L）都是 {@code "k": v}。
	 *
	 * <p>JSON 里不在字符串内的冒号只有「键值分隔」一种语义，所以逐字符扫描、
	 * 跳过字符串字面量（含转义）即可安全处理，不会误改字符串值内部的冒号。</p>
	 */
	private static String spaceAfterColon(String prettyJson) {
		StringBuilder sb = new StringBuilder(prettyJson.length() + 64);
		boolean inString = false;
		boolean escaped = false;
		for (int i = 0; i < prettyJson.length(); i++) {
			char c = prettyJson.charAt(i);
			sb.append(c);
			if (inString) {
				if (escaped) {
					escaped = false;
				} else if (c == '\\') {
					escaped = true;
				} else if (c == '"') {
					inString = false;
				}
				continue;
			}
			if (c == '"') {
				inString = true;
			} else if (c == ':' && i + 1 < prettyJson.length() && prettyJson.charAt(i + 1) != ' ') {
				sb.append(' ');
			}
		}
		return sb.toString();
	}
}
