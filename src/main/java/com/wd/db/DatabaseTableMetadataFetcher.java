package com.wd.db;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 基于 IntelliJ Database 插件的元信息获取实现
 *
 * <p>Database 插件为可选依赖，因此通过反射访问其类，避免在未安装 Database 的环境下
 * 出现 ClassNotFoundException。</p>
 *
 * <p>关键反射目标类（com.intellij.database.*）：</p>
 * <ul>
 *   <li>{@code DbPsiFacade} - 入口，单例</li>
 *   <li>{@code DbDataSource} - 数据源</li>
 *   <li>{@code DasObject} / {@code DasTable} / {@code DasColumn} - DAS 模型</li>
 *   <li>{@code DasUtil} - 工具方法</li>
 * </ul>
 *
 * @author lww
 */
public class DatabaseTableMetadataFetcher implements TableMetadataFetcher {

	private static final Logger LOG = Logger.getInstance(DatabaseTableMetadataFetcher.class);

	/** Database 插件 ID */
	private static final String DATABASE_PLUGIN_ID = "com.intellij.database";

	/** 反射缓存：避免重复查找 Class/Method */
	private static Class<?> dbPsiFacadeClass;
	private static Class<?> dasUtilClass;
	private static Class<?> dasDataSourceClass;
	private static Class<?> dasObjectClass;
	private static boolean classesInitialized = false;
	private static boolean classesAvailable = false;

	/**
	 * 方法查找缓存（类名 + 方法名 → Method），避免热路径反复反射遍历。
	 * key = "类名.方法名"，按需填充。
	 */
	private static final java.util.concurrent.ConcurrentHashMap<String, java.lang.reflect.Method>
			METHOD_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

	@Override
	public boolean isAvailable() {
		return isDatabasePluginEnabled() && ensureClasses();
	}

	@Override
	public List<String> listDataSources(Project project) {
		if (!isAvailable() || project == null) {
			return Collections.emptyList();
		}
		List<String> result = new ArrayList<>();
		try {
			Object facade = invokeStatic(dbPsiFacadeClass, "getInstance", Project.class, project);
			if (facade == null) {
				return Collections.emptyList();
			}
			Collection<?> dataSources = invokeDataSources(facade);
			if (dataSources == null) {
				return Collections.emptyList();
			}
			for (Object ds : dataSources) {
				String name = invokeStringNoArgs(ds, "getName");
				if (name != null) {
					result.add(name);
				}
			}
		} catch (Exception e) {
			LOG.warn("listDataSources failed", e);
		}
		return result;
	}

	@Override
	public List<TableInfo> listTables(Project project, String datasourceName) {
		if (!isAvailable() || project == null) {
			return Collections.emptyList();
		}
		List<TableInfo> result = new ArrayList<>();
		try {
			Object facade = invokeStatic(dbPsiFacadeClass, "getInstance", Project.class, project);
			if (facade == null) {
				return Collections.emptyList();
			}
			Object dataSource = findDataSource(facade, datasourceName);
			if (dataSource == null) {
				return result;
			}
			// 通过 DasUtil.getTables(dataSource) 获取表列表
			Iterable<?> tables = invokeStaticAsIterable(dasUtilClass, "getTables", dasDataSourceClass, dataSource);
			if (tables == null) {
				return result;
			}
			for (Object tbl : tables) {
				String name = invokeStringNoArgs(tbl, "getName");
				String schema = invokeStringNoArgs(tbl, "getSchema");
				String comment = invokeStringNoArgs(tbl, "getComment");
				String id = schema + "." + name;
				result.add(new TableInfo(id, name, schema, datasourceName, comment, null));
			}
		} catch (Exception e) {
			LOG.warn("listTables failed for datasource: " + datasourceName, e);
		}
		return result;
	}

	@Override
	public TableInfo fetchTableInfo(Project project, String datasourceName, String tableName) {
		if (!isAvailable() || project == null) {
			return null;
		}
		try {
			Object facade = invokeStatic(dbPsiFacadeClass, "getInstance", Project.class, project);
			if (facade == null) {
				return null;
			}
			Object dataSource = findDataSource(facade, datasourceName);
			if (dataSource == null) {
				return null;
			}
			Object table = findTable(dataSource, tableName);
			if (table == null) {
				return null;
			}

			String schema = invokeStringNoArgs(table, "getSchema");
			String comment = invokeStringNoArgs(table, "getComment");
			String id = schema + "." + tableName;

			List<ColumnInfo> columns = new ArrayList<>();
			Iterable<?> dasColumns = invokeStaticAsIterable(dasUtilClass, "getColumns", dasObjectClass, table);
			if (dasColumns != null) {
				for (Object col : dasColumns) {
					String colName = invokeStringNoArgs(col, "getName");

					// 字段类型：getDataType() 返回 DataType 对象，用 getSpecification() 获取类型名
					String colType = resolveColumnType(col);

					String colComment = invokeStringNoArgs(col, "getComment");

					// 主键 / 索引：通过 DasTable.getColumnAttrs(DasColumn) 获取属性集合判断
					boolean isPrimary = hasColumnAttribute(table, col, "PRIMARY_KEY");
					boolean isIndexed = hasColumnAttribute(table, col, "INDEX");

					// 可空性：DasColumn.isNotNull() 表示"非空"，取反即为可空
					Boolean isNotNull = invokeBooleanNoArgs(col, "isNotNull");
					boolean isNullable = isNotNull == null || !isNotNull;

					columns.add(new ColumnInfo(colName, colType, colComment,
							isPrimary, isNullable, isIndexed));
				}
			}
		return new TableInfo(id, tableName, schema, datasourceName, comment, columns);
	} catch (Exception e) {
			LOG.warn("fetchTableInfo failed for table: " + tableName, e);
			return null;
		}
	}

	/**
	 * 直接基于拖拽来的 DbTable 对象提取元信息（推荐用于拖拽场景）
	 *
	 * <p>比 {@link #fetchTableInfo} 更可靠：无需通过数据源名称重新匹配查找表，
	 * 直接用拖拽得到的对象，避免数据源显示名与唯一标识不一致的问题。</p>
	 *
	 * @param project 当前工程
	 * @param dbTable 拖拽来的 DbTable 对象（实现 DbElement）
	 * @return 表元信息
	 */
	public TableInfo fetchTableInfoByElement(Project project, Object dbTable) {
		if (!isAvailable() || project == null || dbTable == null) {
			return null;
		}
		try {
			String tableName = invokeStringNoArgs(dbTable, "getName");
			String schema = invokeStringNoArgs(dbTable, "getSchema");
			String comment = invokeStringNoArgs(dbTable, "getComment");

			// 数据源名
			Object dataSource = invokeNoArgs(dbTable, "getDataSource");
			String datasourceName = "";
			if (dataSource != null) {
				Object dsName = invokeNoArgs(dataSource, "getName");
				if (dsName != null) {
					datasourceName = dsName.toString();
				}
			}
			String id = (schema == null ? "" : schema) + "." + tableName;

			List<ColumnInfo> columns = new ArrayList<>();
			Iterable<?> dasColumns = invokeStaticAsIterable(dasUtilClass, "getColumns", dasObjectClass, dbTable);
			if (dasColumns != null) {
				for (Object col : dasColumns) {
					String colName = invokeStringNoArgs(col, "getName");
					String colType = resolveColumnType(col);
					String colComment = invokeStringNoArgs(col, "getComment");

					boolean isPrimary = hasColumnAttribute(dbTable, col, "PRIMARY_KEY");
					boolean isIndexed = hasColumnAttribute(dbTable, col, "INDEX");

					Boolean isNotNull = invokeBooleanNoArgs(col, "isNotNull");
					boolean isNullable = isNotNull == null || !isNotNull;

					columns.add(new ColumnInfo(colName, colType, colComment,
							isPrimary, isNullable, isIndexed));
				}
			}
			LOG.info("fetchTableInfoByElement success: table=" + tableName
					+ ", datasource=" + datasourceName + ", columns=" + columns.size());
			return new TableInfo(id, tableName, schema, datasourceName, comment, columns);
		} catch (Exception e) {
			LOG.warn("fetchTableInfoByElement failed for table: " + (dbTable == null ? "null" : dbTable), e);
			return null;
		}
	}

	// ========== 反射工具方法 ==========

	/**
	 * 解析列的数据类型
	 *
	 * <p>DasColumn.getDataType() 返回 {@code DataType} 对象（不是 String），
	 * 优先尝试 getSpecification() 获取类型名，失败则 toString()。</p>
	 */
	private static String resolveColumnType(Object column) {
		try {
			Object dataType = invokeNoArgs(column, "getDataType");
			if (dataType == null) {
				return "";
			}
			// 优先用 getSpecification()
			Object spec = invokeNoArgs(dataType, "getSpecification");
			if (spec != null && !spec.toString().isEmpty()) {
				return cleanType(spec.toString());
			}
			return cleanType(dataType.toString());
		} catch (Exception e) {
			LOG.warn("resolveColumnType failed", e);
			return "";
		}
	}

	/** 清洗类型字符串：去掉包裹的反引号 / 双引号 / 多余空白 */
	private static String cleanType(String type) {
		return type.replace("`", "").replace("\"", "").trim();
	}

	/**
	 * 判断某列是否具备指定属性（主键/索引等）
	 *
	 * <p>通过 {@code DasTable.getColumnAttrs(DasColumn)} 返回的 {@code Set<Attribute>}
	 * 判断，属性枚举值有 PRIMARY_KEY / INDEX / FOREIGN_KEY 等。</p>
	 *
	 * @param table        DasTable 对象
	 * @param column       DasColumn 对象
	 * @param attributeEnum 属性枚举名（如 "PRIMARY_KEY" / "INDEX"）
	 */
	private static boolean hasColumnAttribute(Object table, Object column, String attributeEnum) {
		try {
			Object attrs = invokeWithArg(table, "getColumnAttrs", column);
			if (attrs instanceof Collection) {
				for (Object attr : (Collection<?>) attrs) {
					if (attr != null && attributeEnum.equals(attr.toString())) {
						return true;
					}
				}
			}
		} catch (Exception e) {
			LOG.warn("hasColumnAttribute failed for " + attributeEnum, e);
		}
		return false;
	}

	/** 调用带一个 Object 参数的方法（按方法名匹配，兼容参数类型差异） */
	private static Object invokeWithArg(Object target, String method, Object arg) {
		try {
			Method m = findMethodByName(target.getClass(), method);
			if (m == null) {
				return null;
			}
			m.setAccessible(true);
			return m.invoke(target, arg);
		} catch (Exception e) {
			LOG.warn("invokeWithArg failed: " + target.getClass().getSimpleName() + "." + method, e);
			return null;
		}
	}

	/** 按方法名查找方法（含父类/接口遍历，不校验参数类型），结果按 (类名, 方法名) 缓存 */
	private static Method findMethodByName(Class<?> clazz, String method) {
		String key = clazz.getName() + "#" + method;
		java.lang.reflect.Method cached = METHOD_CACHE.get(key);
		if (cached != null) {
			return cached;
		}
		java.lang.reflect.Method found = findMethodByNameUncached(clazz, method);
		if (found != null) {
			METHOD_CACHE.put(key, found);
		}
		return found;
	}

	/** 实际查找方法（不缓存） */
	private static java.lang.reflect.Method findMethodByNameUncached(Class<?> clazz, String method) {
		try {
			for (Method m : clazz.getMethods()) {
				if (m.getName().equals(method)) {
					return m;
				}
			}
		} catch (Exception ignored) {
		}
		// 遍历父类接口
		Class<?> sup = clazz.getSuperclass();
		while (sup != null && sup != Object.class) {
			try {
				for (Method m : sup.getMethods()) {
					if (m.getName().equals(method)) {
						return m;
					}
				}
			} catch (Exception ignored) {
			}
			sup = sup.getSuperclass();
		}
		return null;
	}

	private static boolean isDatabasePluginEnabled() {
		try {
			PluginId id = PluginId.getId(DATABASE_PLUGIN_ID);
			// isPluginInstalled 兼容旧版 SDK（isPluginEnabled 是较新版本才有的 API）
			return PluginManagerCore.isPluginInstalled(id);
		} catch (Exception e) {
			return false;
		}
	}

	/** 加载并缓存 Database 插件的关键反射类 */
	private static synchronized boolean ensureClasses() {
		if (classesInitialized) {
			return classesAvailable;
		}
		classesInitialized = true;
		try {
			dbPsiFacadeClass = Class.forName("com.intellij.database.psi.DbPsiFacade");
			dasUtilClass = Class.forName("com.intellij.database.util.DasUtil");
			// DasUtil.getTables 参数类型是 com.intellij.database.model.DasDataSource
			dasDataSourceClass = Class.forName("com.intellij.database.model.DasDataSource");
			// DasUtil.getColumns 参数类型是 com.intellij.database.model.DasObject
			dasObjectClass = Class.forName("com.intellij.database.model.DasObject");
			// 验证 DasColumn 类可加载（仅做存在性校验）
			Class.forName("com.intellij.database.model.DasColumn");
			classesAvailable = true;
		} catch (ClassNotFoundException e) {
			LOG.warn("Database plugin classes not available. Please install/enable the Database plugin.", e);
			classesAvailable = false;
		}
		return classesAvailable;
	}

	private static Object invokeStatic(Class<?> clazz, String method, Class<?> argType, Object arg) {
		try {
			Method m = clazz.getMethod(method, argType);
			return invokeMethod(m, null, arg);
		} catch (Exception e) {
			LOG.warn("invokeStatic failed: " + clazz.getSimpleName() + "." + method, e);
			return null;
		}
	}

	private static Collection<?> invokeStaticWithArg(Class<?> clazz, String method, Class<?> argType, Object arg) {
		Object result = invokeStatic(clazz, method, argType, arg);
		return result instanceof Collection ? (Collection<?>) result : null;
	}

	/**
	 * 调用静态方法并返回 Iterable，兼容 Collection 和 JBIterable
	 *
	 * <p>DasUtil 在 2023.2/2025.3 中返回 {@code JBIterable}（实现 {@code Iterable}），
	 * 不是 {@code Collection}，需要特殊处理。</p>
	 */
	private static Iterable<?> invokeStaticAsIterable(Class<?> clazz, String method, Class<?> argType, Object arg) {
		Object result = invokeStatic(clazz, method, argType, arg);
		if (result instanceof Iterable) {
			return (Iterable<?>) result;
		}
		return null;
	}

	private static Object invokeNoArgs(Object target, String method) {
		try {
			Method m = findMethod(target.getClass(), method);
			if (m == null) {
				return null;
			}
			return invokeMethod(m, target);
		} catch (Exception e) {
			LOG.warn("invokeNoArgs failed: " + target.getClass().getSimpleName() + "." + method, e);
			return null;
		}
	}

	/**
	 * 反射调用方法（跨模块需要 setAccessible，否则模块系统会抛 IllegalAccessException）
	 */
	private static Object invokeMethod(Method m, Object target, Object... args) {
		try {
			m.setAccessible(true);
			return m.invoke(target, args);
		} catch (Exception e) {
			LOG.warn("invokeMethod failed: " + m.getName(), e);
			return null;
		}
	}

	/** 获取 DbPsiFacade.getDataSources() 的结果集合 */
	private static Collection<?> invokeDataSources(Object facade) {
		Object result = invokeNoArgs(facade, "getDataSources");
		return result instanceof Collection ? (Collection<?>) result : null;
	}

	private static String invokeStringNoArgs(Object target, String method) {
		Object result = invokeNoArgs(target, method);
		return result == null ? null : result.toString();
	}

	private static Boolean invokeBooleanNoArgs(Object target, String method) {
		Object result = invokeNoArgs(target, method);
		return result instanceof Boolean ? (Boolean) result : null;
	}

	private static Method findMethod(Class<?> clazz, String method) {
		String key = "F#." + clazz.getName() + "#" + method;
		java.lang.reflect.Method cached = METHOD_CACHE.get(key);
		if (cached != null) {
			return cached;
		}
		java.lang.reflect.Method found = findMethodUncached(clazz, method);
		if (found != null) {
			METHOD_CACHE.put(key, found);
		}
		return found;
	}

	private static java.lang.reflect.Method findMethodUncached(Class<?> clazz, String method) {
		try {
			return clazz.getMethod(method);
		} catch (NoSuchMethodException e) {
			// 尝试在父类中查找
			Class<?> sup = clazz.getSuperclass();
			while (sup != null && sup != Object.class) {
				try {
					return sup.getMethod(method);
				} catch (NoSuchMethodException ignored) {
					sup = sup.getSuperclass();
				}
			}
			return null;
		}
	}

	private static Object findDataSource(Object facade, String name) {
		Collection<?> sources = invokeDataSources(facade);
		if (sources == null) {
			return null;
		}
		for (Object ds : sources) {
			String n = invokeStringNoArgs(ds, "getName");
			if (n != null && n.equals(name)) {
				return ds;
			}
		}
		return null;
	}

	private static Object findTable(Object dataSource, String tableName) {
		Iterable<?> tables = invokeStaticAsIterable(dasUtilClass, "getTables", dasDataSourceClass, dataSource);
		if (tables == null) {
			return null;
		}
		for (Object t : tables) {
			String n = invokeStringNoArgs(t, "getName");
			if (n != null && n.equalsIgnoreCase(tableName)) {
				return t;
			}
		}
		return null;
	}
}