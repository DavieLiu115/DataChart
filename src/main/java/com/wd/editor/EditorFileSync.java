package com.wd.editor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoConstants;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.messages.MessageBusConnection;
import com.wd.i18n.DataChartBundle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 图形 Tab ↔ IDEA 默认 Text Tab 的同步支持（参考 PYYP 的 {@code EosEditorFileSync}）。
 *
 * <p>启用 Text Tab（{@code FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR}）后，同一个
 * .datachart 文件会同时挂着两个编辑器：本插件的图形编辑器与 IDEA 默认文本编辑器。
 * 两者共享同一份磁盘文件，必须处理两类问题：</p>
 * <ol>
 *   <li><b>外部改动</b>：在 Text Tab 保存、Git 切分支 / pull、其它工具改盘后，
 *       磁盘内容与画布模型不一致 → 监听 {@code VFS_CHANGES}，无未保存修改时静默刷新，
 *       有未保存修改时弹窗让用户决定；</li>
 *   <li><b>自己写盘</b>：只写 VFS 不会刷新 Text Tab 背后的 {@link Document}，
 *       此时按 Cmd/Ctrl+S 时 IDEA 内置 SaveAll 会拿旧 Document 再写一遍盘，
 *       把刚保存的新内容覆盖回旧版本 → 走 {@link #writeContent} 同时更新 VFS 与 Document。</li>
 * </ol>
 */
final class EditorFileSync {

	private static final Logger LOG = Logger.getInstance(EditorFileSync.class);

	private final Project project;
	private final VirtualFile file;
	/**
	 * 编辑器实例：写盘时作为 {@code requestor} 传入，用于<b>精确</b>识别「自己触发的 VFS 事件」。
	 *
	 * <p>不能只靠 {@code saving} 标志：VFS 事件是异步派发的，等它到达时 {@link #endSave(long)}
	 * 早已把 {@code saving} 复位；也不能只靠 {@code selfStamp}：保存之后往往还有第二个人
	 * （IDEA 内置 SaveAll）又写了一遍，stamp 就被顶掉了。</p>
	 */
	private final Object owner;
	/** 是否有未保存修改（由编辑器提供，实时读取）。 */
	private final BooleanSupplier unsavedSupplier;
	/** 外部变更后重新加载并刷新图形视图。 */
	private final Runnable reloadAction;
	private final MessageBusConnection connection;

	private volatile boolean saving = false;
	private volatile boolean disposed = false;
	/** 编辑器自身保存后的文件戳：用于区分「自己写盘」与「真正的外部改动」。 */
	private volatile long selfStamp = Long.MIN_VALUE;

	EditorFileSync(@NotNull Project project,
			@NotNull VirtualFile file,
			@NotNull Object owner,
			@NotNull BooleanSupplier unsavedSupplier,
			@NotNull Runnable reloadAction) {
		this.project = project;
		this.file = file;
		this.owner = owner;
		this.unsavedSupplier = unsavedSupplier;
		this.reloadAction = reloadAction;

		connection = project.getMessageBus().connect();
		connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
			@Override
			public void after(@NotNull List<? extends VFileEvent> events) {
				for (VFileEvent event : events) {
					if (!(event instanceof VFileContentChangeEvent)) {
						continue;
					}
					VirtualFile changed = event.getFile();
					if (changed != null && file.equals(changed)) {
						onContentChanged((VFileContentChangeEvent) event);
						return;
					}
				}
			}
		});
	}

	/** 编辑器自身写盘前调用，抑制本次事件。 */
	void beginSave() {
		saving = true;
	}

	/**
	 * 编辑器自身写盘后调用。
	 *
	 * @param stampAfterSave 写盘后的 {@code file.getModificationStamp()}，
	 *                       用于过滤掉自己触发的那次 content change 事件
	 */
	void endSave(long stampAfterSave) {
		selfStamp = stampAfterSave;
		saving = false;
	}

	void dispose() {
		disposed = true;
		connection.disconnect();
	}

	/** 磁盘内容被改动（含自己写盘）：先做三重过滤，剩下的才算「真正的外部改动」。 */
	private void onContentChanged(@NotNull VFileContentChangeEvent event) {
		// ① requestor 命中：编辑器自己写盘引起的事件（唯一稳定判据，异步派发也认得出来）
		if (event.getRequestor() == owner) {
			return;
		}
		// ② 正处于自己的写盘窗口内
		if (saving || disposed) {
			return;
		}
		// ③ 事件到达时文件戳仍等于自己写盘后记录的戳
		if (file.getModificationStamp() == selfStamp) {
			return;
		}
		onExternalChange();
	}

	/** 磁盘内容被外部改动：按是否有未保存修改决定自动刷新还是询问用户。 */
	private void onExternalChange() {
		if (saving || disposed) {
			return;
		}
		long stamp = file.getModificationStamp();
		if (stamp == selfStamp) {
			return;
		}
		ApplicationManager.getApplication().invokeLater(() -> {
			if (disposed || project.isDisposed() || saving) {
				return;
			}
			if (!file.isValid() || file.getModificationStamp() == selfStamp) {
				return;
			}
			if (!unsavedSupplier.getAsBoolean()) {
				// 没有未保存修改，直接同步，用户无感
				LOG.info("检测到 .datachart 外部改动，自动刷新: " + file.getName());
				reloadAction.run();
				return;
			}
			// 有未保存修改：交给用户决定，避免静默丢弃
			int choice = Messages.showYesNoDialog(project,
					DataChartBundle.message("DataChart.editor.externallyModified.message", file.getName()),
					DataChartBundle.message("DataChart.editor.externallyModified.title"),
					Messages.getQuestionIcon());
			if (choice == Messages.YES) {
				reloadAction.run();
			}
		});
	}

	/**
	 * 把序列化结果写回 .datachart。
	 *
	 * <p><b>为什么要走 Document 而不是只写 {@code setBinaryContent}</b>：文件同时挂着图形 Tab 与
	 * IDEA 默认 Text Tab（{@code PLACE_BEFORE_DEFAULT_EDITOR}），Text Tab 背后有一份
	 * {@link Document}。只写 VFS 不会刷新它，它会停留在旧内容；此时按 Cmd/Ctrl+S，
	 * IDEA 内置的 SaveAll 就会拿这份旧 Document 再写一遍盘，把刚保存的新内容<b>覆盖回旧版本</b>
	 * （表现为「保存后内容被回滚」）。</p>
	 *
	 * <p>另外本次写盘属于「程序性保存」，不能往项目撤销栈里留记录：否则 IDEA 的 Cmd+Z
	 * 会把画板自己的撤销按键抢走，且撤销的是这次保存同步（内容被倒回旧版本）。</p>
	 *
	 * @param requestor 写盘发起者（编辑器实例），写入 VFS 事件供 {@link #onContentChanged} 识别
	 */
	static void writeContent(@NotNull VirtualFile file, @NotNull String content,
			@NotNull Object requestor) throws IOException {
		// IDEA 的 Document 内部只用 LF：传入含 CR 的文本会在 setText 时抛
		// AssertionError: Wrong line separators，这里统一规范化兜底
		String text = StringUtil.convertLineSeparators(content);
		Document doc = FileDocumentManager.getInstance().getDocument(file);

		setUndoDisabled(file, doc, true);
		try {
			// ① 直接写盘（带 requestor，供 onContentChanged 精确识别自触发事件）
			file.setBinaryContent(text.getBytes(StandardCharsets.UTF_8), -1L, -1L, requestor);
			// ② 让 Text Tab 背后的 Document 追上磁盘，避免 IDEA 内置 SaveAll 之后
			//    拿这份旧 Document 把我们刚写的内容覆盖回旧版本
			if (doc != null && !text.equals(doc.getText())) {
				doc.setText(text);
				FileDocumentManager.getInstance().saveDocument(doc);
			}
		} finally {
			setUndoDisabled(file, doc, false);
		}
	}

	/**
	 * 打开 / 关闭「本次改动不记入撤销栈」标记。
	 *
	 * <p>{@code UndoUtil} 提供了 {@code disableUndoFor(VirtualFile/Document)}，但<b>没有</b>
	 * VirtualFile 版的 {@code enableUndoFor}（2024.1 只有 Document 版）；查其实现可知两者都只是
	 * 往对象上挂 {@code UndoConstants.DONT_RECORD_UNDO} 标记，所以 VirtualFile 的还原只能手动清该 Key。</p>
	 *
	 * <p>⚠️ {@code UndoConstants} 与 {@code DONT_RECORD_UNDO} 在 2024.1 已被标记 {@code @Deprecated}，
	 * 但平台没有给出等价的新 API（既没有 enableUndoFor(VirtualFile)，也没有新的标记 Key），
	 * 因此只能保留调用并抑制告警 —— 一旦平台补上 enableUndoFor(VirtualFile) 就应替换。</p>
	 */
	@SuppressWarnings("deprecation")
	private static void setUndoDisabled(@Nullable VirtualFile file, @Nullable Document doc,
			boolean disabled) {
		if (file != null) {
			if (disabled) {
				UndoUtil.disableUndoFor(file);
			} else {
				file.putUserData(UndoConstants.DONT_RECORD_UNDO, null);
			}
		}
		if (doc != null) {
			if (disabled) {
				UndoUtil.disableUndoFor(doc);
			} else {
				UndoUtil.enableUndoFor(doc);
			}
		}
	}
}
