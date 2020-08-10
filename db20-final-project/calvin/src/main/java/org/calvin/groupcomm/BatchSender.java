package org.calvin.groupcomm;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.vanilladb.comm.client.VanillaCommClient;
import org.vanilladb.comm.view.ProcessType;

public class BatchSender implements Runnable{
	
	private List<Serializable> messageList = new CopyOnWriteArrayList<Serializable>();
	private int targetServerId;
	private VanillaCommClient client;
	
	public BatchSender(int targetServerId, VanillaCommClient client) {
		this.targetServerId = targetServerId;
		this.client = client;
	}
	
	@Override
    public void run() {
		sendRequestPeriodically();
    }
	
	private void sendRequestPeriodically() {
		while (true) {
			while (messageList.isEmpty()) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
			}
			
			//synchronized?
			synchronized (this) {
				for(Serializable m: messageList) {
					client.sendP2pMessage(ProcessType.SERVER, targetServerId, m);
				}
				messageList.clear();
			}
			
		}
	}
	
	synchronized public void addMessage(Serializable m) {
		messageList.add(m);
	}
	
}