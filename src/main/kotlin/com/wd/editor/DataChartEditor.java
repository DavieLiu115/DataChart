package com.wd.editor;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.wd.ui.DataChartView;
import java.awt.BorderLayout;
import java.beans.PropertyChangeListener;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DataChartEditor extends UserDataHolderBase implements FileEditor {

	private final JPanel editorPanel;
	private final Project project;
	private VirtualFile file;
	private DataChartView dataView;
	private volatile boolean initialized = false;

	public DataChartEditor(Project project, VirtualFile file) {
		this.project = project;
		this.file = file;
		// 只创建容器面板,不立即初始化 DataChartView
		editorPanel = new JPanel(new BorderLayout());
	}

	/**
	 * 延迟初始化 DataChartView,只在真正需要显示时才创建
	 */
	private void ensureInitialized() {
		if (!initialized) {
			synchronized (this) {
				if (!initialized) {
					dataView = new DataChartView(project);
					editorPanel.add(dataView.getRootComponent());
					initialized = true;
				}
			}
		}
	}

	@Override
	public @NotNull JComponent getComponent() {
		ensureInitialized(); // 延迟初始化
		return editorPanel; // 返回自定义的 UI 组件
	}

	@Override
	public @Nullable JComponent getPreferredFocusedComponent() {
		return editorPanel; // 指定默认聚焦的组件
	}

	@Override
	public @NotNull String getName() {
		return "DataChartEditor"; // 编辑器名称
	}

	@Override
	public void setState(@NotNull FileEditorState state) {
		// 处理状态更新（可选）
	}

	@Override
	public boolean isModified() {
		return false; // 是否被修改
	}

	@Override
	public boolean isValid() {
		return true; // 是否有效
	}

	@Override
	public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
		// 添加属性监听器（可选）
	}

	@Override
	public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
		// 移除属性监听器（可选）
	}

	@Override
	public @Nullable FileEditorLocation getCurrentLocation() {
		return null; // 当前位置（可选）
	}

	@Override
	public void dispose() {
		// 清理资源（可选）
		editorPanel.removeAll();
		if (dataView != null) {
			dataView.dispose();
		}
	}

	@Override
	public @NotNull VirtualFile getFile() {
		return this.file; // 文件（可选）
	}
}