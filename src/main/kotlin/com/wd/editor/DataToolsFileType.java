package com.wd.editor;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.wd.icon.PluginIcons;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author lww
 */
public class DataToolsFileType extends LanguageFileType {

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
	public String getDescription() {
		return DataChart.LANGUAGE_NAME;
	}

	@NotNull
	@Override
	public String getDefaultExtension() {
		return DataChart.LANGUAGE_NAME;
	}

	@Nullable
	@Override
	public Icon getIcon() {
		return PluginIcons.dataSchema;
	}
}