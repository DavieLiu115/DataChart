package com.wd.editor;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * .datachart 的 Text Tab（JSON 高亮文本编辑器）提供者。
 *
 * <p>与 {@link DataChartEditorProvider}（Board Tab）配对：两个 Tab 都由本插件提供，
 * 平台默认的纯文本编辑器由 Board provider 的
 * {@link FileEditorPolicy#HIDE_DEFAULT_EDITOR} 隐藏 —— 因为它的 Tab 名也是 "Text"，
 * 会和这里的 JSON 版 Text Tab 重名。</p>
 *
 * @author lww
 */
public class DataChartTextEditorProvider implements FileEditorProvider, DumbAware {

	@Override
	public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
		return DataToolsFileType.EXTENSION.equalsIgnoreCase(file.getExtension());
	}

	@Override
	public @NotNull FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
		return new DataChartTextEditor(project, file);
	}

	@Override
	public @NotNull String getEditorTypeId() {
		return "DataChartTextEditorProvider";
	}

	@Override
	public @NotNull FileEditorPolicy getPolicy() {
		return FileEditorPolicy.NONE;
	}
}
