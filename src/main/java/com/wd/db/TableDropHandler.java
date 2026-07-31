package com.wd.db;

import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDTarget;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
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
 *   <li>通过 {@code DnDEvent.getTransferData(DnDEventImpl.ourDataFlavor)} 获取 {@code Object[]}</li>
 *   <li>遍历数组，找出类型为 {@code DbElement} 的元素（即 DbTable）</li>
 *   <li>通过 {@code DbElement} 继承自 Psi 的 {@code getName()} 获取表名</li>
 *   <li>调用 {@link TableMetadataFetcher#fetchTableInfoByElement} 获取完整元信息并回调</li>
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
		// 判断拖拽数据中是否包含数据库表元素
		Object dbElement = findFirstDbElement(event);
		if (dbElement != null) {
			event.setDropPossible(true, "拖放到看板");
			return true;
		}
		return false;
	}

	@Override
	public void drop(DnDEvent event) {
		Object dbElement = findFirstDbElement(event);
		if (dbElement == null) {
			LOG.warn("[DnD] drop: 拖拽数据中没有找到 DbElement，忽略");
			return;
		}
		LOG.info("[DnD] drop: 找到 DbElement，对象类型 = " + dbElement.getClass().getName());
		processTable(dbElement, event.getPointOn(null));
	}

	/**
	 * 从 DnDEvent 中提取第一个 DbElement 元素
	 *
	 * <p>Database 工具窗口拖拽时，元素通过
	 * {@code DnDEvent.getTransferData(DnDEventImpl.ourDataFlavor)} 以 {@code Object[]}
	 * 形式提供，数组元素才是 DbElement（如 DbTable）。</p>
	 *
	 * @return 第一个 DbElement，找不到则返回 null
	 */
	private static Object findFirstDbElement(DnDEvent event) {
		if (event == null) {
			return null;
		}
		Object[] objects = extractObjects(event);
		if (objects == null) {
			return null;
		}
		for (Object obj : objects) {
			if (isDbTable(obj)) {
				return obj;
			}
		}
		return null;
	}

	/**
	 * 通过 DnDEventImpl.ourDataFlavor 从 event 提取 Object[]
	 */
	private static Object[] extractObjects(DnDEvent event) {
		try {
			// 反射获取 DnDEventImpl.ourDataFlavor 静态字段
			Class<?> eventImplClass = Class.forName("com.intellij.ide.dnd.DnDEventImpl");
			java.lang.reflect.Field flavorField = eventImplClass.getDeclaredField("ourDataFlavor");
			flavorField.setAccessible(true);
			Object flavorObj = flavorField.get(null);
			if (!(flavorObj instanceof DataFlavor)) {
				LOG.warn("[DnD] ourDataFlavor 不是 DataFlavor 类型");
				return null;
			}
			Object data = event.getTransferData((DataFlavor) flavorObj);
			if (data instanceof Object[]) {
				return (Object[]) data;
			}
			LOG.warn("[DnD] 拖拽数据不是 Object[] 类型，实际类型 = "
					+ (data == null ? "null" : data.getClass().getName()));
		} catch (Exception e) {
			LOG.warn("[DnD] 提取拖拽对象失败: " + e.getMessage());
		}
		return null;
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
			String tableName = getName(dbTable);
			if (tableName == null || tableName.isEmpty()) {
				LOG.warn("[DnD] 拖拽的表对象没有名字，忽略");
				return;
			}
			LOG.info("[DnD] 用户拖拽了表: " + tableName
					+ "，对象类型: " + dbTable.getClass().getName()
					+ "，拖放屏幕坐标: (" + (dropPoint == null ? "null" : dropPoint.x + "," + dropPoint.y) + ")");

			// 直接基于拖拽对象查询元信息（比名字匹配更可靠）
			TableInfo info;
			if (fetcher instanceof DatabaseTableMetadataFetcher) {
				info = ((DatabaseTableMetadataFetcher) fetcher).fetchTableInfoByElement(project, dbTable);
			} else {
				// 回退到按名字查询
				String datasourceName = getDataSourceName(dbTable);
				LOG.info("[DnD] 回退按名字查询: datasource=" + datasourceName + ", table=" + tableName);
				info = fetcher.fetchTableInfo(project, datasourceName, tableName);
			}
			if (info == null) {
				LOG.warn("[DnD] 获取表元信息失败（返回 null），表: " + tableName);
				return;
			}
			LOG.info("[DnD] 获取表元信息成功: " + info);
			if (callback != null) {
				LOG.info("[DnD] 回调看板绘制，表: " + tableName
						+ ", 字段数: " + info.getColumns().size());
				callback.onTableDropped(info, dropPoint);
			} else {
				LOG.warn("[DnD] 没有注册回调，无法绘制");
			}
		} catch (Exception e) {
			LOG.warn("[DnD] 处理拖拽表时异常", e);
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
			Object dataSource = invoke(dbElementClass.getMethod("getDataSource"), dbTable);
			if (dataSource != null) {
				Object name = invoke(dataSource.getClass().getMethod("getName"), dataSource);
				return name == null ? "" : name.toString();
			}
		} catch (Exception e) {
			LOG.warn("getDataSourceName failed", e);
		}
		return "";
	}

	/**
	 * 获取表名
	 *
	 * <p>DbElement 接口本身没有 getName()，但继承的
	 * {@code com.intellij.psi.PsiFileSystemItem} 有。通过 dbElementClass 查找继承方法。</p>
	 */
	private String getName(Object dbTable) {
		try {
			// 查找继承自 Psi 接口的 getName() 方法
			java.lang.reflect.Method m = findMethodByName(dbElementClass, "getName");
			if (m == null) {
				LOG.warn("getName method not found on DbElement");
				return null;
			}
			Object name = invoke(m, dbTable);
			return name == null ? null : name.toString();
		} catch (Exception e) {
			LOG.warn("getName failed", e);
			return null;
		}
	}

	/**
	 * 反射调用方法（跨模块需 setAccessible，否则 IllegalAccessException）
	 */
	private static Object invoke(java.lang.reflect.Method method, Object target, Object... args) {
		try {
			method.setAccessible(true);
			return method.invoke(target, args);
		} catch (Exception e) {
			LOG.warn("invoke failed: " + method.getName(), e);
			return null;
		}
	}

	/** 按方法名在接口及其父接口中查找方法 */
	private static java.lang.reflect.Method findMethodByName(Class<?> clazz, String name) {
		try {
			for (java.lang.reflect.Method m : clazz.getMethods()) {
				if (m.getName().equals(name)) {
					return m;
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	/**
	 * 加载并缓存 Database 的 DbElement 类
	 */
	private static synchronized boolean ensureDbClasses() {
		if (dbClassesReady) {
			return true;
		}
		try {
			// 2023.2 和 2025.3 中都只有 DbElement（DbNamedElement 类不存在）
			dbElementClass = Class.forName("com.intellij.database.psi.DbElement");
			dbClassesReady = true;
		} catch (ClassNotFoundException e) {
			LOG.warn("Database plugin classes not available for DnD", e);
			dbClassesReady = false;
		}
		return dbClassesReady;
	}
}