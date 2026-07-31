package com.wd.db;

import com.intellij.openapi.project.Project;
import java.util.List;

/**
 * 数据库表元信息获取接口
 *
 * <p>抽象层：屏蔽具体数据库实现差异（Database 插件、JDBC 直连、静态数据等），
 * 上层（看板 UI）只依赖此接口，方便测试和扩展。</p>
 *
 * @author lww
 */
public interface TableMetadataFetcher {

	/**
	 * 列出当前 Project 中所有可用的 DataSource
	 */
	List<String> listDataSources(Project project);

	/**
	 * 列出指定 DataSource 下的所有表
	 *
	 * @param project          当前工程
	 * @param datasourceName   DataSource 名称（来自 listDataSources）
	 */
	List<TableInfo> listTables(Project project, String datasourceName);

	/**
	 * 获取指定表的完整元信息（列定义等）
	 *
	 * @param project        当前工程
	 * @param datasourceName DataSource 名称
	 * @param tableName      表名
	 * @return 表元信息，如果表不存在则返回 null
	 */
	TableInfo fetchTableInfo(Project project, String datasourceName, String tableName);

	/**
	 * 检查当前环境是否支持数据库访问
	 *
	 * <p>Database 插件为可选依赖时，未安装应返回 false，上层应给出友好提示。</p>
	 */
	boolean isAvailable();
}