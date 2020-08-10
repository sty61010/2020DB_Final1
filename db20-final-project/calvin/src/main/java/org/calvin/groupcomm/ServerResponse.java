package org.calvin.groupcomm;

import java.io.Serializable;

import org.vanilladb.core.remote.storedprocedure.SpResultSet;

public class ServerResponse implements Serializable{
	
	private static final long serialVersionUID = -3041611364670826943L;
	private int rteID;
	private SpResultSet rs;
	private Long txn;
	
	public ServerResponse(int rteID, SpResultSet rs, Long txn) {
		this.rteID = rteID;
		this.rs = rs;
		this.txn = txn;
	}
	
	public int getRteID() {
		return this.rteID;
	}
	
	public Long getTxn() {
		return this.txn;
	}
	
	public SpResultSet getResultSet() {
		return this.rs;
	}
	
}