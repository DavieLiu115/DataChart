package com.wd.editor;

import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.impl.FileEditorProviderSuppressor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * 抑制「平台默认文本编辑器」对 .datachart 生效。
 *
 * <p><b>为什么需要它</b>：平台的 {@link TextEditorProvider}（注册 id {@code text-editor}，
 * 实际实现 {@code PsiAwareTextEditorProvider}）会为所有非二进制文件提供一个 Tab，
 * Tab 名固定是 "Text"。而本插件的 Text Tab 是 {@link DataChartTextEditor}（JSON 高亮），
 * 两者重名 —— 打开 .datachart 会看到 "Board | Text | Text" 三个 Tab。</p>
 *
 * <p>{@code FileEditorPolicy.HIDE_DEFAULT_EDITOR} 解决不了这个问题：平台只拿它移除
 * {@code DefaultPlatformFileEditorProvider}（反编译 {@code FileEditorProviderManagerImpl
 * .postProcessResult} 确认），动不了独立 EP 注册的 {@code TextEditorProvider}。
 * 因此改用 {@link FileEditorProviderSuppressor}（它的 {@code isSuppressed} <b>带
 * project / file 参数</b>，可以只对 .datachart 生效，不影响其它文件）。</p>
 *
 * @author lww
 */
public class DataChartTextEditorSuppressor implements FileEditorProviderSuppressor {

	@Override
	public boolean isSuppressed(@NotNull Project project, @NotNull VirtualFile file,
			@NotNull FileEditorProvider provider) {
		if (!DataToolsFileType.EXTENSION.equalsIgnoreCase(file.getExtension())) {
			return false;
		}
		// 只管平台文本编辑器；插件自己的 Board / Text provider 不受影响
		return provider instanceof TextEditorProvider;
	}
}
