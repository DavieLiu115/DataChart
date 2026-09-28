package com.wd.editor;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author lww
 */
public class DataChartEditorProvider implements FileEditorProvider, DumbAware {

	@Override
	public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
		// 根据文件扩展名判断是否接受该文件
		return DataToolsFileType.EXTENSION.equalsIgnoreCase(file.getExtension());
	}

	@Override
	public @NotNull FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
		// 创建自定义编辑器
		return new DataChartEditor(project, file);
	}

	@Override
	public @NotNull String getEditorTypeId() {
		// 唯一标识符
		return "DataChartEditorProvider";
	}

	@Override
	public @NotNull FileEditorPolicy getPolicy() {
		// 2026-09-24：Board / Text 已下沉为同一个 FileEditor 内部的页签（JBTabs），
		// 所以这里让本 provider 独占 IDE 层面的编辑器 —— 隐藏平台自带的文本编辑器等，
		// 避免 IDE 层再冒出一个多余的 tab（平台不保证多 provider 的 Tab 顺序，见 DEVELOPMENT_GUIDE 第 50 节）。
		return POLICY;
	}

	/**
	 * {@code FileEditorPolicy.HIDE_OTHER_EDITORS} 的<b>反射</b>结果（拿不到时退化为 {@link FileEditorPolicy#NONE}）。
	 *
	 * <p><b>为什么不直接写 {@code return FileEditorPolicy.HIDE_OTHER_EDITORS;}</b>（2026-09-28）：</p>
	 * <ul>
	 *   <li>该常量在 232~242 都标着 {@code @ApiStatus.Experimental}，Plugin Verifier 每个版本都会报一条
	 *       {@code Experimental field usage: FileEditorPolicy.HIDE_OTHER_EDITORS}（263 已转正、不再报）；</li>
	 *   <li>更实际的是<b>风险方向</b>：硬引用（getstatic）下，平台一旦改名/删除这个常量，
	 *       verifier 会报"字段找不到"级别的**兼容性问题**，而 {@code getPolicy()} 处在
	 *       "创建编辑器"的必经链路上 —— 会直接 {@code NoSuchFieldError}，整个编辑器都用不了；</li>
	 *   <li>反射则没有二进制依赖：取不到就退化为 {@link FileEditorPolicy#NONE}
	 *       （平台文本编辑器会跟着出现，即"多一个 tab"这种外观退化），编辑器创建永远不会失败。</li>
	 * </ul>
	 *
	 * <p>语义依据（javap 核实）：{@code FileEditorProviderManagerImpl.postProcessResult} 里
	 * 该 policy 的分支谓词是 {@code it.getPolicy() != HIDE_OTHER_EDITORS}，即"移除除我之外的所有 provider"，
	 * {@code PsiAwareTextEditorProvider} 自然被移除。顺带记一笔：{@code HIDE_DEFAULT_EDITOR} 的分支谓词是
	 * {@code it instanceof DefaultPlatformFileEditorProvider}，**管不到**文本编辑器，两者不可混用。</p>
	 */
	private static final FileEditorPolicy POLICY = resolveHideOtherEditorsPolicy();

	/** 反射取 {@code FileEditorPolicy.HIDE_OTHER_EDITORS}，失败则返回 {@link FileEditorPolicy#NONE}。 */
	private static FileEditorPolicy resolveHideOtherEditorsPolicy() {
		try {
			Object policy = FileEditorPolicy.class.getField("HIDE_OTHER_EDITORS").get(null);
			return policy instanceof FileEditorPolicy ? (FileEditorPolicy) policy : FileEditorPolicy.NONE;
		} catch (Throwable t) {
			// 平台没有该常量（老版本或将来被改名）：退化为"显示所有编辑器"，功能不受影响
			return FileEditorPolicy.NONE;
		}
	}

}