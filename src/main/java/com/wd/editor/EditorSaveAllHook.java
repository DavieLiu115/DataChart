package com.wd.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

/**
 * 把平台内置的「Save All」（Cmd/Ctrl+S）与图形编辑器打通
 * （参考 PYYP 的 {@code EosSaveAllHook}）。
 *
 * <p><b>为什么需要它</b>：画板的改动在内存模型里、不在 {@code Document} 里，平台内置的
 * {@code SaveAllAction} 只保存 Document。启用 Text Tab 后一旦 Text Tab 有未保存内容，
 * 内置 SaveAll 就处于 enabled 状态并<b>先于</b>画布的 KeyListener 消费 Cmd/Ctrl+S，
 * 结果画板改动不落盘、还容易被旧 Document 覆盖回旧版本。</p>
 *
 * <p>这里挂在 {@link FileDocumentManagerListener#beforeAllDocumentsSaving()} 上：
 * 平台每次执行「全部保存」时，先把图形编辑器的改动写盘，与画布自己的快捷键形成双保险。</p>
 */
final class EditorSaveAllHook implements Disposable {

	private static final Logger LOG = Logger.getInstance(EditorSaveAllHook.class);

	private final MessageBusConnection connection;

	EditorSaveAllHook(@NotNull Project project, @NotNull Runnable save) {
		connection = project.getMessageBus().connect(this);
		connection.subscribe(FileDocumentManagerListener.TOPIC, new FileDocumentManagerListener() {
			@Override
			public void beforeAllDocumentsSaving() {
				LOG.info("[DataChart Save] 平台 Save All → 保存图形编辑器");
				save.run();
			}
		});
	}

	@Override
	public void dispose() {
		connection.dispose();
	}
}
