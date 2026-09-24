package com.wd.editor;

import com.intellij.lang.Language;

/**
 * .datachart 的自定义语言（仅用于文件类型声明；语法高亮由 Text Tab 的 JSON 编辑器负责）。
 *
 * @author lww
 */
public class DataChart extends Language {

	public static final String LANGUAGE_NAME = "dataChart";

	public static final DataChart INSTANCE_C = new DataChart();

	protected DataChart() {
		super(LANGUAGE_NAME);
	}
}
