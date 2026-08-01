package com.wd.ui;

import com.wd.db.ColumnInfo;
import com.wd.db.TableInfo;
import com.wd.db.TableMetadataService;
import com.wd.model.ChartData;
import com.wd.model.ChartRelation;
import com.wd.model.RelationType;
import java.util.Collections;
import java.util.List;

/**
 * 看板持久化：负责 {@link KanbanBoard} 与 {@link ChartData}（.datachart JSON 模型）之间的转换。
 *
 * <p>从 {@link KanbanBoard} 抽取，职责单一：</p>
 * <ul>
 *   <li>{@link #toChartData}：把卡片 + 连线序列化为图数据模型</li>
 *   <li>{@link #loadFromChartData}：从图数据模型恢复看板状态</li>
 * </ul>
 *
 * <p>本类为纯转换，不触发重绘；恢复过程通过 {@link AddConnection} 回调把连线写回看板。</p>
 *
 * @author lww
 */
public final class BoardPersistence {

	private BoardPersistence() {
	}

	/**
	 * 将卡片 + 连线序列化为图数据模型（用于保存到 .datachart）。
	 */
	public static ChartData toChartData(List<KanbanCard> cards, List<Connection> connections) {
		ChartData data = new ChartData();
		for (KanbanCard card : cards) {
			TableInfo info = card.getTableInfo();
			if (info == null) {
				continue;
			}
			ChartData.TableCardModel model = new ChartData.TableCardModel();
			model.setId(card.getId());
			model.setDatasource(info.getDatasourceName());
			model.setSchema(info.getSchema());
			model.setTableName(info.getName());
			model.setComment(info.getComment());
			java.awt.geom.Rectangle2D b = card.getBounds();
			model.setX(b.getX());
			model.setY(b.getY());
			model.setWidth(b.getWidth());
			model.setHeight(b.getHeight());
			// 保存列信息（避免重新打开时重新查数据库）
			model.setColumns(new java.util.ArrayList<>(info.getColumns()));
			// 保存用户手动选中的行（橙色高亮持久化）
			model.setHighlightedRows(new java.util.ArrayList<>(card.getHighlightedRows()));
			data.getTables().add(model);
		}
		// 保存连线
		for (Connection conn : connections) {
			ChartRelation rel = new ChartRelation(
					conn.getSource().getId(), Integer.toString(conn.getSourceRow()),
					conn.getTarget().getId(), Integer.toString(conn.getTargetRow()),
					conn.getRelationType());
			data.getRelations().add(rel);
		}
		return data;
	}

	/**
	 * 从图数据模型恢复看板状态（打开 .datachart 文件时调用）。
	 *
	 * @param data         图数据模型
	 * @param findCardById 按卡片 ID 查找卡片（返回 null 表示不存在）
	 * @param addConnection 恢复一条连线（源/目标卡片，源/目标行，关系类型）
	 */
	public static void loadFromChartData(ChartData data,
			FindCard findCardById, AddConnection addConnection) {
		if (data == null) {
			return;
		}
		// 连线在 toChartData 层面不需要重建卡片，这里仅恢复连线（卡片由调用方先行构建）
		for (ChartRelation rel : data.getRelations()) {
			KanbanCard src = findCardById.find(rel.getFromCardId());
			KanbanCard tgt = findCardById.find(rel.getToCardId());
			if (src == null || tgt == null) {
				continue;
			}
			int srcRow = parseRowIndex(rel.getFromColumn());
			int tgtRow = parseRowIndex(rel.getToColumn());
			if (srcRow < 0 || tgtRow < 0) {
				continue;
			}
			RelationType type = rel.getRelationType() == null
					? RelationType.UNKNOWN : rel.getRelationType();
			addConnection.add(src, srcRow, tgt, tgtRow, type);
		}
	}

	/**
	 * 构建 {@link TableInfo}：优先用 JSON 中保存的列信息，否则尝试重新查询元信息。
	 *
	 * @param model       表卡片模型
	 * @param project     工程（重新查询时使用）
	 */
	public static TableInfo resolveTableInfo(ChartData.TableCardModel model,
			com.intellij.openapi.project.Project project) {
		List<ColumnInfo> savedColumns = model.getColumns();
		if (savedColumns != null && !savedColumns.isEmpty()) {
			// 优先用 JSON 里保存的列（快速、离线可用）
			return new TableInfo(model.getId(), model.getTableName(),
					model.getSchema(), model.getDatasource(),
					model.getComment(), savedColumns);
		}
		// 没有保存列信息，尝试重新查询元信息
		TableMetadataService svc = TableMetadataService.getInstance(project);
		TableInfo info = svc.getFetcher().fetchTableInfo(
				project, model.getDatasource(), model.getTableName());
		if (info == null) {
			// 查询失败，构造空 TableInfo
			return new TableInfo(model.getId(), model.getTableName(),
					model.getSchema(), model.getDatasource(),
					model.getComment(), Collections.emptyList());
		}
		return info;
	}

	/** 按卡片 ID 查找卡片 */
	public interface FindCard {
		KanbanCard find(String id);
	}

	/** 添加一条连线 */
	public interface AddConnection {
		void add(KanbanCard source, int sourceRow,
				KanbanCard target, int targetRow, RelationType type);
	}

	/**
	 * 从字符串解析行索引。
	 */
	private static int parseRowIndex(String s) {
		try {
			return Integer.parseInt(s);
		} catch (Exception e) {
			return -1;
		}
	}
}
