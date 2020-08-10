package org.calvin.groupcomm;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Server2Server implements Serializable {
	
	private static final long serialVersionUID = -8035894777958203133L;
	private List<Pair> list = new ArrayList<Pair>();
	
	public Server2Server(List<Pair> list) {
		this.list = list;
	}
	
	public List<Pair> getList() {
		return list;
	}
}