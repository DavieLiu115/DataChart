package com.wd.db;

import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDTarget;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.awt.Point;
import javax.swing.JComponent;

/**
 * 看板区域的拖拽目标处理器
 *
 * <p>接收来自 Database 工具窗口的表格拖拽，提取表元信息并回调给看板绘制。
 * 所有 Database 相关操作均通过 {@link DatabaseTableMetadataFetcher} 反射完成，
 * 避免在未安装 Database 插件的 IDE 上出现 ClassNotFoundException。</p>
 *
 * <p>拖拽对象判定流程（反射）：</p>
 * <ol>
 *   <li>从 {@code DnDEvent.getAttachedObject()} 获取拖拽对象</li>
 *   <li>判断对象是否为 {@code DbTable}（实现 {@code DbElement}）</li>
 *   <li>通过 {@code DbElement.getDataSource().getName()} 获取数据源名</li>
 *   <li>通过 {@code DbNamedElement.getName()} 获取表名</li>
 *   <li>调用 {@link TableMetadataFetcher#fetchTableInfo} 获取完整元信息并回调</li>
 * </ol>
 *
 * @author lww
 */
public class TableDropHandler implements DnDTarget {

	private static final Logger LOG = Logger.getInstance(TableDropHandler.class);

	/** 回调接口：收到表元信息后由看板处理 */
	public interface TableDropCallback {
		/**
		 * 表被拖入看板
		 *
		 * @param info      表元信息（含字段、主键等）
		 * @param dropPoint 拖放位置（看板坐标）
		 */
		void onTableDropped(TableInfo info, Point dropPoint);
	}

	private final Project project;
	private final TableMetadataFetcher fetcher;
	private final TableDropCallback callback;

	/** 缓存 Database 反射类 */
	private static Class<?> dbElementClass;
	private static Class<?> dbNamedElementClass;
	private static boolean dbClassesReady = false;

	/**
	 * @param project  当前工程
	 * @param callback 拖拽回调
	 */
	public TableDropHandler(Project project, TableDropCallback callback) {
		this.project = project;
		this.fetcher = TableMetadataService.getInstance(project).getFetcher();
		this.callback = callback;
	}

	/**
	 * 注册到指定组件（看板）
	 */
	public void registerTo(JComponent component) {
		try {
			DnDManager.getInstance().registerTarget(this, component);
			LOG.info("TableDropHandler registered to " + component.getClass().getSimpleName());
		} catch (Exception e) {
			LOG.warn("Failed to register TableDropHandler", e);
		}
	}

	/**
	 * 取消注册
	 */
	public void unregisterFrom(JComponent component) {
		try {
			DnDManager.getInstance().unregisterTarget(this, component);
		} catch (Exception e) {
			LOG.warn("Failed to unregister TableDropHandler", e);
		}
	}

	@Override
	public boolean update(DnDEvent event) {
		// 判断拖拽对象是否为数据库表
		if (isDbTable(event.getAttachedObject())) {
			event.setDropPossible(true, "拖放到看板");
			return true;
		}
		return false;
	}

	@Override
	public void drop(DnDEvent event) {
		Object attached = event.getAttachedObject();
		if (!isDbTable(attached)) {
			return;
		}
		processTable(attached, event.getPointOn(null));
	}

	@Override
	public void cleanUpOnLeave() {
		// 无需清理
	}

	/**
	 * 处理拖入的数据库表
	 */
	private void processTable(Object dbTable, Point dropPoint) {
		try {
			String datasourceName = getDataSourceName(dbTable);
			String tableName = getName(dbTable);
			if (tableName == null || tableName.isEmpty()) {
				LOG.warn("Dropped DbTable has no name");
				return;
			}

			LOG.info("Table dropped: datasource=" + datasourceName + ", table=" + tableName);

			// 查询元信息（同步调用；若需异步可改为后台任务）
			TableInfo info = fetcher.fetchTableInfo(project, datasourceName, tableName);
			if (info == null) {
				LOG.warn("No metadata found for table: " + tableName);
				return;
			}
			if (callback != null) {
				callback.onTableDropped(info, dropPoint);
			}
		} catch (Exception e) {
			LOG.warn("Failed to process dropped table", e);
		}
	}

	// ========== 反射工具 ==========

	/**
	 * 判断对象是否为数据库表（DbTable）
	 */
	private static boolean isDbTable(Object obj) {
		if (obj == null || !ensureDbClasses()) {
			return false;
		}
		return dbElementClass.isInstance(obj);
	}

	/**
	 * 获取表所在数据源名称
	 */
	private String getDataSourceName(Object dbTable) {
		try {
			Object dataSource = dbElementClass.getMethod("getDataSource").invoke(dbTable);
			if (dataSource != null) {
				Object name = dataSource.getClass().getMethod("getName").invoke(dataSource);
				return name == null ? "" : name.toString();
			}
		} catch (Exception e) {
			LOG.warn("getDataSourceName failed", e);
		}
		return "";
	}

	/**
	 * 获取表名
	 */
	private String getName(Object dbTable) {
		try {
			Object name = dbNamedElementClass.getMethod("getName").invoke(dbTable);
			return name == null ? null : name.toString();
		} catch (Exception e) {
			LOG.warn("getName failed", e);
			return null;
		}
	}

	/**
	 * 加载并缓存 Database 的 DbElement / DbNamedElement 类
	 */
	private static synchronized boolean ensureDbClasses() {
		if (dbClassesReady) {
			return true;
		}
		try {
			dbElementClass = Class.forName("com.intellij.database.psi.DbElement");
			dbNamedElementClass = Class.forName("com.intellij.database.psi.DbNamedElement");
			dbClassesReady = true;
		} catch (ClassNotFoundException e) {
			LOG.warn("Database plugin classes not available for DnD", e);
			dbClassesReady = false;
		}
		return dbClassesReady;
	}
}