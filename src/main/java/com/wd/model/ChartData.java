package com.wd.model;

import com.wd.db.ColumnInfo;
import java.util.ArrayList;
import java.util.List;

/**
 * 图数据模型（对应 .datachart JSON 文件）
 *
 * <p>存储整个看板图的所有信息：版本、画布上放置的表卡片、表之间的连接关系。</p>
 *
 * @author lww
 */
public class ChartData {

	/** 格式版本 */
	private String version = "1.0";

	/** 图表/看板名称 */
	private String name;

	/** 画布上放置的表卡片 */
	private List<TableCardModel> tables = new ArrayList<>();

	/** 表之间的连接关系 */
	private List<ChartRelation> relations = new ArrayList<>();

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<TableCardModel> getTables() {
		return tables;
	}

	public void setTables(List<TableCardModel> tables) {
		this.tables = tables;
	}

	public List<ChartRelation> getRelations() {
		return relations;
	}

	public void setRelations(List<ChartRelation> relations) {
		this.relations = relations;
	}

	/**
	 * 看板上的一张表卡片
	 */
	public static class TableCardModel {

		/** 卡片唯一 ID（datasource.table 形式） */
		private String id;

		/** 数据源名称 */
		private String datasource;

		/** Schema 名 */
		private String schema;

		/** 表名 */
		private String tableName;

		/** 表注释 */
		private String comment;

		/** 卡片在画布上的位置（画板坐标） */
		private double x;
		private double y;

		/** 卡片尺寸 */
		private double width;
		private double height;

		public TableCardModel() {
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getDatasource() {
			return datasource;
		}

		public void setDatasource(String datasource) {
			this.datasource = datasource;
		}

		public String getSchema() {
			return schema;
		}

		public void setSchema(String schema) {
			this.schema = schema;
		}

		public String getTableName() {
			return tableName;
		}

		public void setTableName(String tableName) {
			this.tableName = tableName;
		}

		public String getComment() {
			return comment;
		}

		public void setComment(String comment) {
			this.comment = comment;
		}

		public double getX() {
			return x;
		}

		public void setX(double x) {
			this.x = x;
		}

		public double getY() {
			return y;
		}

		public void setY(double y) {
			this.y = y;
		}

		public double getWidth() {
			return width;
		}

		public void setWidth(double width) {
			this.width = width;
		}

		public double getHeight() {
			return height;
		}

		public void setHeight(double height) {
			this.height = height;
		}

		/** 字段列表（用于持久化，避免重新查询数据库） */
		private List<ColumnInfo> columns = new ArrayList<>();

		public List<ColumnInfo> getColumns() {
			return columns;
		}

		public void setColumns(List<ColumnInfo> columns) {
			this.columns = columns;
		}
	}
}