package io.openems.controller.emsig.ojalgo;

// import org.ojalgo.optimisation.Variable;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;

public class EV {


	public static class Charge {
		public IntVar power = null;
		// public IntVar mode = null; maybe BoolVar
	}
	
	public final Charge charge = new Charge();
	
	public IntVar energy = null;
	
	public EV() {
		
	}
}