/*******************************************************************************
 * Copyright 2016, 2018 elasql.org contributors
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
//package org.elasql.bench.server.procedure.calvin.micro;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.vanilladb.bench.benchmarks.tpcc.TpccConstants;
import org.vanilladb.bench.server.param.micro.TestbedLoaderParamHelper;
import org.vanilladb.core.server.VanillaDb;
import org.vanilladb.core.storage.tx.recovery.CheckpointTask;
import org.vanilladb.core.storage.tx.recovery.RecoveryMgr;

public class MicroTestbedLoaderProc extends CalvinStoredProcedure<TestbedLoaderParamHelper> {
//	private static Logger logger = Logger.getLogger(MicroTestbedLoaderProc.class.getName());

	public MicroTestbedLoaderProc(long txNum) {
		super(txNum, new TestbedLoaderParamHelper());
	}

	@Override
	protected void prepareKeys() {
		// do nothing
		// XXX: We should lock those tables
		// List<String> writeTables = Arrays.asList(paramHelper.getTables());
		// localWriteTables.addAll(writeTables);

	}
	
	@Override
	protected void executeSql(Map<RecKey, CalvinRecord> reads) {
		createSchemas();

		// Generate item records
		generateItems(1, getParamHelper().getNumberOfItems());


	}
	

	
	private void createSchemas() {
		if (logger.isLoggable(Level.FINE))
			logger.info("Create tables...");
		
		for (String cmd : paramHelper.getTableSchemas())
			VanillaDb.newPlanner().executeUpdate(cmd, tx);
		
		if (logger.isLoggable(Level.FINE))
			logger.info("Create indexes...");

		for (String cmd : paramHelper.getIndexSchemas())
			VanillaDb.newPlanner().executeUpdate(cmd, tx);
		
		if (logger.isLoggable(Level.FINE))
			logger.info("Finish creating schemas.");
	}

	private void generateItems(int startIId, int endIId) {
		if (logger.isLoggable(Level.FINE))
			logger.info("Start populating items from i_id=" + startIId + " to i_id=" + endIId);

		int iid, iimid;
		String iname, idata;
		double iprice;
		String sql;
		for (int i = startIId; i <= endIId; i++) {
			iid = i;

			// Deterministic value generation by item id
			iimid = iid % (TpccConstants.MAX_IM - TpccConstants.MIN_IM) + TpccConstants.MIN_IM;
			iname = String.format("%0" + TpccConstants.MIN_I_NAME + "d", iid);
			iprice = (iid % (int) (TpccConstants.MAX_PRICE - TpccConstants.MIN_PRICE)) + TpccConstants.MIN_PRICE;
			idata = String.format("%0" + TpccConstants.MIN_I_DATA + "d", iid);

			sql = "INSERT INTO item(i_id, i_im_id, i_name, i_price, i_data) VALUES (" + iid + ", " + iimid + ", '"
					+ iname + "', " + iprice + ", '" + idata + "' )";

			int result = VanillaDb.newPlanner().executeUpdate(sql, tx);
			if (result <= 0)
				throw new RuntimeException();
		}

		if (logger.isLoggable(Level.FINE))
			logger.info("Populating items completed.");
	}
}
