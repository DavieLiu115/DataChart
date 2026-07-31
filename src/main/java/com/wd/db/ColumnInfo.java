package com.wd.db;

/**
 * 数据库列元信息模型
 *
 * @author lww
 */
public class ColumnInfo {

	private final String name;
	private final String type;
	private final String comment;
	private final boolean isPrimaryKey;
	private final boolean isNullable;
	private final boolean isIndexed;

	public ColumnInfo(String name, String type, String comment,
			boolean isPrimaryKey, boolean isNullable, boolean isIndexed) {
		this.name = name == null ? "" : name;
		this.type = type == null ? "" : type;
		this.comment = comment == null ? "" : comment;
		this.isPrimaryKey = isPrimaryKey;
		this.isNullable = isNullable;
		this.isIndexed = isIndexed;
	}

	public String getName() {
		return name;
	}

	public String getType() {
		return type;
	}

	public String getComment() {
		return comment;
	}

	public boolean isPrimaryKey() {
		return isPrimaryKey;
	}

	public boolean isNullable() {
		return isNullable;
	}

	public boolean isIndexed() {
		return isIndexed;
	}

	@Override
	public String toString() {
		return "ColumnInfo{" +
				"name='" + name + '\'' +
				", type='" + type + '\'' +
				(isPrimaryKey ? ", PK" : "") +
				(isIndexed ? ", IDX" : "") +
				'}';
	}
}