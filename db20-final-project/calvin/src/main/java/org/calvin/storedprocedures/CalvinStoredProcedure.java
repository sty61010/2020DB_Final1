package org.calvin.storedprocedures;

import java.util.List;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.calvin.cache.CalvinRecord;
import org.calvin.groupcomm.Pair;
import org.calvin.server.Calvin;
import org.calvin.sql.RecKey;
import org.vanilladb.comm.view.ProcessView;
import org.vanilladb.core.query.algebra.Plan;
import org.vanilladb.core.query.algebra.Scan;
import org.vanilladb.core.remote.storedprocedure.SpResultSet;
import org.vanilladb.core.server.VanillaDb;
import org.vanilladb.core.storage.tx.*;
import org.vanilladb.core.sql.Constant;
import org.vanilladb.core.sql.storedprocedure.*;

public abstract class CalvinStoredProcedure <H extends StoredProcedureParamHelper> extends StoredProcedure<StoredProcedureParamHelper> implements Runnable{ 
//	private static Logger logger = Logger.getLogger(StoredProcedure.class.getName());
	
	private class KeyQryPair {
		private RecKey rk;
		private String qry;
		public KeyQryPair(RecKey rk, String qry) {
			this.rk = rk;
			this.qry = qry;
		}
	}
	
	protected H paramHelper;
	private Transaction tx;
	private long txNum;
	private int localId;
	private int lastRead_id;
	private int sender_id;
	
	private Set<Integer> activeParticipants = new HashSet<Integer>();
	
	private Set<KeyQryPair> localReadKey = new HashSet<KeyQryPair>();
	private Set<RecKey> remoteReadRecKey = new HashSet<RecKey>();
	private Set<RecKey> localWriteRecKey = new HashSet<RecKey>();
	private Set<RecKey> localInsertRecKey = new HashSet<RecKey>();
	
	private Map<RecKey, CalvinRecord> reads = new HashMap<RecKey, CalvinRecord>();

	private int clientID;
	private int rteID;
	private SpResultSet rs;
	private Long rte2txn;

	public CalvinStoredProcedure(H helper) {
		super(helper);
		if (helper == null)
			throw new IllegalArgumentException("paramHelper should not be null");
		
		paramHelper = helper;
	}
	
	@Override
	public void run() {
		//prepare();
		rs = execute();
		
		Calvin.server.sendStoredProcResults(rs, Calvin.server.server, this.clientID, this.rteID, this.rte2txn);
	}
	
	protected abstract void prepareRecKey();
	
	public void setID(int clientId, int rteId, Long rte2txn) {
		this.clientID = clientId;
		this.rteID = rteId;
		this.rte2txn = rte2txn;
	}
	
	public void prepare(Object... pars) {
		
		
		// prepare parameters
		if (paramHelper == null) System.out.println("mother fucker helper");
		paramHelper.prepareParameters(pars);
		
		// create a transaction
		boolean isReadOnly = paramHelper.isReadOnly();
		// should replace vanillaDB with server
		tx = Calvin.txMgr().newTransaction(Connection.TRANSACTION_SERIALIZABLE, isReadOnly);
		
		prepareRecKey();
		registerConservativeLocks();
		if(this.activeParticipants.isEmpty()) {
			sender_id = lastRead_id;
			this.activeParticipants.add(sender_id);
		}
		
	}
	
	@Override
	public SpResultSet execute() {
//		System.out.println("execute sql");
		boolean isCommitted = false;
		
		// Get all locks
		acquireConservativeLocks();
//		System.out.println("locks acquired");
		// Do self part
		executeSelf();
		tx.commit();
		isCommitted = true;
//		System.out.println("commit!");
		
		return new SpResultSet(isCommitted, paramHelper.getResultSetSchema(), paramHelper.newResultSetRecord());
		
	}
	
	public void executeSelf() {
		// local read and send to remote
		localread();
		System.out.println("localRead successfully");
		// Remote read
		remoteRead(reads);
		System.out.println("remotedRead successfully");
		// local write
		executeSql(reads);
	}
	
	protected abstract void executeSql(Map<RecKey, CalvinRecord> reads);
	
	public void remoteRead(Map<RecKey, CalvinRecord> reads) {
		for(RecKey rk: remoteReadRecKey) {
			reads.put(rk, Calvin.cache.remoteRead(rk));
		}
	}
	
	public void localread() {
		List<Pair> list = new ArrayList<Pair>();
		for(KeyQryPair pair: localReadKey) {
			String qry = pair.qry;
			Plan p = VanillaDb.newPlanner().createQueryPlan(qry, tx);
			CalvinRecord cr = new CalvinRecord();
			Scan s = p.open();
			s.beforeFirst();
			if(s.next()) {
			//  read selected column
				Set<String> fieldName = p.schema().fields();
				for(String fn: fieldName) {
					Constant val = s.getVal(fn);
					cr.setVal(fn, val);
				}
				list.add(new Pair(cr, pair.rk));
				this.reads.put(pair.rk, cr);
			}
		}
		// Send p2p
		for(int node: this.activeParticipants) {
			System.out.println("node: " + node);
			if(node!=this.localId) {
				Calvin.server.sendRecord2RemoteServer(node, list, Calvin.server.server);
				System.out.println("send to remote server successfully");
			}
			
		}
	}
	
//	@Override
//	protected abstract void executeSql() {
		
//	}
	public void addReadKey(RecKey rk, String qry) {
		// partion Mgr
		int nodeId = Calvin.partionMgr.getPartition(rk);
		if(nodeId==localId) {
			this.localReadKey.add(new KeyQryPair(rk, qry));
		} else {
			this.remoteReadRecKey.add(rk);
		}
		
		lastRead_id = nodeId;
	}
	
	public void addWriteKey(RecKey rk) {
		int nodeId = Calvin.partionMgr.getPartition(rk);
		if(nodeId==localId) 
			this.localWriteRecKey.add(rk);
		this.activeParticipants.add(nodeId);
		sender_id = nodeId;
	}
	
	public void addInsertKey(RecKey rk) {
		int nodeId = Calvin.partionMgr.getPartition(rk);
		if(nodeId==localId) 
			this.localInsertRecKey.add(rk);
		this.activeParticipants.add(nodeId);
		sender_id = nodeId;
	}
	
	public void update(RecKey rk, String qry) {
		if (localWriteRecKey.contains(rk))
			VanillaDb.newPlanner().executeUpdate(qry, tx);
	}
	
	public void insert(RecKey rk, String qry) {
		if (localInsertRecKey.contains(rk))
			VanillaDb.newPlanner().executeUpdate(qry, tx);
	}
	
	public void delete(RecKey rk, String qry) {
		// XXX: Do we need a 'localDeleteKeys' for this ?
		if (localWriteRecKey.contains(rk))
			VanillaDb.newPlanner().executeUpdate(qry, tx);
	}
	
	protected Transaction getTransaction() {
		return tx;
	}
//	@Override
	public boolean isReadOnly() {
		return paramHelper.isReadOnly();
	}
	public void setTxNum(long txNum) {
		this.txNum = txNum;
	}
	protected void abort() {
		throw new ManuallyAbortException();
	}
	
	protected void abort(String message) {
		throw new ManuallyAbortException(message);
	}
	public void registerConservativeLocks() {
		Set<RecKey> lrmap = new HashSet<RecKey>();
		for(KeyQryPair pair: this.localReadKey) {
			lrmap.add(pair.rk);
		}
		this.tx.ccMgr.RegisterReadKeys(lrmap);
		this.tx.ccMgr.RegisterWriteKeys(this.localInsertRecKey);
		this.tx.ccMgr.RegisterWriteKeys(this.localWriteRecKey);
	}
	public void acquireConservativeLocks() {
		this.tx.ccMgr.requestLocks();
	}
	public H getParamHelper() {
		return paramHelper;
	}
}