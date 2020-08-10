package org.calvin.groupcomm;

import java.io.Serializable;

public class StoredProcInfo implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private int rteID;
	private int clientID;
	private int procID;
	private Long txn;
	private Object[] pars;
	
	public StoredProcInfo(int rteID, int clientID, int procID, Long txn, Object... pars) {
		this.rteID = rteID;
		this.clientID = clientID;
		this.procID = procID;
		this.pars = pars;
		this.txn = txn;
	}
	
	
	public long getTxNum() {
		return this.txn;
	}
	
	public int getClienID() {
		return this.clientID;
	}
	
	public int getPorcID() {
		return this.procID;
	}
	
	public int getRteID() {
		return this.rteID;
	}
	
	public Object[] getPars() {
		return this.pars;
	}
}