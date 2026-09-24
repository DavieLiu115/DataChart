package com.wd.editor;

import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.impl.FileEditorProviderSuppressor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * 抑制「平台自带文本编辑器」对 .datachart 生效。
 *
 * <p><b>为什么需要它</b>：平台的 {@link TextEditorProvider}（EP 里 id {@code text-editor}，
 * 实现类 {@code PsiAwareTextEditorProvider}）会为所有非二进制文件提供一个 Tab，名字固定是 "Text"，
 * 而它按文件语言（我们自定义的 {@code dataChart}）渲染 —— 那个语言没注册词法/高亮，
 * 所以它是**纯黑白文本**，看起来就是"没高亮"。</p>
 *
 * <p>本插件的编辑器（{@link DataChartEditor}）内部已经用 {@code JBTabs} 提供了 Board / Text 两个页签，
 * 其中 Text 页用 {@code LightVirtualFile + JsonFileType} 承载内容 → 完整的 IDEA JSON 高亮与格式化。
 * 因此必须把平台那个多余的文本编辑器 Tab 去掉，否则会出现两个 "Text"，且用户点到的很可能是没高亮的那个。</p>
 *
 * <p>⚠️ 为什么不用 {@code FileEditorPolicy.HIDE_OTHER_EDITORS / HIDE_DEFAULT_EDITOR}：
 * 反编译 {@code FileEditorProviderManagerImpl.postProcessResult} 可知 {@code HIDE_DEFAULT_EDITOR}
 * 只移除 {@code DefaultPlatformFileEditorProvider}；而 {@code PsiAwareTextEditorProvider} 是独立 EP 注册的，
 * 单靠 policy 挡不住。{@link FileEditorProviderSuppressor} 的 {@code isSuppressed} <b>带 project / file 参数</b>，
 * 可以只对 .datachart 精准生效，不影响其它文件。</p>
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
		// 只管平台文本编辑器；插件自己的 Board / Text 都在同一个 FileEditor 内部，不受影响
		return provider instanceof TextEditorProvider;
	}
}
