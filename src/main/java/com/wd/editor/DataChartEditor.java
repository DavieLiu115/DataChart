package com.wd.editor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.wd.i18n.DataChartBundle;
import com.wd.ui.DataChartView;
import com.wd.ui.NotificationUtil;
import java.awt.BorderLayout;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.nio.charset.StandardCharsets;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DataChartEditor extends UserDataHolderBase implements FileEditor {

	private static final Logger LOG = Logger.getInstance(DataChartEditor.class);

	private final JPanel editorPanel;
	private final Project project;
	private final VirtualFile file;
	private DataChartView dataView;
	private volatile boolean initialized = false;
	private boolean modified = false;
	private volatile boolean loading = false;
	/** dispose 已执行：丢弃一切挂起的异步加载/回调（2026-08-27 复查补充） */
	private volatile boolean disposed = false;
	private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

	/** 最后一次保存成功时的文件修改戳，用于 dispose 时检测磁盘是否被外部修改（2026-08-27） */
	private long lastSavedStamp = -1;

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
					// 暴露当前文件名（用于导出 PDF / 图片的默认文件名）
					dataView.setBaseFileName(resolveBaseFileName());
					editorPanel.add(dataView.getRootComponent(), BorderLayout.CENTER);
					initialized = true;
					// 打开文件时加载已有内容
					loadFromFile();
				}
			}
		}
	}

	/**
	 * 解析当前文件的基础名（用于导出 PDF / 图片默认文件名）
	 *
	 * <p>取 {@code file.getNameWithoutExtension()}，未保存（无 file）时返回 "datachart"。</p>
	 */
	private String resolveBaseFileName() {
		if (file == null || file.getName().isEmpty()) {
			return "datachart";
		}
		String name = file.getNameWithoutExtension();
		return (name == null || name.isEmpty()) ? "datachart" : name;
	}

	/**
	 * 从文件中加载看板内容（.datachart JSON）。
	 *
	 * <p>2026-08-27 优化：文件字节读取移到后台线程，避免大文件 IO 卡 EDT；
	 * JSON 解析与看板状态更新回到 EDT 执行（Swing 组件只能在 EDT 操作）。</p>
	 */
	private void loadFromFile() {
		if (file == null || dataView == null) {
			return;
		}
		loading = true;
		ApplicationManager.getApplication().executeOnPooledThread(() -> {
			String content = null;
			try {
				content = ReadAction.compute(() -> {
					try {
						return new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
					} catch (Exception ex) {
						return null;
					}
				});
			} catch (Exception e) {
				LOG.warn("读取 .datachart 文件失败: " + file.getName(), e);
			}
			final String loaded = content;
			javax.swing.SwingUtilities.invokeLater(() -> {
				loading = false;
				// dispose 后丢弃加载结果，避免重新填充已清理的看板（内存泄漏/悬空引用）
				if (disposed) {
					return;
				}
				if (loaded != null && !loaded.trim().isEmpty()) {
					dataView.loadFromJson(loaded);
				}
			});
		});
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
	 * 保存看板内容到文件（Ctrl+S / Command+S 时触发）。
	 *
	 * <p>2026-08-27：写入失败不再静默吞掉，改为 LOG + 气泡通知；
	 * 成功后记录文件修改戳，供 dispose 外部修改检测使用。</p>
	 */
	public void saveDocument() {
		if (file == null || dataView == null) {
			return;
		}
		// 2026-08-27 复查补充：异步加载未完成时禁止保存，
		// 否则 serializeToJson 会序列化尚未加载的空看板并覆盖磁盘原文件
		if (loading) {
			NotificationUtil.info(DataChartBundle.message("DataChart.notify.loading"),
					DataChartBundle.message("DataChart.editor.loading.content"));
			return;
		}
		try {
			// 序列化看板为 JSON
			String json = dataView.serializeToJson();
			final boolean[] ok = {false};
			// 写入文件（字节方式，保持文件类型不变）
			ApplicationManager.getApplication().runWriteAction(() -> {
				try {
					file.setBinaryContent(json.getBytes(StandardCharsets.UTF_8));
					ok[0] = true;
				} catch (Exception e) {
					LOG.warn("保存 .datachart 文件失败: " + file.getName(), e);
				}
			});
			if (!ok[0]) {
				NotificationUtil.error(DataChartBundle.message("DataChart.notify.save.failed"),
						DataChartBundle.message("DataChart.editor.save.writeFailed", file.getName()));
				return;
			}
			lastSavedStamp = file.getModificationStamp();
			// 保存成功后重置修改状态（文件名恢复，星号消失）
			setModified(false);
		} catch (Exception e) {
			LOG.warn("保存 .datachart 序列化失败", e);
			NotificationUtil.error(DataChartBundle.message("DataChart.notify.save.failed"),
					DataChartBundle.message("DataChart.editor.save.serializeFailed", e.getMessage()));
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
		disposed = true; // 先置位，阻断挂起的异步加载回调
		// 2026-08-27 修复：不再无条件自动保存。
		// 若磁盘文件在本编辑器上次保存后被外部工具修改过，放弃自动落盘，
		// 避免静默覆盖外部改动（此前会绕过 IDE 未保存确认直接写盘）。
		if (modified) {
			boolean changedExternally = false;
			try {
				long currentStamp = ReadAction.compute(file::getModificationStamp);
				changedExternally = lastSavedStamp >= 0 && currentStamp != lastSavedStamp;
			} catch (Exception e) {
				LOG.warn("dispose 时读取文件修改戳失败", e);
			}
			if (changedExternally) {
				LOG.warn("文件在磁盘上已被外部修改，放弃自动保存以避免覆盖: " + file.getName());
			} else {
				saveDocument();
			}
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
	 * 获取修改状态属性名，兼容不同版本 API（结果静态缓存，避免每次 setModified 反射）：
	 * <ul>
	 *   <li>旧版（2022.3 等）：{@code FileEditor.PROP_MODIFIED} 常量</li>
	 *   <li>新版（2025.3 等）：{@code FileEditor.getPropModified()} 静态方法</li>
	 * </ul>
	 */
	private static volatile String cachedModifiedPropertyName;

	private static String getModifiedPropertyName() {
		if (cachedModifiedPropertyName != null) {
			return cachedModifiedPropertyName;
		}
		try {
			// 新版优先：getPropModified() 静态方法
			java.lang.reflect.Method method = FileEditor.class.getMethod("getPropModified");
			Object result = method.invoke(null);
			if (result instanceof String) {
				cachedModifiedPropertyName = (String) result;
				return cachedModifiedPropertyName;
			}
		} catch (NoSuchMethodException ignored) {
			// 旧版没有该方法
		} catch (Exception ignored) {
		}
		// 旧版回退：PROP_MODIFIED 常量
		cachedModifiedPropertyName = "modified";
		return cachedModifiedPropertyName;
	}
}