package com.wd.editor;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author lww
 */
public class DataChartEditorProvider implements FileEditorProvider, DumbAware {

	@Override
	public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
		// 根据文件扩展名判断是否接受该文件
		return DataToolsFileType.EXTENSION.equalsIgnoreCase(file.getExtension());
	}

	@Override
	public @NotNull FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
		// 创建自定义编辑器
		return new DataChartEditor(project, file);
	}

	@Override
	public @NotNull String getEditorTypeId() {
		// 唯一标识符
		return "DataChartEditorProvider";
	}

	@Override
	public @NotNull FileEditorPolicy getPolicy() {
		// 2026-09-24：改为 PLACE_BEFORE_DEFAULT_EDITOR（与 PYYP 的 .bizx / .datasetx 一致）——
		// 保留 IDEA 默认的 Text Tab，并把图形编辑器排到它前面作为默认激活 Tab。
		// 两端通过 EditorFileSync 双向同步（跨 Tab 保存 / 外部改动）。
		return FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR;
	}

}