package org.calvin.groupcomm;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.vanilladb.comm.client.VanillaCommClient;
import org.vanilladb.comm.client.VanillaCommClientListener;
import org.vanilladb.comm.view.ProcessType;
import org.vanilladb.comm.view.ProcessView;
import org.vanilladb.core.remote.storedprocedure.SpResultSet;

public class GroupClient implements VanillaCommClientListener{
	
	private static Logger logger = Logger.getLogger(GroupClient.class.getName());
	private int selfId;
	private Map<Integer, BlockingQueue<ServerResponse>> rte2results = new ConcurrentHashMap<Integer, BlockingQueue<ServerResponse>>();
	private BatchSender batchSender;
	
	public GroupClient(int clientID) {
		if (logger.isLoggable(Level.INFO))
			logger.info("Initializing the client...");
		
		this.selfId = clientID;
		VanillaCommClient client = new VanillaCommClient(this.selfId, this);
		new Thread(client).start();
		int serverCount = ProcessView.buildServersProcessList(-1).getSize();
		this.batchSender = new BatchSender(0, client);
		new Thread(this.batchSender).start();
	}
	
	public SpResultSet callStoredProc(int rteID, int procID, Object... pars) {
		
		BlockingQueue<ServerResponse> tmp = rte2results.get(rteID);
		if (tmp == null) {
			tmp = new LinkedBlockingQueue<ServerResponse>();
			rte2results.put(rteID, tmp);
		}
		
		
		Serializable m = new StoredProcInfo(rteID, selfId, procID, (long) 0, pars);
		batchSender.addMessage(m);
		
		try {
			ServerResponse sr = tmp.take();
			return sr.getResultSet();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public void onReceiveP2pMessage(ProcessType senderType, int senderId, Serializable message) {
		if (senderType == ProcessType.SERVER) {
			ServerResponse sr = (ServerResponse) message;
//			System.out.println("Received a P2P message from " + senderType + " " + senderId
//					+ ", rte: " + sr.getRteID() + ", is ResultSet Null?" + (sr.getResultSet() == null) );
			rte2results.get(sr.getRteID()).add(sr);
		}
	}
	
}
