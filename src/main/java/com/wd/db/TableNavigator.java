package com.wd.db;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * 表的「跳转」入口：复用 Database 插件自带的原生 Action。
 *
 * <p>背景：IDEA 的数据库 ER 图（Diagrams）右键菜单里的
 * {@code Go To > Data / Go to DDL / Database Explorer} 并不是插件自己写的跳转逻辑，
 * 而是把 Database 插件已有的 Action 组装进菜单（见 {@code DbDiagramProvider} 与
 * {@code DatabasePlugin.xml} 中的 {@code DbDiagrams.SourceActionsGroup.GoTo} 组）。
 * 这些 Action 只认 {@code CommonDataKeys.PSI_ELEMENT}，因此本类的职责是：</p>
 * <ol>
 *   <li>把 {@code .datachart} 中持久化的「数据源 + 表名」现场解析回 PSI 元素
 *       （{@code DbElement}，见 {@link DatabaseTableMetadataFetcher#resolveDbElement}）；</li>
 *   <li>构造携带该 PSI 元素的 {@link DataContext}，用 {@code action.update()} 判断可用性后
 *       调用 {@code actionPerformed()}。</li>
 * </ol>
 *
 * <p>不缓存 PSI 元素：数据源同步 / IDE 重启后元素会失效，每次跳转都重新解析，
 * 这样旧 {@code .datachart} 文件与重启场景都能正常工作。</p>
 *
 * <p>未安装 / 未启用 Database 插件时，{@link #isActionAvailable(String)} 返回 false，
 * 调用方据此隐藏或禁用菜单项。</p>
 *
 * @author lww
 */
public final class TableNavigator {

	/** 查看数据（图中显示为 "Data"），对应 {@code OpenEditorAction$OpenDataAction} */
	public static final String ACTION_OPEN_DATA = "Jdbc.OpenEditor.Data";

	/** 跳到 DDL，对应 {@code OpenEditorAction$OpenDDLAction} */
	public static final String ACTION_OPEN_DDL = "Jdbc.OpenEditor.DDL";

	/** 在 Database 工具窗口中定位，对应 {@code SelectInDatabaseViewAction} */
	public static final String ACTION_SELECT_IN_DATABASE_VIEW = "sql.SelectInDatabaseView";

	/** 查找引用（平台内置动作） */
	public static final String ACTION_FIND_USAGES = "FindUsages";

	private TableNavigator() {
	}

	/**
	 * 判断指定动作当前是否可用（未安装 / 未启用 Database 插件时返回 false）。
	 *
	 * @param actionId 动作 id，取值见本类常量
	 */
	public static boolean isActionAvailable(@Nullable String actionId) {
		if (actionId == null) {
			return false;
		}
		try {
			return ActionManager.getInstance().getAction(actionId) != null;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * 对指定表执行 Database 插件的原生跳转动作。
	 *
	 * <p>必须在 EDT 调用（由右键菜单触发）。内部会先做 PSI 解析与可用性判断，
	 * 任一环节失败都返回 false，由调用方给出提示。</p>
	 *
	 * @param project  当前工程
	 * @param info     表元信息（提供数据源名与表名，用于现场解析 PSI 元素）
	 * @param actionId 动作 id，取值见本类常量
	 * @return 是否成功执行
	 */
	public static boolean performAction(@Nullable Project project,
			@Nullable TableInfo info, @Nullable String actionId) {
		if (project == null || project.isDisposed() || info == null || actionId == null) {
			return false;
		}
		AnAction action = ActionManager.getInstance().getAction(actionId);
		if (action == null) {
			return false; // 未安装 / 未启用 Database 插件
		}
		PsiElement element = resolvePsiElement(project, info);
		if (element == null) {
			return false; // 数据源或表已不存在（可能已被删除 / 重命名）
		}

		DataContext dataContext = SimpleDataContext.builder()
				.add(CommonDataKeys.PROJECT, project)
				.add(CommonDataKeys.PSI_ELEMENT, element)
				.build();

		AnActionEvent event = AnActionEvent.createFromDataContext(
				ActionPlaces.POPUP, action.getTemplatePresentation().clone(), dataContext);

		// update() 内部通常也要读 PSI，包在 read action 中；
		// actionPerformed() 放到 read action 外面执行（它可能触发写操作 / 弹窗）
		Boolean enabled = ReadAction.compute(() -> {
			action.update(event);
			return event.getPresentation().isEnabled();
		});
		if (enabled == null || !enabled) {
			return false;
		}

		action.actionPerformed(event);
		return true;
	}

	/**
	 * 把持久化的表信息解析为 Database 插件的 PSI 元素（{@code DbElement}）。
	 *
	 * <p>解析失败（数据源不存在、表被删、未装 Database 插件）返回 null。</p>
	 */
	@Nullable
	private static PsiElement resolvePsiElement(Project project, TableInfo info) {
		Object element;
		try {
			element = ReadAction.compute(() -> {
				TableMetadataFetcher fetcher = TableMetadataService.getInstance(project).getFetcher();
				if (fetcher instanceof DatabaseTableMetadataFetcher) {
					return ((DatabaseTableMetadataFetcher) fetcher)
							.resolveDbElement(project, info.getDatasourceName(), info.getName());
				}
				return null;
			});
		} catch (Exception e) {
			return null;
		}
		if (element instanceof PsiElement) {
			PsiElement psi = (PsiElement) element;
			if (psi.isValid()) {
				return psi;
			}
		}
		return null;
	}
}
