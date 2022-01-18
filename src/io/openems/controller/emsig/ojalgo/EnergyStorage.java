package io.openems.controller.emsig.ojalgo;

import org.ojalgo.optimisation.Variable;

public class EnergyStorage {

	public static class Charge {
		public Variable power = null;
		//public Variable mode = null;
	}

	public static class Discharge {
		public Variable power = null;
		//public Variable mode = null;

	}

	public final Charge charge = new Charge();
	public final Discharge discharge = new Discharge();
	

	public Variable power = null;
	public Variable energy = null;

	public EnergyStorage() {
	}

}
