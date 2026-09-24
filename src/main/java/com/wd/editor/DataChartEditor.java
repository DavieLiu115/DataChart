package com.wd.editor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.JBTabsFactory;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.wd.i18n.DataChartBundle;
import com.wd.model.ChartJsonUtil;
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
	/** 编辑器内部的页签容器（Board / Text 两个页） */
	private JBTabs tabs;
	private TabInfo boardTab;
	private TabInfo textTab;
	/** Text 页：JSON 高亮的文本编辑器 */
	private DataChartJsonPanel jsonPanel;
	private volatile boolean initialized = false;
	private boolean modified = false;
	private volatile boolean loading = false;
	/** dispose 已执行：丢弃一切挂起的异步加载/回调（2026-08-27 复查补充） */
	private volatile boolean disposed = false;
	private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

	/** 最后一次保存成功时的文件修改戳，用于 dispose 时检测磁盘是否被外部修改（2026-08-27） */
	private long lastSavedStamp = -1;

	/** 图形 Tab ↔ IDEA 默认 Text Tab 的跨 Tab 同步（2026-09-24 新增） */
	private final EditorFileSync fileSync;
	/** 平台内置 Save All（Cmd/Ctrl+S）时补位保存画布（2026-09-24 新增） */
	private final EditorSaveAllHook saveAllHook;

	public DataChartEditor(Project project, VirtualFile file) {
		this.project = project;
		this.file = file;
		// 只创建容器面板,不立即初始化 DataChartView
		editorPanel = new JPanel(new BorderLayout());
		if (file != null) {
			// Text Tab 保存 / 外部工具改动磁盘 → 重新加载画布（有未保存修改时会先问用户）
			fileSync = new EditorFileSync(project, file, this, () -> modified, () -> {
				if (!disposed) {
					reloadFromDisk();
				}
			});
			// 双保险：平台内置 Save All 只保存 Document，这里补上画布的内存模型
			saveAllHook = new EditorSaveAllHook(project, () -> {
				if (modified) {
					saveDocument();
				}
			});
		} else {
			fileSync = null;
			saveAllHook = null;
		}
	}

	/**
	 * 延迟初始化 DataChartView,只在真正需要显示时才创建
	 */
	private void ensureInitialized() {
		if (!initialized) {
			synchronized (this) {
				if (!initialized) {
					// ---------- Board 页 ----------
					dataView = new DataChartView(project);
					// 看板内容变更时标记文件为已修改
					dataView.setBoardChangeListener(() -> setModified(true));
					// Command+S / Ctrl+S 保存
					dataView.setSaveListener(this::saveDocument);
					// 暴露当前文件名（用于导出 PDF / 图片的默认文件名）
					dataView.setBaseFileName(resolveBaseFileName());

					// ---------- Text 页（JSON 高亮） ----------
					jsonPanel = new DataChartJsonPanel(project, file, () -> setModified(true));

					// ---------- 内部页签：Board 在前、默认选中 ----------
					// 为什么不用两个 FileEditorProvider 做成 IDE 级的两个 Tab：
					// 平台不保证多个自定义 provider 的顺序（PLACE_BEFORE/AFTER_DEFAULT_EDITOR
					// 没有任何平台代码处理、EP 的 order 属性实测也无效、provider 还是协程并发创建），
					// 放到编辑器内部就能 100% 控制顺序与默认页。
					tabs = JBTabsFactory.createTabs(project, this);
					boardTab = new TabInfo(dataView.getRootComponent())
							.setText(DataChartBundle.message("DataChart.editor.tab.board"));
					textTab = new TabInfo(jsonPanel.getComponent())
							.setText(DataChartBundle.message("DataChart.editor.tab.text"));
					tabs.addTab(boardTab);
					tabs.addTab(textTab);
					tabs.addListener(new TabsListener() {
						@Override
						public void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
							// 切换前先保存"离开的那一页"，保证另一页看到的是同一份内容
							saveTab(oldSelection);
							if (newSelection == textTab && jsonPanel != null) {
								jsonPanel.syncFromDiskIfClean();
							}
						}
					});
					tabs.select(boardTab, false);
					editorPanel.add(tabs.getComponent(), BorderLayout.CENTER);

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
				// dispose 后丢弃加载结果，避免重新填充已清理的看板（内存泄漏/悬空引用）
				if (disposed) {
					return;
				}
				try {
					if (loaded != null && !loaded.trim().isEmpty()) {
						dataView.loadFromJson(loaded);
						// 磁盘上若还是历史遗留的紧凑 JSON，顺手重排成多行（只改空白）
						prettifyFileIfNeeded(loaded);
					}
				} finally {
					// 2026-09-24 调整：loading 挪到 loadFromJson 之后解除。
					// 否则重建看板时的变更回调会把"刚打开的文件"直接标记成已修改
					loading = false;
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
		// IDE 层面的编辑器 Tab 名用文件名；Board / Text 是编辑器内部的页签（见 ensureInitialized）
		return file == null ? DataChartBundle.message("DataChart.editor.tab.board") : file.getName();
	}

	/**
	 * 丢弃内存中的未保存修改，重新从磁盘加载画布
	 * （Text Tab 保存、Git 切分支 / pull、其它工具改盘后由 {@link EditorFileSync} 调用）。
	 */
	private void reloadFromDisk() {
		if (disposed || dataView == null) {
			return;
		}
		// 先清修改状态（此时 loading 为 false，setModified 才会生效），再重新加载
		setModified(false);
		loadFromFile();
	}

	/**
	 * 磁盘内容若是紧凑（单行）JSON，则重排为多行写回，让 Text Tab 打开即是格式化 JSON。
	 *
	 * <p>2026-09-24 新增：只重排空白，不改字段顺序与取值（见 {@link ChartJsonUtil#prettifyText}）。
	 * 文本不是合法 JSON、已经是格式化版本、或 Text Tab 里有未保存修改时，都不动文件。</p>
	 */
	private void prettifyFileIfNeeded(String raw) {
		if (raw == null || raw.isEmpty() || file == null || fileSync == null) {
			return;
		}
		// Text Tab 正在编辑（Document 有未保存修改）时不要插一脚，避免覆盖用户输入
		try {
			Document document = FileDocumentManager.getInstance().getDocument(file);
			if (document != null
					&& FileDocumentManager.getInstance().isDocumentUnsaved(document)) {
				return;
			}
		} catch (Exception e) {
			return;
		}
		String pretty = ChartJsonUtil.prettifyText(raw, ChartJsonUtil.resolveIndentSize(project));
		if (pretty == null || pretty.equals(raw)) {
			return; // 不是合法 JSON，或已经是格式化版本
		}
		try {
			WriteCommandAction.runWriteCommandAction(project, () -> {
				try {
					fileSync.beginSave();
					EditorFileSync.writeContent(file, pretty, this);
					lastSavedStamp = file.getModificationStamp();
					LOG.info("已把紧凑的 .datachart 重排为多行 JSON: " + file.getName());
				} catch (Exception e) {
					LOG.warn("重排 .datachart 格式失败: " + file.getName(), e);
				} finally {
					fileSync.endSave(file.getModificationStamp());
				}
			});
		} catch (Exception e) {
			LOG.warn("重排 .datachart 格式异常", e);
		}
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
	 * 保存"当前显示的那一页"（Cmd/Ctrl+S、平台 Save All、关闭编辑器时触发）。
	 */
	public void saveDocument() {
		saveTab(tabs == null ? boardTab : tabs.getSelectedInfo());
	}

	/** 保存指定页签对应视图的内容；null 视作 Board（页签尚未创建时）。 */
	private void saveTab(@Nullable TabInfo tab) {
		if (tab != null && tab == textTab) {
			if (jsonPanel != null && jsonPanel.isModified()) {
				jsonPanel.save();
				setModified(false);
			}
			return;
		}
		saveBoard();
	}

	/**
	 * 保存看板内容到文件。
	 *
	 * <p>2026-08-27：写入失败不再静默吞掉，改为 LOG + 气泡通知；
	 * 成功后记录文件修改戳，供 dispose 外部修改检测使用。</p>
	 */
	private void saveBoard() {
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
		final boolean[] ok = {false};
		try {
			// 序列化看板为 JSON
			final String json = dataView.serializeToJson();
			// 2026-09-24：写盘统一走 EditorFileSync.writeContent ——
			// 它同时更新 VFS 与 Text Tab 背后的 Document，避免 IDE 内置 SaveAll
			// 之后拿旧 Document 把刚保存的内容覆盖回旧版本。
			WriteCommandAction.runWriteCommandAction(project, () -> {
				try {
					if (fileSync != null) {
						fileSync.beginSave();
					}
					EditorFileSync.writeContent(file, json, this);
					lastSavedStamp = file.getModificationStamp();
					ok[0] = true;
				} catch (Exception e) {
					LOG.warn("保存 .datachart 文件失败: " + file.getName(), e);
				} finally {
					if (fileSync != null) {
						fileSync.endSave(file.getModificationStamp());
					}
				}
			});
		} catch (Exception e) {
			LOG.warn("保存 .datachart 序列化失败", e);
			NotificationUtil.error(DataChartBundle.message("DataChart.notify.save.failed"),
					DataChartBundle.message("DataChart.editor.save.serializeFailed", e.getMessage()));
			return;
		}
		if (!ok[0]) {
			NotificationUtil.error(DataChartBundle.message("DataChart.notify.save.failed"),
					DataChartBundle.message("DataChart.editor.save.writeFailed", file.getName()));
			return;
		}
		// 保存成功后重置修改状态（文件名恢复，星号消失）
		setModified(false);
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
		if (fileSync != null) {
			fileSync.dispose();
		}
		if (saveAllHook != null) {
			saveAllHook.dispose();
		}
		if (jsonPanel != null) {
			jsonPanel.dispose();
		}
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

	static String getModifiedPropertyName() {
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