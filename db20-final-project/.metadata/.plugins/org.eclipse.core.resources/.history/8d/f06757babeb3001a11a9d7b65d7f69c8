package org.calvin.cache;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import org.calvin.sql.RecKey;
import org.vanilladb.core.sql.Constant;

public class CacheMgr {
	
	public Map<RecKey, BlockingQueue<CalvinRecord>> remoteRecords = new ConcurrentHashMap<RecKey ,BlockingQueue<CalvinRecord>>();
	
	public CacheMgr() {
	}
	
	public CalvinRecord remoteRead(RecKey rk) {
		BlockingQueue<CalvinRecord> tmp = null;
		
		tmp = this.remoteRecords.get(rk);
		if (tmp == null) {
			tmp = new LinkedBlockingQueue<CalvinRecord>();
			this.remoteRecords.put(rk, tmp);
		}
		
		
		try {
			while(true) {
				CalvinRecord cr = tmp.take();
				Map<String, Constant> predicate = rk.getPred();
				boolean flag = true;
				for(Map.Entry<String, Constant> entry: predicate.entrySet()) {
					if (cr.getVal(entry.getKey()) != entry.getValue()) {
						flag = false;
						break;
					}
				}
				if (flag) {
					return cr;
				} else {
					tmp.put(cr);
				}
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
}