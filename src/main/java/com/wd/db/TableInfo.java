package com.wd.db;

import java.util.Collections;
import java.util.List;

/**
 * 数据库表元信息模型
 *
 * <p>用纯 POJO 表示一张数据库表的元信息，与具体数据库实现解耦，
 * 便于在没有 Database 插件时也能提供静态数据/测试数据。</p>
 *
 * @author lww
 */
public class TableInfo {

	private final String id;
	private final String name;
	private final String schema;
	private final String datasourceName;
	private final String comment;
	private final List<ColumnInfo> columns;

	public TableInfo(String id, String name, String schema, String datasourceName,
			String comment, List<ColumnInfo> columns) {
		this.id = id;
		this.name = name;
		this.schema = schema;
		this.datasourceName = datasourceName;
		this.comment = comment == null ? "" : comment;
		this.columns = columns == null ? Collections.emptyList() : columns;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getSchema() {
		return schema;
	}

	public String getDatasourceName() {
		return datasourceName;
	}

	public String getComment() {
		return comment;
	}

	public List<ColumnInfo> getColumns() {
		return columns;
	}

	/**
	 * 显示标题：优先使用 comment（如果有），否则用 name
	 */
	public String getDisplayTitle() {
		return comment == null || comment.isEmpty() ? name : name + "  /  * " + comment + " *";
	}

	@Override
	public String toString() {
		return "TableInfo{" +
				"id='" + id + '\'' +
				", name='" + name + '\'' +
				", schema='" + schema + '\'' +
				", columns=" + columns.size() +
				'}';
	}
}