package com.wd.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
 *
 * <p>⚠️ <b>为什么 Topic 要反射取</b>（2026-09-28）：
 * Plugin Verifier 在 <b>2023.2（IU-232.10335.12）</b>上报
 * {@code Field not found: FileDocumentManagerListener.TOPIC ... NoSuchFieldError} ——
 * 该字段是后来才加到平台上的（241 里是 {@code public static final Topic<...>}），
 * 而我们 {@code plugin.xml} 声明 {@code since-build="223"}，verifier 会逐个构建检查。
 * 硬引用会让 2023.2 及更早的 IDE 直接抛 {@code NoSuchFieldError}；
 * 反射则能优雅降级：拿不到 Topic 就不注册钩子（画布自身的 Cmd/Ctrl+S 保存仍然有效，
 * 这个钩子本来就是"双保险"的补充）。</p>
 */
final class EditorSaveAllHook implements Disposable {

	private static final Logger LOG = Logger.getInstance(EditorSaveAllHook.class);

	/** {@code FileDocumentManagerListener.TOPIC}；老平台上不存在该字段时为 {@code null}。 */
	@Nullable
	private static final Topic<FileDocumentManagerListener> SAVE_ALL_TOPIC = resolveSaveAllTopic();

	private final MessageBusConnection connection;

	EditorSaveAllHook(@NotNull Project project, @NotNull Runnable save) {
		connection = project.getMessageBus().connect(this);
		if (SAVE_ALL_TOPIC == null) {
			// 2023.2 及更早的平台没有这个 Topic：不注册即可，功能自动降级（不会报错）
			LOG.info("[DataChart Save] 当前平台没有 FileDocumentManagerListener.TOPIC，跳过 SaveAll 钩子");
			return;
		}
		connection.subscribe(SAVE_ALL_TOPIC, new FileDocumentManagerListener() {
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

	/**
	 * 反射取 {@code FileDocumentManagerListener.TOPIC}。
	 *
	 * @return 平台的 Topic；字段不存在（2023.2 及更早）或类型不符时返回 {@code null}
	 */
	@Nullable
	@SuppressWarnings("unchecked")
	private static Topic<FileDocumentManagerListener> resolveSaveAllTopic() {
		try {
			Object topic = FileDocumentManagerListener.class.getField("TOPIC").get(null);
			return topic instanceof Topic
					? (Topic<FileDocumentManagerListener>) topic
					: null;
		} catch (Throwable t) {
			// NoSuchFieldException 是预期情况（老平台），其余异常也只影响这个"双保险"钩子
			LOG.info("反射获取 FileDocumentManagerListener.TOPIC 失败（老平台属正常）: " + t);
			return null;
		}
	}
}
