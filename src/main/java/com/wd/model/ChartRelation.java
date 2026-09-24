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

	/** 源表连接字段（列 index，字符串形式；删除列后会错位，优先用 {@link #fromColumnName}） */
	private String fromColumn;

	/** 源表连接字段列名（2026-08-03 新增，删除列后仍能准确定位） */
	private String fromColumnName;

	/** 目标表卡片 ID */
	private String toCardId;

	/** 目标表连接字段（列 index，字符串形式；删除列后会错位，优先用 {@link #toColumnName}） */
	private String toColumn;

	/** 目标表连接字段列名（2026-08-03 新增，删除列后仍能准确定位） */
	private String toColumnName;

	/** 关系类型 */
	private RelationType relationType;

	/** 关系名称（可选，如外键名） */
	private String name;

	/**
	 * 连线配色序号（palette 下标，2026-09-24 新增，随 .datachart 文件保存）。
	 *
	 * <p><b>为什么必须存</b>：连线颜色原来是"每次打开文件按读到的顺序重新分配"，
	 * 删掉靠前的连线后再打开，后面的连线序号整体前移 → 颜色全变（用户反馈的问题）。</p>
	 *
	 * <p><b>为什么存序号而不是 RGB</b>：调色板是 {@code JBColor}（浅色 / 深色两版），
	 * 存序号才能让同一份文件在两种主题下各取所需；存死 RGB 会让老连线在深色主题下不变色，
	 * 压在暗底上刺眼、当行背景还会糊住文字。</p>
	 *
	 * <p>旧文件没有该字段（{@code null}）→ 加载时仍按顺序分配，行为与以前一致。</p>
	 */
	private Integer colorIndex;

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

	public String getFromColumnName() {
		return fromColumnName;
	}

	public void setFromColumnName(String fromColumnName) {
		this.fromColumnName = fromColumnName;
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

	public String getToColumnName() {
		return toColumnName;
	}

	public void setToColumnName(String toColumnName) {
		this.toColumnName = toColumnName;
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

	/**
	 * 连线配色序号（调色板下标）。
	 *
	 * @return palette 下标；{@code null} 表示旧文件没存过，加载时按顺序分配
	 */
	public Integer getColorIndex() {
		return colorIndex;
	}

	public void setColorIndex(Integer colorIndex) {
		this.colorIndex = colorIndex;
	}
}