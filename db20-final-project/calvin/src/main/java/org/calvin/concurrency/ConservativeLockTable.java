/*******************************************************************************
 * Copyright 2016, 2017 vanilladb.org contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package org.calvin.concurrency;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import org.vanilladb.core.storage.tx.concurrency.LockAbortException;

/**
 * Checks the compatibility of locking requests on a single item (e.g., file,
 * block, or record). Does <em>not</em> implement a locking protocol to ensure
 * the semantic correctness of locking different items with different
 * granularity.
 * 
 * <p>
 * If a transaction requests to lock an item that causes a conflict with an
 * existing lock on that item, then the transaction is placed into a wait list
 * and will be notified (awaked) to compete for the lock whenever the conflict
 * is resolved. Currently, there is only one wait list for all items.
 * </p>
 */
class ConservativeLockTable {
	final static int IS_LOCK = 0, IX_LOCK = 1, S_LOCK = 2, SIX_LOCK = 3,
			X_LOCK = 4;

	class Lockers {
		Set<Long> sLockers, ixLockers, isLockers;
		LinkedList<Long> requestList;
		// only one tx can hold xLock(sixLock) on single item
		long sixLocker, xLocker;
		static final long NONE = -1; // for sixLocker, xLocker

		Lockers() {
			sLockers = new HashSet<Long>();
			ixLockers = new HashSet<Long>();
			isLockers = new HashSet<Long>();
			sixLocker = NONE;
			xLocker = NONE;
			requestList = new LinkedList<Long>();
		}
		
		@Override
		public String toString() {
			return "S: " + sLockers + ",IX: " + ixLockers + ",IS: " + isLockers
					+ ",SIX: " + sixLocker + ",X: " + xLocker + ", request list: " + requestList;
		}
	}

	private Map<Object, Lockers> lockerMap = new HashMap<Object, Lockers>();
	private final Object anchors[] = new Object[1009];

	public ConservativeLockTable() {
		for (int i = 0; i < anchors.length; ++i) {
			anchors[i] = new Object();
		}
	}

	private Object getAnchor(Object o) {
		int code = o.hashCode() % anchors.length;
		if (code < 0) {
			code += anchors.length;
		}
		return anchors[code];
	}
	
	void RegistertLock(Object obj, long txNum) {
		synchronized (getAnchor(obj)) {
			Lockers lockers = prepareLockers(obj);
			// one tx only occur one time
			if(!lockers.requestList.contains(txNum))
				lockers.requestList.add(txNum);
		}
	}

	/**
	 * Grants an slock on the specified item. If any conflict lock exists when
	 * the method is called, then the calling thread will be placed on a wait
	 * list until the lock is released. If the thread remains on the wait list
	 * for a certain amount of time, then an exception is thrown.
	 * 
	 * @param obj
	 *            a lockable item
	 * @param txNum
	 *            a transaction number
	 * 
	 */
	void sLock(Object obj, long txNum) {
		Object anchor = getAnchor(obj);
		synchronized (anchor) {
			Lockers lks = prepareLockers(obj);

			if (hasSLock(lks, txNum)) {
				lks.requestList.remove(txNum);
				return;
			}
				
			try {
				Long first = lks.requestList.peek();
				while (!sLockable(lks, txNum) || first != txNum) {
					anchor.wait();
					first = lks.requestList.peek();
				}
				lks.requestList.pop();
				lks.sLockers.add(txNum);
				anchor.notifyAll();
			} catch (InterruptedException e) {
				throw new LockAbortException("abort tx." + txNum + " by interrupted");
			}
		}
	}

	/**
	 * Grants an xlock on the specified item. If any conflict lock exists when
	 * the method is called, then the calling thread will be placed on a wait
	 * list until the lock is released. If the thread remains on the wait list
	 * for a certain amount of time, then an exception is thrown.
	 * 
	 * @param obj
	 *            a lockable item
	 * @param txNum
	 *            a transaction number
	 * 
	 */
	void xLock(Object obj, long txNum) {
		Object anchor = getAnchor(obj);
		synchronized (anchor) {
			Lockers lks = prepareLockers(obj);

			if (hasXLock(lks, txNum)) {
				lks.requestList.remove(txNum);
				return;
			}
				
			try {
				Long first = lks.requestList.peek();
				while (!xLockable(lks, txNum) || first != txNum) {
					anchor.wait();
					first = lks.requestList.peek();
				}
				lks.requestList.pop();
				lks.xLocker = txNum;
				anchor.notifyAll();
			} catch (InterruptedException e) {
				throw new LockAbortException("abort tx." + txNum + " by interrupted");
			}
		}
	}

	/**
	 * Grants an sixlock on the specified item. If any conflict lock exists when
	 * the method is called, then the calling thread will be placed on a wait
	 * list until the lock is released. If the thread remains on the wait list
	 * for a certain amount of time, then an exception is thrown.
	 * 
	 * @param obj
	 *            a lockable item
	 * @param txNum
	 *            a transaction number
	 * 
	 */
	void sixLock(Object obj, long txNum) {
		Object anchor = getAnchor(obj);
		synchronized (anchor) {
			Lockers lks = prepareLockers(obj);

			if (hasSixLock(lks, txNum)) {
				lks.requestList.remove(txNum);
				return;
			}
				
			try {
				Long first = lks.requestList.peek();
				while (!sixLockable(lks, txNum) || first != txNum) {
					anchor.wait();
					first = lks.requestList.peek();
				}
				lks.requestList.pop();
				lks.sixLocker = txNum;
				anchor.notifyAll();
			} catch (InterruptedException e) {
				throw new LockAbortException("abort tx." + txNum + " by interrupted");
			}
		}
	}

	/**
	 * Grants an islock on the specified item. If any conflict lock exists when
	 * the method is called, then the calling thread will be placed on a wait
	 * list until the lock is released. If the thread remains on the wait list
	 * for a certain amount of time, then an exception is thrown.
	 * 
	 * @param obj
	 *            a lockable item
	 * @param txNum
	 *            a transaction number
	 */
	void isLock(Object obj, long txNum) {
		Object anchor = getAnchor(obj);
		synchronized (anchor) {
			Lockers lks = prepareLockers(obj);

			if (hasIsLock(lks, txNum)) {
				lks.requestList.remove(txNum);
				return;
			}
				
			try {
				Long first = lks.requestList.peek();
				while (!isLockable(lks, txNum) || first != txNum) {
					anchor.wait();
					first = lks.requestList.peek();
				}
				lks.requestList.pop();
				lks.isLockers.add(txNum);
				anchor.notifyAll();
			} catch (InterruptedException e) {
				throw new LockAbortException("abort tx." + txNum + " by interrupted");
			}
		}
	}

	/**
	 * Grants an ixlock on the specified item. If any conflict lock exists when
	 * the method is called, then the calling thread will be placed on a wait
	 * list until the lock is released. If the thread remains on the wait list
	 * for a certain amount of time, then an exception is thrown.
	 * 
	 * @param obj
	 *            a lockable item
	 * @param txNum
	 *            a transaction number
	 */
	void ixLock(Object obj, long txNum) {
		Object anchor = getAnchor(obj);
		synchronized (anchor) {
			Lockers lks = prepareLockers(obj);

			if (hasIxLock(lks, txNum)) {
				lks.requestList.remove(txNum);
				return;
			}
				
			try {
				Long first = lks.requestList.peek();
				while (!ixLockable(lks, txNum) || first != txNum) {
					anchor.wait();
					first = lks.requestList.peek();
				}
				lks.requestList.pop();
				lks.ixLockers.add(txNum);
				anchor.notifyAll();
			} catch (InterruptedException e) {
				throw new LockAbortException("abort tx." + txNum + " by interrupted");
			}
		}
	}

	/**
	 * Releases the specified type of lock on an item holding by a transaction.
	 * If a lock is the last lock on that block, then the waiting transactions
	 * are notified.
	 * 
	 * @param obj
	 *            a lockable item
	 * @param txNum
	 *            a transaction number
	 * @param lockType
	 *            the type of lock
	 */
	void release(Object obj, long txNum, int lockType) {
		Object anchor = getAnchor(obj);
		synchronized (anchor) {
			Lockers lks = lockerMap.get(obj);
			/*
			 * In some situation, tx will release the lock of the object that
			 * have been released.
			 */
			if (lks != null) {
				releaseLock(lks, anchor, txNum, lockType);

				// Check if this transaction have any other lock on this object
				if (!sLocked(lks) && !xLocked(lks) && !sixLocked(lks)
						&& !isLocked(lks) && !ixLocked(lks)
						&& lks.requestList.isEmpty())
					lockerMap.remove(obj);
			}
		}
	}


	private void releaseLock(Lockers lks, Object anchor, long txNum,
			int lockType) {
		switch (lockType) {
		case X_LOCK:
			if (lks.xLocker == txNum) {
				lks.xLocker = -1;
				anchor.notifyAll();
			}
			return;
		case SIX_LOCK:
			if (lks.sixLocker == txNum) {
				lks.sixLocker = -1;
				anchor.notifyAll();
			}
			return;
		case S_LOCK:
			Set<Long> sl = lks.sLockers;
			if (sl != null && sl.contains(txNum)) {
				sl.remove((Long) txNum);
				if (sl.isEmpty())
					anchor.notifyAll();
			}
			return;
		case IS_LOCK:
			Set<Long> isl = lks.isLockers;
			if (isl != null && isl.contains(txNum)) {
				isl.remove((Long) txNum);
				if (isl.isEmpty())
					anchor.notifyAll();
			}
			return;
		case IX_LOCK:
			Set<Long> ixl = lks.ixLockers;
			if (ixl != null && ixl.contains(txNum)) {
				ixl.remove((Long) txNum);
				if (ixl.isEmpty())
					anchor.notifyAll();
			}
			return;
		default:
			throw new IllegalArgumentException();
		}
	}

	private Lockers prepareLockers(Object obj) {
		Lockers lockers = lockerMap.get(obj);
		if (lockers == null) {
			lockers = new Lockers();
			lockerMap.put(obj, lockers);
		}
		return lockers;
	}

	/*
	 * Verify if an item is locked.
	 */

	private boolean sLocked(Lockers lks) {
		return lks != null && lks.sLockers.size() > 0;
	}

	private boolean xLocked(Lockers lks) {
		return lks != null && lks.xLocker != -1;
	}

	private boolean sixLocked(Lockers lks) {
		return lks != null && lks.sixLocker != -1;
	}

	private boolean isLocked(Lockers lks) {
		return lks != null && lks.isLockers.size() > 0;
	}

	private boolean ixLocked(Lockers lks) {
		return lks != null && lks.ixLockers.size() > 0;
	}

	/*
	 * Verify if an item is held by a tx.
	 */

	private boolean hasSLock(Lockers lks, long txNum) {
		return lks != null && lks.sLockers.contains(txNum);
	}

	private boolean hasXLock(Lockers lks, long txNUm) {
		return lks != null && lks.xLocker == txNUm;
	}

	private boolean hasSixLock(Lockers lks, long txNum) {
		return lks != null && lks.sixLocker == txNum;
	}

	private boolean hasIsLock(Lockers lks, long txNum) {
		return lks != null && lks.isLockers.contains(txNum);
	}

	private boolean hasIxLock(Lockers lks, long txNum) {
		return lks != null && lks.ixLockers.contains(txNum);
	}

	private boolean isTheOnlySLocker(Lockers lks, long txNum) {
		return lks != null && lks.sLockers.size() == 1
				&& lks.sLockers.contains(txNum);
	}

	private boolean isTheOnlyIsLocker(Lockers lks, long txNum) {
		return lks != null && lks.isLockers.size() == 1
				&& lks.isLockers.contains(txNum);
	}

	private boolean isTheOnlyIxLocker(Lockers lks, long txNum) {
		return lks != null && lks.ixLockers.size() == 1
				&& lks.ixLockers.contains(txNum);
	}

	/*
	 * Verify if an item is lockable to a tx.
	 */

	private boolean sLockable(Lockers lks, long txNum) {
		return (!xLocked(lks) || hasXLock(lks, txNum))
				&& (!sixLocked(lks) || hasSixLock(lks, txNum))
				&& (!ixLocked(lks) || isTheOnlyIxLocker(lks, txNum));
	}

	private boolean xLockable(Lockers lks, long txNum) {
		return (!sLocked(lks) || isTheOnlySLocker(lks, txNum))
				&& (!sixLocked(lks) || hasSixLock(lks, txNum))
				&& (!ixLocked(lks) || isTheOnlyIxLocker(lks, txNum))
				&& (!isLocked(lks) || isTheOnlyIsLocker(lks, txNum))
				&& (!xLocked(lks) || hasXLock(lks, txNum));
	}

	private boolean sixLockable(Lockers lks, long txNum) {
		return (!sixLocked(lks) || hasSixLock(lks, txNum))
				&& (!ixLocked(lks) || isTheOnlyIxLocker(lks, txNum))
				&& (!sLocked(lks) || isTheOnlySLocker(lks, txNum))
				&& (!xLocked(lks) || hasXLock(lks, txNum));
	}

	private boolean ixLockable(Lockers lks, long txNum) {
		return (!sLocked(lks) || isTheOnlySLocker(lks, txNum))
				&& (!sixLocked(lks) || hasSixLock(lks, txNum))
				&& (!xLocked(lks) || hasXLock(lks, txNum));
	}

	private boolean isLockable(Lockers lks, long txNum) {
		return (!xLocked(lks) || hasXLock(lks, txNum));
	}
}
