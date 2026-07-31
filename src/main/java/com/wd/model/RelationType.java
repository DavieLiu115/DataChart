package com.wd.model;

/**
 * 表连接关系类型
 *
 * @author lww
 */
public enum RelationType {
	/** 一对一 */
	ONE_TO_ONE,
	/** 一对多 */
	ONE_TO_MANY,
	/** 多对一 */
	MANY_TO_ONE,
	/** 多对多 */
	MANY_TO_MANY,
	/** 未知/未指定 */
	UNKNOWN
}
