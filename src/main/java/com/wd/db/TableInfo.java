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

	private String id;
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

	/**
	 * 覆盖卡片 ID（2026-08-07 起用于拖入重复表时分配 UUID，避免 schema.table 形式 id 重复）
	 */
	public void setId(String id) {
		this.id = id;
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
		StringBuilder sb = new StringBuilder();
		sb.append("TableInfo{id='").append(id)
				.append("', name='").append(name)
				.append("', schema='").append(schema)
				.append("', datasource='").append(datasourceName)
				.append("', comment='").append(comment)
				.append("', columns=[");
		for (int i = 0; i < columns.size(); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(columns.get(i).toString());
		}
		sb.append("]}");
		return sb.toString();
	}
}