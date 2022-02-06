package io.openems.controller.emsig.ojalgo;

import org.ojalgo.optimisation.Variable;

public class EnergyGrid {

	public static class Buy {
		public Variable power = null;
		public double cost;
	}

	public static class Sell {
		public Variable power = null;
		public double revenue;
		
		//In case of 2 PVs
//		public double[] revenue = new double[2];
	}

	public final Sell sell = new Sell();
	public final Buy buy = new Buy();

	public Variable power;
	public Variable isBuy;

	public EnergyGrid() {

	}
}
