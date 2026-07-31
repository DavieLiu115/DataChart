package com.wd.editor;

import com.intellij.lang.Language;

/**
 * @author lww
 */
public class DataChart extends Language {

	public static final String LANGUAGE_NAME = "dataChart";

	public static final DataChart INSTANCE_C = new DataChart();

	protected DataChart() {
		super(LANGUAGE_NAME);
	}
}
