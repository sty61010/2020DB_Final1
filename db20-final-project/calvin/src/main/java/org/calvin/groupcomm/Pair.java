package org.calvin.groupcomm;

import java.io.Serializable;

import org.calvin.cache.CalvinRecord;
import org.calvin.sql.RecKey;

public class Pair implements Serializable{
	
	private static final long serialVersionUID = -6918468803372906756L;
	private CalvinRecord calvinRecord;
	private RecKey reckey;
	
	public Pair(CalvinRecord cr, RecKey rk) {
		this.reckey = rk;
		this.calvinRecord = cr;
	}
	
	public RecKey getRecKey() {
		return reckey;
	}
	
	public CalvinRecord getCalvinRecord() {
		return calvinRecord;
	}
}

