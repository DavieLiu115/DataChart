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
		// 2026-09-24：Board / Text 改成同一个 FileEditor 内部的页签（JBTabs），
		// 因此这里让本 provider 独占 IDE 层面的编辑器：隐藏平台自带的文本编辑器等，
		// 避免 IDE 层再冒出一个多余的 tab。
		// （平台不保证多个自定义 provider 之间的 Tab 顺序，见 DEVELOPMENT_GUIDE 第 50 节。）
		return FileEditorPolicy.HIDE_OTHER_EDITORS;
	}

}