package org.vanilladb.bench.server.procedure;

import org.calvin.storedprocedures.CalvinStoredProcFactory;
import org.calvin.storedprocedures.CalvinStoredProcedure;
import org.vanilladb.bench.ControlTransactionType;
import org.vanilladb.core.sql.storedprocedure.StoredProcedure;
import org.vanilladb.core.sql.storedprocedure.StoredProcedureFactory;

public class BasicStoredProcFactory implements CalvinStoredProcFactory {
	
	private CalvinStoredProcFactory underlayerFactory;
	
	public BasicStoredProcFactory(CalvinStoredProcFactory underlayerFactory) {
		this.underlayerFactory = underlayerFactory;
	}

	@Override
	public CalvinStoredProcedure<?> getStroredProcedure(int pid) {
		ControlTransactionType txnType = ControlTransactionType.fromProcedureId(pid);
		if (txnType != null) {
			switch (txnType) {
			case START_PROFILING:
				return new StartProfilingProc();
			case STOP_PROFILING:
				return new StopProfilingProc();
			}
		}
		
		return underlayerFactory.getStroredProcedure(pid);
	}
}
