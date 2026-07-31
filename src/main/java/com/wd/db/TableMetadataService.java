package com.wd.db;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

/**
 * 表元信息获取服务的 Project 级入口
 *
 * <p>对外只暴露 {@link TableMetadataFetcher} 接口，具体实现由本类决定。
 * 这样 UI 层不依赖 Database 插件的类，便于测试和切换实现。</p>
 *
 * @author lww
 */
@Service(Service.Level.PROJECT)
public final class TableMetadataService {

	private final TableMetadataFetcher fetcher;

	public TableMetadataService(Project project) {
		// 默认使用基于 Database 插件的实现；
		// 后续如果需要 Mock 或直连 JDBC，可在此处切换
		this.fetcher = new DatabaseTableMetadataFetcher();
	}

	public TableMetadataFetcher getFetcher() {
		return fetcher;
	}

	/**
	 * 静态便捷方法，避免每次都写 project.getService(...)
	 */
	public static TableMetadataService getInstance(Project project) {
		return project.getService(TableMetadataService.class);
	}
}