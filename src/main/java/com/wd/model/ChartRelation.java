package com.wd.model;

/**
 * 表连接关系（连线）
 *
 * <p>描述两张表之间的一对一 / 一对多等关系，以及连线属性。</p>
 *
 * @author lww
 */
public class ChartRelation {

	/** 源表卡片 ID */
	private String fromCardId;

	/** 源表连接字段 */
	private String fromColumn;

	/** 目标表卡片 ID */
	private String toCardId;

	/** 目标表连接字段 */
	private String toColumn;

	/** 关系类型 */
	private RelationType relationType;

	/** 关系名称（可选，如外键名） */
	private String name;

	public ChartRelation() {
	}

	public ChartRelation(String fromCardId, String fromColumn,
			String toCardId, String toColumn, RelationType relationType) {
		this.fromCardId = fromCardId;
		this.fromColumn = fromColumn;
		this.toCardId = toCardId;
		this.toColumn = toColumn;
		this.relationType = relationType;
	}

	public String getFromCardId() {
		return fromCardId;
	}

	public void setFromCardId(String fromCardId) {
		this.fromCardId = fromCardId;
	}

	public String getFromColumn() {
		return fromColumn;
	}

	public void setFromColumn(String fromColumn) {
		this.fromColumn = fromColumn;
	}

	public String getToCardId() {
		return toCardId;
	}

	public void setToCardId(String toCardId) {
		this.toCardId = toCardId;
	}

	public String getToColumn() {
		return toColumn;
	}

	public void setToColumn(String toColumn) {
		this.toColumn = toColumn;
	}

	public RelationType getRelationType() {
		return relationType;
	}

	public void setRelationType(RelationType relationType) {
		this.relationType = relationType;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}