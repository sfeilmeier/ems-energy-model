package io.openems.controller.emsig.ojalgo;

import java.util.ArrayList;
import java.util.List;

public class Period {

	private final static int MINUTES_PER_DAY = 1440;

	public static Period from(int thisIndex, int maxIndex, int lengthInMinutes) {
		StringBuilder b = new StringBuilder();

		// More than one day?
		if (maxIndex * lengthInMinutes > MINUTES_PER_DAY) {
			b.append("D");
			final int day = thisIndex / (MINUTES_PER_DAY / lengthInMinutes);
			b.append(day);
			b.append("_");
			thisIndex -= day * (MINUTES_PER_DAY / lengthInMinutes); // normalize to first day from here
		}

		// Period of the day
		final int minute = thisIndex * lengthInMinutes;
		b.append(String.format("%02d", minute / 60));
		b.append(":");
		b.append(String.format("%02d", minute % 60));
		return new Period(b.toString());
	}

	public final String name;
	public final EnergyGrid grid = new EnergyGrid();
	public final EnergyStorage ess = new EnergyStorage();
	public final PV pv = new PV();
	public final HouseHold hh = new HouseHold();
	public final EV ev = new EV();

	// If we want to have multiple EVs
//	public final EV ev1 = new EV();
//	public final EV ev2 = new EV();
	public final List<EV> evs = new ArrayList<EV>();

	// If we want to have multiple PVs
	public final List<PV> pvs = new ArrayList<PV>();

	private Period(String name) {
		this.name = name;
	}

}
