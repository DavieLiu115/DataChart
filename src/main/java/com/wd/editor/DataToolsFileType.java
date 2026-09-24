package com.wd.editor;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.wd.icon.PluginIcons;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * .datachart 文件类型。
 *
 * <p>语言仍是插件自定义的 {@link DataChart}（不改成 JSON 语言，避免影响看板/文件类型的既有行为）；
 * 编辑器 Text Tab 的 JSON 高亮与格式化由 {@code DataChartTextEditor} 用
 * {@code LightVirtualFile + JsonFileType} 实现（参考 YamlHelper 的做法）。</p>
 *
 * @author lww
 */
public class DataToolsFileType extends LanguageFileType {

	public static final String EXTENSION = "datachart";

	public static final DataToolsFileType INSTANCE_C = new DataToolsFileType();

	private DataToolsFileType() {
		super(DataChart.INSTANCE_C);
	}

	@NotNull
	@Override
	public String getName() {
		return DataChart.LANGUAGE_NAME;
	}

	@NotNull
	@Override
	public String getDisplayName() {
		return "DataChart";
	}

	@NotNull
	@Override
	public String getDescription() {
		return "DataChart";
	}

	@NotNull
	@Override
	public String getDefaultExtension() {
		return EXTENSION;
	}

	@Nullable
	@Override
	public Icon getIcon() {
		return PluginIcons.dataSchema;
	}
}
