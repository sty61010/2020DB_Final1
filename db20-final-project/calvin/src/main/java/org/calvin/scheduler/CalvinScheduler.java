package org.calvin.scheduler;

import java.util.LinkedList;
import java.util.Queue;

import org.calvin.storedprocedures.CalvinStoredProcFactory;
import org.calvin.storedprocedures.CalvinStoredProcedure;
import org.calvin.groupcomm.StoredProcInfo;
import org.calvin.server.Calvin;
public class CalvinScheduler {

	private CalvinStoredProcFactory cspFactory;
	private Queue<StoredProcInfo> spiList = new LinkedList<StoredProcInfo>();
	
	public CalvinScheduler (CalvinStoredProcFactory factory) {
		this.cspFactory = factory;
	}
	
	public void pushSchedule(StoredProcInfo... infos) {
		for(int i=0; i<infos.length;i++) {
			spiList.add(infos[i]);
		}
	}

	public void runSchedule() {
		while(spiList.size() != 0) {
			StoredProcInfo info = spiList.remove();
			CalvinStoredProcedure<?> csp = (CalvinStoredProcedure<?>) cspFactory.getStroredProcedure(info.getPorcID());
//			if (VanillaDb.spFactory() == null) System.out.println("fucking null");
//			System.out.println(info.getPorcID() + ", " + info.getPars());
//			CalvinStoredProcedure<?> csp = (CalvinStoredProcedure<?>) VanillaDb.spFactory().getStroredProcedure(info.getPorcID());
			csp.setTxNum(info.getTxNum());
			csp.prepare(info.getPars());
			csp.setID(info.getClienID(), info.getRteID(), info.getTxNum());
			Calvin.taskMgr().getExecutor().execute(csp);
			
		}
	}
}
