package io.openems.controller.emsig.ojalgo;

import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;

public class EnergyGrid {

	public static class Buy {
		public IntVar power = null;
		public int cost;
	}

	public static class Sell {
		public IntVar power = null;
		public int revenue;
	}

	public final Sell sell = new Sell();
	public final Buy buy = new Buy();

	public IntVar power;
	public BoolVar isBuy;

	public EnergyGrid() {

	}
}
