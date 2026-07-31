package com.wd.db;

/**
 * 数据库列元信息模型
 *
 * @author lww
 */
public class ColumnInfo {

	private String name;
	private String type;
	private String comment;
	private boolean isPrimaryKey;
	private boolean isNullable;
	private boolean isIndexed;

	/** fastjson 反序列化需要的无参构造器 */
	public ColumnInfo() {
	}

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

	public void setName(String name) {
		this.name = name == null ? "" : name;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type == null ? "" : type;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment == null ? "" : comment;
	}

	public boolean isPrimaryKey() {
		return isPrimaryKey;
	}

	public void setPrimaryKey(boolean primaryKey) {
		isPrimaryKey = primaryKey;
	}

	public boolean isNullable() {
		return isNullable;
	}

	public void setNullable(boolean nullable) {
		isNullable = nullable;
	}

	public boolean isIndexed() {
		return isIndexed;
	}

	public void setIndexed(boolean indexed) {
		isIndexed = indexed;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("ColumnInfo{name='").append(name)
				.append("', type='").append(type).append('\'')
				.append(", nullable=").append(isNullable)
				.append(", pk=").append(isPrimaryKey)
				.append(", idx=").append(isIndexed);
		if (comment != null && !comment.isEmpty()) {
			sb.append(", comment='").append(comment).append('\'');
		}
		sb.append('}');
		return sb.toString();
	}
}