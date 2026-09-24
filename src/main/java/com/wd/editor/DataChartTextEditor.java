package com.wd.editor;

import com.intellij.json.JsonFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.EditorCustomization;
import com.intellij.ui.ErrorStripeEditorCustomization;
import com.wd.i18n.DataChartBundle;
import java.awt.BorderLayout;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.nio.charset.StandardCharsets;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;

/**
 * .datachart 的 Text Tab：带 <b>JSON 高亮 / 格式化</b>的文本编辑器，用于查看 / 编辑原始 JSON。
 *
 * <p><b>为什么不直接用平台默认文本编辑器</b>：.datachart 的语言是本插件自定义的
 * {@link DataChart}（没有注册词法 / 高亮），所以平台的 Text Tab 是纯黑白文本。
 * 这里按 YamlHelper 的做法，用一个 {@link JsonFileType} 的
 * {@link LightVirtualFile} 承载内容，编辑器因此拿到 IDEA 自带的 JSON 能力：
 * 语法高亮、折叠、括号匹配、Structure View，以及 {@code Ctrl+Alt+L} 的 JSON 格式化
 * 与 JSON 语法校验。</p>
 *
 * <p>内容与真实 .datachart 的同步复用与 Board Tab 相同的机制：
 * {@link EditorFileSync}（外部改动 → 重新加载；写盘统一走
 * {@link EditorFileSync#writeContent}）+ {@link EditorSaveAllHook}（平台 Cmd/Ctrl+S）。</p>
 *
 * @author lww
 */
public class DataChartTextEditor extends UserDataHolderBase implements FileEditor {

	private static final Logger LOG = Logger.getInstance(DataChartTextEditor.class);

	private final Project project;
	/** 真实的 .datachart 文件（读写目标） */
	private final VirtualFile file;
	/** 仅为提供 JSON 高亮而存在的虚拟文件（不落盘） */
	private final LightVirtualFile lightFile;
	private final Document document;
	private final Editor editor;
	private final JPanel panel;
	private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);
	private final EditorFileSync fileSync;
	private final EditorSaveAllHook saveAllHook;

	private boolean modified = false;
	/** 程序性刷新内容中（外部同步）：此时 document 变更不算用户修改 */
	private boolean loading = false;
	private volatile boolean disposed = false;
	/** 最后一次写盘 / 加载时的文件戳，用于 dispose 时判断磁盘是否被外部改过 */
	private long lastSavedStamp = -1;

	public DataChartTextEditor(Project project, VirtualFile file) {
		this.project = project;
		this.file = file;
		String text = readFileText();
		this.lastSavedStamp = file.getModificationStamp();

		// 用 JsonFileType 的 LightVirtualFile 承载内容 → 编辑器按 JSON 建立高亮
		// （参考 YamlHelper 的 MyEditorFactory#createEditor）
		this.lightFile = new LightVirtualFile(
				file.getNameWithoutExtension() + ".json", JsonFileType.INSTANCE, text);
		this.document = createJsonDocument(text);
		this.editor = createEditor(document);
		this.panel = new JPanel(new BorderLayout());
		this.panel.add(editor.getComponent(), BorderLayout.CENTER);

		document.addDocumentListener(new DocumentListener() {
			@Override
			public void documentChanged(@NotNull DocumentEvent event) {
				if (loading) {
					return;
				}
				setModified(true);
			}
		}, this);

		// 与 Board Tab 相同的一套同步机制
		this.fileSync = new EditorFileSync(project, file, this, () -> modified, this::reloadFromDisk);
		this.saveAllHook = new EditorSaveAllHook(project, () -> {
			if (modified) {
				saveDocument();
			}
		});
	}

	/**
	 * 准备 Document：优先走 PSI（这样 Ctrl+Alt+L 格式化、JSON 校验、Structure View 才可用）；
	 * PSI 建立失败时退回普通文档（高亮仍在，只是少了依赖 PSI 的功能）。
	 */
	private Document createJsonDocument(String text) {
		return ReadAction.compute(() -> {
			try {
				PsiFile psiFile = PsiManager.getInstance(project).findFile(lightFile);
				if (psiFile != null) {
					Document psiDocument = PsiDocumentManager.getInstance(project).getDocument(psiFile);
					if (psiDocument != null) {
						return psiDocument;
					}
				}
			} catch (Throwable t) {
				LOG.warn("为 Text Tab 建立 JSON PSI 文档失败，退回普通文档", t);
			}
			return EditorFactory.getInstance().createDocument(text);
		});
	}

	private Editor createEditor(Document document) {
		Editor created = EditorFactory.getInstance()
				.createEditor(document, project, lightFile, false, EditorKind.MAIN_EDITOR);
		EditorSettings settings = created.getSettings();
		settings.setLineNumbersShown(true);
		settings.setIndentGuidesShown(true);
		settings.setFoldingOutlineShown(true);
		settings.setCaretRowShown(true);
		// 长 JSON 值（如 aiGuide）软换行，避免横向滚动
		settings.setUseSoftWraps(true);
		if (created instanceof EditorEx) {
			EditorEx editorEx = (EditorEx) created;
			// 纯数据文件：关闭错误条纹与拼写检查
			ErrorStripeEditorCustomization.DISABLED.customize(editorEx);
			EditorCustomization disabledSpellChecking =
					SpellCheckingEditorCustomizationProvider.getInstance().getDisabledCustomization();
			if (disabledSpellChecking != null) {
				disabledSpellChecking.customize(editorEx);
			}
		}
		return created;
	}

	// ---------- 保存 / 同步 ----------

	/**
	 * 保存：把编辑器内容写回 .datachart
	 * （Cmd/Ctrl+S、平台 Save All、关闭编辑器时触发）。
	 */
	public void saveDocument() {
		if (disposed || file == null || !file.isValid()) {
			return;
		}
		final String text = document.getText();
		try {
			WriteCommandAction.runWriteCommandAction(project, () -> {
				try {
					fileSync.beginSave();
					EditorFileSync.writeContent(file, text, this);
					lastSavedStamp = file.getModificationStamp();
				} catch (Exception e) {
					LOG.warn("保存 .datachart 文本失败: " + file.getName(), e);
				} finally {
					fileSync.endSave(file.getModificationStamp());
				}
			});
			setModified(false);
		} catch (Exception e) {
			LOG.warn("保存 .datachart 文本异常", e);
		}
	}

	/** 磁盘内容被外部改动（Board Tab 保存、Git 切分支等）：重新加载。 */
	private void reloadFromDisk() {
		if (disposed) {
			return;
		}
		String text = readFileText();
		if (text == null) {
			return;
		}
		SwingUtilities.invokeLater(() -> {
			if (disposed) {
				return;
			}
			applyText(text, true);
		});
	}

	/**
	 * 把磁盘文本写入编辑器。
	 *
	 * @param resetModified true = 以磁盘为准的刷新（清除修改标记）
	 */
	private void applyText(String text, boolean resetModified) {
		loading = true;
		try {
			if (!text.equals(document.getText())) {
				WriteCommandAction.runWriteCommandAction(project, () -> document.setText(text));
			}
			lastSavedStamp = file.getModificationStamp();
		} catch (Exception e) {
			LOG.warn("刷新 Text Tab 内容失败: " + file.getName(), e);
		} finally {
			loading = false;
		}
		if (resetModified) {
			// loading 已复位，这里才能真正通知 IDE 清除「已修改」标记
			setModified(false);
		}
	}

	private void setModified(boolean value) {
		if (loading) {
			return;
		}
		boolean oldValue = modified;
		if (oldValue == value) {
			return;
		}
		modified = value;
		propertyChangeSupport.firePropertyChange(
				DataChartEditor.getModifiedPropertyName(), oldValue, value);
	}

	private String readFileText() {
		try {
			return ReadAction.compute(() -> {
				try {
					return new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
				} catch (Exception e) {
					LOG.warn("读取 .datachart 失败: " + file.getName(), e);
					return null;
				}
			});
		} catch (Exception e) {
			LOG.warn("读取 .datachart 异常: " + file.getName(), e);
			return null;
		}
	}

	// ---------- FileEditor ----------

	@Override
	public @NotNull JComponent getComponent() {
		return panel;
	}

	@Override
	public @NotNull JComponent getPreferredFocusedComponent() {
		return editor.getContentComponent();
	}

	@Override
	public @NotNull String getName() {
		// 与 Board Tab 配对的自定义 Tab 名（平台默认文本编辑器已被 HIDE_DEFAULT_EDITOR 隐藏）
		return DataChartBundle.message("DataChart.editor.tab.text");
	}

	@Override
	public void setState(@NotNull FileEditorState state) {
		// 文本编辑器无需额外恢复状态（caret / 滚动由编辑器自身维护）
	}

	@Override
	public boolean isModified() {
		return modified;
	}

	@Override
	public boolean isValid() {
		return !disposed && file != null && file.isValid();
	}

	@Override
	public @NotNull VirtualFile getFile() {
		return file;
	}

	@Override
	public void selectNotify() {
		if (disposed) {
			return;
		}
		// 切到本 Tab 时兜底同步一次（正常路径由 EditorFileSync 的 VFS 监听完成）：
		// 若用户在当前 Tab 有未保存修改，则不动他的输入
		if (!modified) {
			String text = readFileText();
			if (text != null && !text.equals(document.getText())) {
				applyText(text, true);
			}
		}
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
	public void dispose() {
		disposed = true;
		// 与 Board Tab 一致的策略：关闭时若仍有未保存修改，
		// 且磁盘自上次保存后没有被外部改过，则自动落盘，避免静默丢失
		if (modified) {
			boolean changedExternally = false;
			try {
				changedExternally = lastSavedStamp >= 0
						&& file.getModificationStamp() != lastSavedStamp;
			} catch (Exception e) {
				LOG.warn("dispose 时读取文件修改戳失败", e);
			}
			if (changedExternally) {
				LOG.warn("文件在磁盘上已被外部修改，放弃自动保存以避免覆盖: " + file.getName());
			} else {
				saveDocument();
			}
		}
		fileSync.dispose();
		saveAllHook.dispose();
		try {
			EditorFactory.getInstance().releaseEditor(editor);
		} catch (Exception e) {
			LOG.warn("释放 Text Tab 编辑器失败", e);
		}
		panel.removeAll();
	}
}
