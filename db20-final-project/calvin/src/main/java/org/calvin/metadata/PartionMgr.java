package org.calvin.metadata;

import org.calvin.sql.RecKey;
import org.vanilladb.comm.view.ProcessView;

public class PartionMgr {
	public static final int part_num;
	
	static {
		part_num = ProcessView.SERVER_COUNT;
	}
	
	public int getPartition(RecKey rk) {
		String tableName = rk.getTableName();
		int hashCode = 19;
		hashCode = hashCode * 37 + tableName.hashCode();
		if (hashCode < 0) hashCode *= -1;
		return  (hashCode % part_num);
	}
}