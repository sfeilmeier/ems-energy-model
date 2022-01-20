package io.openems.controller.emsig.ojalgo;

import org.ojalgo.optimisation.Variable;

public class EV {


	public static class Charge {
		public Variable power = null;
		// public Variable mode = null;
	}
	
	public final Charge charge = new Charge();
	
	public Variable energy = null;
	
	public EV() {
		
	}
}
