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
		return "datachart".equalsIgnoreCase(file.getExtension());
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
		// 隐藏默认编辑器
		return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
	}

}