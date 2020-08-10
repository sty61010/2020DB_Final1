package org.calvin.groupcomm;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.calvin.cache.CalvinRecord;
import org.calvin.server.Calvin;
import org.vanilladb.comm.server.VanillaCommServer;
import org.vanilladb.comm.server.VanillaCommServerListener;
import org.vanilladb.comm.view.ProcessType;
import org.vanilladb.core.remote.storedprocedure.SpResultSet;

public class GroupServer implements VanillaCommServerListener {
	private static Logger logger = Logger.getLogger(GroupServer.class.getName());
	
	private static final BlockingQueue<Serializable> msgQueue =
			new LinkedBlockingDeque<Serializable>();
	private static final BlockingQueue<Serializable> totalOrderQueue =
			new LinkedBlockingDeque<Serializable>();
	
	private int nodeID;
	public VanillaCommServer server;
	
	public GroupServer(int nodeID) {
		this.nodeID = nodeID;
		this.server = new VanillaCommServer(this.nodeID, this);
		new Thread(server).start();
		createClientRequestHandler(server);
		passTotalOrdering(server);
	}

	private static void createClientRequestHandler(
			final VanillaCommServer server) {
		new Thread(new Runnable() {

			@Override
			public void run() {
				while (true) {
					try {
						Serializable message = msgQueue.take();
						server.sendTotalOrderMessage(message);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}

		}).start();
	}
	
	private static void passTotalOrdering(
			final VanillaCommServer server) {
		new Thread(new Runnable() {

			@Override
			public void run() {
				while (true) {
					try {
						Serializable message = totalOrderQueue.take();
						StoredProcInfo mes = (StoredProcInfo) message;
						//TODO:
						//call scheduler
//						System.out.println("Pass a total order message with tx:" + mes.getTxNum());
						Calvin.scheduler.pushSchedule(mes);
						Calvin.scheduler.runSchedule();
						
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}

		}).start();
	}

	@Override
	public void onServerReady() {
		if (logger.isLoggable(Level.INFO))
			logger.info("The server " + nodeID + " is ready!");
	}

	@Override
	public void onServerFailed(int failedServerId) {
		if (logger.isLoggable(Level.SEVERE))
			logger.severe("Server " + failedServerId + " failed");
	}

	@Override
	public void onReceiveP2pMessage(ProcessType senderType, int senderId, Serializable message) {
		if (senderType == ProcessType.CLIENT) {
			try {
				msgQueue.put(message);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} else if (senderType == ProcessType.SERVER) {
			System.out.println("receive remote msg successfully");
			//TODO:
			//cache the records received from other
			Server2Server mes = (Server2Server) message;
			List<Pair> list = mes.getList();
			for(Pair p: list) {
				BlockingQueue<CalvinRecord> tmp = null;
				tmp = Calvin.cache.remoteRecords.get(p.getRecKey());
				if (tmp == null) {
					tmp = new LinkedBlockingQueue<CalvinRecord>();
					Calvin.cache.remoteRecords.put(p.getRecKey(), tmp);
				}

				try {
					tmp.put(p.getCalvinRecord());
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
		} else {
			if (logger.isLoggable(Level.SEVERE))
				logger.severe("Something went wrong.");
		}
	}

	@Override
	public void onReceiveTotalOrderMessage(long serialNumber, Serializable message) {
		if (logger.isLoggable(Level.INFO))
			logger.info("Server" + this.nodeID + "receive total order message");
		try {
			totalOrderQueue.put(message);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void sendStoredProcResults(SpResultSet resultset, VanillaCommServer server, int clientID, int rteID, Long txn) {
		//TODO:
		//send back spr to client through p2p
		ServerResponse sr = new ServerResponse(rteID, resultset, txn);
		server.sendP2pMessage(ProcessType.CLIENT, clientID , sr);
//		System.out.println("send to client successfully");
	}
	
	public void sendRecord2RemoteServer(int serverID, List<Pair> list, VanillaCommServer server) {
		//TODO:
		//send record to remote server p2p
		Server2Server s2s = new Server2Server(list);
		server.sendP2pMessage(ProcessType.SERVER, serverID, s2s);
	}
	
	public int getNodeID() {
		return this.nodeID;
	}
	
}

