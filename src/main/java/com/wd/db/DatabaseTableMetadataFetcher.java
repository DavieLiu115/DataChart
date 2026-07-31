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
	private static Class<?> dbDataSourceClass;
	private static Class<?> dasTableClass;
	private static Class<?> dasColumnClass;
	private static boolean classesInitialized = false;
	private static boolean classesAvailable = false;

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
			Collection<?> dataSources = invokeNoArgs(facade, "getDataSources");
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
			Collection<?> tables = invokeStaticWithArg(dasUtilClass, "getTables", dbDataSourceClass, dataSource);
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
			Collection<?> dasColumns = invokeStaticWithArg(dasUtilClass, "getColumns", dasTableClass, table);
			if (dasColumns != null) {
				for (Object col : dasColumns) {
					String colName = invokeStringNoArgs(col, "getName");
					String colType = invokeStringNoArgs(col, "getDataType");
					if (colType == null) {
						colType = "";
					} else {
						// 去掉包裹的引号
						colType = colType.replace("`", "").replace("\"", "");
					}
					String colComment = invokeStringNoArgs(col, "getComment");
					boolean isPrimary = invokeBooleanNoArgs(col, "isPrimary");
					if (isPrimary == null) {
						isPrimary = invokeBooleanNoArgs(col, "isPrimaryKey");
					}
					boolean isNullable = invokeBooleanNoArgs(col, "isNotNull");
					// DasColumn.isNotNull 表示"非空"，需取反
					if (isNullable != null) {
						isNullable = !isNullable;
					} else {
						isNullable = true;
					}
					Boolean isIndex = invokeBooleanNoArgs(col, "isIndex");
					boolean isIndexed = isIndex != null && isIndex;
					columns.add(new ColumnInfo(colName, colType, colComment,
							isPrimary != null && isPrimary, isNullable, isIndexed));
				}
			}
			return new TableInfo(id, tableName, schema, datasourceName, comment, columns);
		} catch (Exception e) {
			LOG.warn("fetchTableInfo failed for table: " + tableName, e);
			return null;
		}
	}

	// ========== 反射工具方法 ==========

	private static boolean isDatabasePluginEnabled() {
		try {
			PluginId id = PluginId.getId(DATABASE_PLUGIN_ID);
			return PluginManagerCore.isPluginInstalled(id) && PluginManagerCore.isPluginEnabled(id);
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
			dbDataSourceClass = Class.forName("com.intellij.database.psi.DbDataSource");
			dasTableClass = Class.forName("com.intellij.database.model.DasTable");
			dasColumnClass = Class.forName("com.intellij.database.model.DasColumn");
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
			return m.invoke(null, arg);
		} catch (Exception e) {
			LOG.warn("invokeStatic failed: " + clazz.getSimpleName() + "." + method, e);
			return null;
		}
	}

	private static Collection<?> invokeStaticWithArg(Class<?> clazz, String method, Class<?> argType, Object arg) {
		Object result = invokeStatic(clazz, method, argType, arg);
		return result instanceof Collection ? (Collection<?>) result : null;
	}

	private static Object invokeNoArgs(Object target, String method) {
		try {
			Method m = findMethod(target.getClass(), method);
			if (m == null) {
				return null;
			}
			return m.invoke(target);
		} catch (Exception e) {
			LOG.warn("invokeNoArgs failed: " + target.getClass().getSimpleName() + "." + method, e);
			return null;
		}
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
		Collection<?> sources = invokeNoArgs(facade, "getDataSources");
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
		Collection<?> tables = invokeStaticWithArg(dasUtilClass, "getTables", dbDataSourceClass, dataSource);
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