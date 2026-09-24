package com.wd.editor;

import com.intellij.json.JsonFileType;
import com.intellij.openapi.Disposable;
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.EditorCustomization;
import com.intellij.ui.ErrorStripeEditorCustomization;
import java.awt.BorderLayout;
import java.nio.charset.StandardCharsets;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;

/**
 * .datachart 编辑器内部页签的「Text」页：带 <b>JSON 高亮 / 格式化</b>的文本编辑器。
 *
 * <p><b>为什么用 {@link LightVirtualFile} + {@link JsonFileType}</b>：.datachart 的语言是插件自定义的
 * {@link DataChart}（没注册词法 / 高亮），而"属性键紫色、字符串绿色"这类观感需要 <b>JSON 语言本身</b>
 * 的全套机制（token 高亮 + PSI 层的高亮访客）。把内容放进一个 JSON 类型的虚拟文件里，
 * 编辑器就直接拿到 IDEA 原生的 JSON 体验：语法高亮、括号匹配、折叠、Structure View，
 * 以及 {@code Ctrl+Alt+L} 的 JSON 格式化与语法校验。（参考 YamlHelper 的 {@code MyEditorFactory}。）</p>
 *
 * <p>页签本身由外层 {@link DataChartEditor} 用 {@code JBTabs} 管理；本类只负责
 * "内容 + 编辑器 + 与真实文件同步"，保存时机（Cmd+S / 切页签 / 关闭编辑器）由外层驱动。</p>
 *
 * @author lww
 */
public class DataChartJsonPanel implements Disposable {

	private static final Logger LOG = Logger.getInstance(DataChartJsonPanel.class);

	private final Project project;
	/** 真实的 .datachart 文件（读写目标） */
	private final VirtualFile file;
	/** 仅为提供 JSON 高亮而存在的虚拟文件（不落盘） */
	private final LightVirtualFile lightFile;
	private final Document document;
	private final Editor editor;
	private final JPanel panel;
	private final EditorFileSync fileSync;
	/** 内容被修改时通知外层 FileEditor（触发 PROP_MODIFIED，让 IDE 显示"已修改"） */
	private final Runnable modificationListener;

	private boolean modified = false;
	/** 程序性刷新内容中（外部同步）：此时 document 变更不算用户修改 */
	private boolean loading = false;
	private volatile boolean disposed = false;
	/** 最后一次写盘 / 加载时的文件戳，用于关闭时判断磁盘是否被外部改过 */
	private long lastSavedStamp = -1;

	DataChartJsonPanel(@NotNull Project project, @NotNull VirtualFile file,
			@NotNull Runnable modificationListener) {
		this.project = project;
		this.file = file;
		this.modificationListener = modificationListener;

		String text = readFileText();
		this.lastSavedStamp = file.getModificationStamp();

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

		// 外部改动（Board 页保存、Git 切分支、其它工具改盘）→ 重新加载
		this.fileSync = new EditorFileSync(project, file, this, () -> modified, this::reloadFromDisk);
	}

	// ---------- 对外 ----------

	@NotNull
	JComponent getComponent() {
		return panel;
	}

	@NotNull
	JComponent getPreferredFocusedComponent() {
		return editor.getContentComponent();
	}

	boolean isModified() {
		return modified;
	}

	/**
	 * 把编辑器里的文本写回 .datachart。
	 * 由外层 FileEditor 在 Cmd+S、切换页签、关闭编辑器时调用。
	 */
	void save() {
		if (disposed || !file.isValid()) {
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

	/**
	 * 切到本页时兜底同步一次磁盘内容
	 * （正常路径由 {@link EditorFileSync} 的 VFS 监听完成；本页有未保存修改时不动用户输入）。
	 */
	void syncFromDiskIfClean() {
		if (disposed || modified) {
			return;
		}
		String text = readFileText();
		if (text != null && !text.equals(document.getText())) {
			applyText(text, true);
		}
	}

	// ---------- 内部 ----------

	private void setModified(boolean value) {
		if (loading) {
			return;
		}
		boolean oldValue = modified;
		if (oldValue == value) {
			return;
		}
		modified = value;
		if (modificationListener != null) {
			modificationListener.run();
		}
	}

	/** 磁盘内容被外部改动：重新加载（丢弃内存中的文本）。 */
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
			LOG.warn("刷新 Text 页内容失败: " + file.getName(), e);
		} finally {
			loading = false;
		}
		if (resetModified) {
			setModified(false);
		}
	}

	/**
	 * 准备 Document：优先走 PSI（这样 {@code Ctrl+Alt+L} 格式化、JSON 校验、Structure View 才可用）；
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
				LOG.warn("为 Text 页建立 JSON PSI 文档失败，退回普通文档", t);
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

	@Override
	public void dispose() {
		disposed = true;
		fileSync.dispose();
		try {
			EditorFactory.getInstance().releaseEditor(editor);
		} catch (Exception e) {
			LOG.warn("释放 Text 页编辑器失败", e);
		}
		panel.removeAll();
	}
}
