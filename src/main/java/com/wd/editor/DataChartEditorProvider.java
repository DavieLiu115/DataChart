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
		// 2026-09-24：Text Tab 交回平台默认文本编辑器，Board 用 PLACE_BEFORE_DEFAULT_EDITOR
		// 稳定地排在它之前并默认激活（平台专门处理"自定义 editor 相对默认 editor 的位置"，
		// 而多个自定义 provider 的 Tab 顺序在平台上没有保证）。
		// Text Tab 的 JSON 高亮由 DataChartSyntaxHighlighterFactory 挂到 dataChart 语言上。
		return FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR;
	}

}