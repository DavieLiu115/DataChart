package com.wd.editor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.wd.ui.DataChartView;
import java.awt.BorderLayout;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.nio.charset.StandardCharsets;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DataChartEditor extends UserDataHolderBase implements FileEditor {

	private final JPanel editorPanel;
	private final Project project;
	private final VirtualFile file;
	private DataChartView dataView;
	private volatile boolean initialized = false;
	private boolean modified = false;
	private boolean loading = false;
	private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

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
					// 看板内容变更时标记文件为已修改
					dataView.setBoardChangeListener(() -> setModified(true));
					// Command+S / Ctrl+S 保存
					dataView.setSaveListener(this::saveDocument);
					editorPanel.add(dataView.getRootComponent(), BorderLayout.CENTER);
					initialized = true;
					// 打开文件时加载已有内容
					loadFromFile();
				}
			}
		}
	}

	/**
	 * 从文件中加载看板内容（.datachart JSON）
	 */
	private void loadFromFile() {
		if (file == null || dataView == null) {
			return;
		}
		try {
			loading = true;
			String content = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
			if (content != null && !content.trim().isEmpty()) {
				dataView.loadFromJson(content);
			}
		} catch (Exception e) {
			// 文件为空或不存在时忽略，保持空看板
		} finally {
			loading = false;
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
		return modified; // 返回实际修改状态
	}

	/**
	 * 设置编辑器的修改状态，并通知 IDE（触发文件名变蓝 + 星号）
	 */
	public void setModified(boolean modified) {
		if (loading) {
			return; // 加载过程中不触发修改状态
		}
		boolean oldValue = this.modified;
		this.modified = modified;
		propertyChangeSupport.firePropertyChange(getModifiedPropertyName(), oldValue, modified);
	}

	@Override
	public boolean isValid() {
		return file != null && file.isValid(); // 检查文件是否仍然有效
	}

	/**
	 * 保存看板内容到文件（Ctrl+S / Command+S 时触发）
	 */
	public void saveDocument() {
		if (file == null || dataView == null) {
			return;
		}
		try {
			// 序列化看板为 JSON
			String json = dataView.serializeToJson();
			// 写入文件（字节方式，保持文件类型不变）
			ApplicationManager.getApplication().runWriteAction(() -> {
				try {
					file.setBinaryContent(json.getBytes(StandardCharsets.UTF_8));
				} catch (Exception e) {
					// 写入失败
				}
			});
			// 保存成功后重置修改状态（文件名恢复，星号消失）
			setModified(false);
		} catch (Exception e) {
			// 保存失败
		}
	}

	/**
	 * 保存并通过 FileDocumentManager 同步到磁盘（可选，增强可靠性）
	 */
	public void saveAsDocument() {
		saveDocument();
	}

	@Override
	public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
		propertyChangeSupport.addPropertyChangeListener(listener);
	}

	@Override
	public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
		propertyChangeSupport.removePropertyChangeListener(listener);
	}

	@Override
	public @Nullable FileEditorLocation getCurrentLocation() {
		return null; // 当前位置（可选）
	}

	@Override
	public void dispose() {
		// 自动保存未保存的改动（关闭/切换编辑器时）
		if (modified) {
			saveDocument();
		}
		// 清理资源
		editorPanel.removeAll();
		if (dataView != null) {
			dataView.dispose();
		}
	}

	@Override
	public @NotNull VirtualFile getFile() {
		return this.file;
	}

	/**
	 * 获取修改状态属性名，兼容不同版本 API：
	 * <ul>
	 *   <li>旧版（2022.3 等）：{@code FileEditor.PROP_MODIFIED} 常量</li>
	 *   <li>新版（2025.3 等）：{@code FileEditor.getPropModified()} 静态方法</li>
	 * </ul>
	 */
	private static String getModifiedPropertyName() {
		try {
			// 新版优先：getPropModified() 静态方法
			java.lang.reflect.Method method = FileEditor.class.getMethod("getPropModified");
			Object result = method.invoke(null);
			if (result instanceof String) {
				return (String) result;
			}
		} catch (NoSuchMethodException ignored) {
			// 旧版没有该方法
		} catch (Exception ignored) {
		}
		// 旧版回退：PROP_MODIFIED 常量
		return "modified";
	}
}