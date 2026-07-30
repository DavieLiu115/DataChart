package com.wd.editor;

import com.intellij.testFramework.LightVirtualFile;

public class DataChartVirtualFile extends LightVirtualFile {

	public DataChartVirtualFile(String name) {
		super(name);
		// 标记为非物理文件,避免被索引
		setWritable(true);
	}

	@Override
	public boolean isValid() {
		return true;
	}

	@Override
	public String getPresentableName() {
		String name = getName();
		int dotIndex = name.lastIndexOf('.');
		return (dotIndex > 0) ? name.substring(0, dotIndex) : name;
	}

	@Override
	public boolean isInLocalFileSystem() {
		return true;
	}

}