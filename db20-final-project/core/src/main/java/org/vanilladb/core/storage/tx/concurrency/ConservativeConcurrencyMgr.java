package org.vanilladb.core.storage.tx.concurrency;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.calvin.sql.RecKey;
import org.vanilladb.core.storage.file.BlockId;
import org.vanilladb.core.storage.record.RecordId;
import org.vanilladb.core.storage.tx.Transaction;
import org.vanilladb.core.storage.tx.concurrency.ConcurrencyMgr;

public class ConservativeConcurrencyMgr extends ConcurrencyMgr {
	protected long txNum;

	protected static ConservativeLockTable lockTbl = new ConservativeLockTable();
	
	private Set<Object> readObjs, writeObjs;
	
	public ConservativeConcurrencyMgr(long txNumber) {
		txNum = txNumber;
		readObjs = new HashSet<Object>();
		writeObjs = new HashSet<Object>();
	}


	public void RegisterReadKeys(Collection<RecKey> keys) {
		if (keys != null) {
			for (RecKey key : keys) {
				lockTbl.RegistertLock(key, txNum);
				readObjs.add(key);
			}
		}
	}
	
	public void RegisterWriteKeys(Collection<RecKey> keys) {
		if (keys != null) {
			for (RecKey key : keys) {
				lockTbl.RegistertLock(key, txNum);
				writeObjs.add(keys);
			}
		}
	}
	

	public void requestLocks() {
		for (Object obj : writeObjs)
			lockTbl.xLock(obj, txNum);
		
		for (Object obj : readObjs)
			if (!writeObjs.contains(obj))
				lockTbl.sLock(obj, txNum);
	}

	public void onTxCommit(Transaction tx) {
		releaseLocks();
	}

	public void onTxRollback(Transaction tx) {
		releaseLocks();
	}

	public void onTxEndStatement(Transaction tx) {}

	private void releaseLocks() {
		for (Object obj : writeObjs)
			lockTbl.release(obj, txNum, ConservativeLockTable.X_LOCK);
		
		for (Object obj : readObjs)
			if (!writeObjs.contains(obj))
				lockTbl.release(obj, txNum, ConservativeLockTable.S_LOCK);
		
		readObjs.clear();
		writeObjs.clear();
	}
	
	@Override
	public void modifyFile(String fileName) {}

	@Override
	public void readFile(String fileName) {}

	@Override
	public void modifyBlock(BlockId blk) {}

	@Override
	public void readBlock(BlockId blk) {}
	
	@Override
	public void modifyRecord(RecordId recId) {}

	@Override
	public void readRecord(RecordId recId) {}

	@Override
	public void insertBlock(BlockId blk) {}

	@Override
	public void modifyIndex(String dataFileName) {}

	@Override
	public void readIndex(String dataFileName) {}

	@Override
	public void modifyLeafBlock(BlockId blk) {}

	@Override
	public void readLeafBlock(BlockId blk) {}

	public void crabDownDirBlockForModification(BlockId blk) {}
	
	public void crabDownDirBlockForRead(BlockId blk) {}

	public void crabBackDirBlockForModification(BlockId blk) {}

	public void crabBackDirBlockForRead(BlockId blk) {}

	public void releaseIndexLocks() {}

	public void lockRecordFileHeader(BlockId blk) {}

	public void releaseRecordFileHeader(BlockId blk) {}	

}
