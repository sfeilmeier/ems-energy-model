package io.openems.controller.emsig.ojalgo;

import static io.openems.controller.emsig.ojalgo.Constants.ESS_INITIAL_ENERGY;
import static io.openems.controller.emsig.ojalgo.Constants.ESS_MAX_CHARGE;
import static io.openems.controller.emsig.ojalgo.Constants.ESS_MAX_DISCHARGE;
import static io.openems.controller.emsig.ojalgo.Constants.ESS_MAX_CHARGE_DIFFERENCE;
import static io.openems.controller.emsig.ojalgo.Constants.ESS_MAX_ENERGY;
import static io.openems.controller.emsig.ojalgo.Constants.ESS_MIN_ENERGY;
import static io.openems.controller.emsig.ojalgo.Constants.GRID_BUY_COST;
import static io.openems.controller.emsig.ojalgo.Constants.GRID_BUY_LIMIT;
import static io.openems.controller.emsig.ojalgo.Constants.GRID_SELL_LIMIT;
import static io.openems.controller.emsig.ojalgo.Constants.GRID_SELL_REVENUE;
import static io.openems.controller.emsig.ojalgo.Constants.GRID_SELL_REVENUE0;
import static io.openems.controller.emsig.ojalgo.Constants.GRID_SELL_REVENUE1;
import static io.openems.controller.emsig.ojalgo.Constants.MINUTES_PER_PERIOD;
import static io.openems.controller.emsig.ojalgo.Constants.PV_POWER;
// import static io.openems.controller.emsig.ojalgo.Constants.PV0_POWER;
// import static io.openems.controller.emsig.ojalgo.Constants.PV1_POWER;
import static io.openems.controller.emsig.ojalgo.Constants.HH_LOAD;
import static io.openems.controller.emsig.ojalgo.Constants.EV_AVAIL;
import static io.openems.controller.emsig.ojalgo.Constants.ESS_CHARGE_EFFICIENCY;
import static io.openems.controller.emsig.ojalgo.Constants.ESS_DISCHARGE_EFFICIENCY;
import static io.openems.controller.emsig.ojalgo.Constants.NO_OF_PERIODS;
import static io.openems.controller.emsig.ojalgo.Constants.EV_INITIAL_ENERGY;
import static io.openems.controller.emsig.ojalgo.Constants.EV_MAX_ENERGY;
import static io.openems.controller.emsig.ojalgo.Constants.EV_REQUIRED_ENERGY;
import static io.openems.controller.emsig.ojalgo.Constants.EV_MIN_CHARGE;
import static io.openems.controller.emsig.ojalgo.Constants.EV_MAX_CHARGE;
import static io.openems.controller.emsig.ojalgo.Constants.EV_CHARGE_EFFICIENCY;

import static java.math.BigDecimal.ONE;

import java.awt.Color;
import java.io.IOException;
import java.math.RoundingMode;

import javax.swing.text.AttributeSet.ColorAttribute;

import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.integer.IntegerStrategy;
import org.ojalgo.type.CalendarDateUnit;
import org.ojalgo.type.context.NumberContext;

import com.github.plot.Plot;
import com.github.plot.Plot.AxisFormat;
import com.github.plot.Plot.Data;

public class EnergyModel {

	public final ExpressionsBasedModel model;
	public final Period[] periods;

	public EnergyModel() {
//		model = new ExpressionsBasedModel();
		var options = new Optimisation.Options();
//      options.mip_defer = 0.5; // default: 0.9
//      options.mip_gap = 1.0E-2; // default: 1.0E-6
		options.integer(IntegerStrategy.DEFAULT.withGapTolerance(NumberContext.of(4)));
//		options.feasibility = NumberContext.of(12, 8);  // default: NumberContext.of(12, 8)
//		options.iterations_abort = Integer.MAX_VALUE
//		options.solution = NumberContext.ofScale(14).withMode(RoundingMode.HALF_DOWN);
		options.time_abort = 600000; // default: CalendarDateUnit.DAY.toDurationInMillis();
		options.time_suffice = 200000; // default: CalendarDateUnit.HOUR.toDurationInMillis();
		model = new ExpressionsBasedModel(options);

		// New constraint: prefer ess.charge over grid.sell to make up
		// for forecast errors concerning PV power and HH load
		// TODO In the future, one shall add an additional objective function
		// that makes sure that charging is prioritized over selling to the grid

		// Find first index such that the potential ESS energy exceeds
		// the max energy; for all periods smaller than said index
		// impose a new ESS charging constraint (prioritization)
		// Note: the present approach does not yield
		// the actual potential ESS energy;
		// A priori, we do not know how much energy is really used to
		// serve the load, and we have charging limitations and efficiencies
		// However, it is a reasonable guiding principle and yields
		// promising simulation results
		int pvSum = 0;
		int hhSum = 0;

		// If p.pv.power.prod - p.hh.power.cons >> ESS_MAX_CHARGE at the
		// beginning of the simulation, then the ems will stop charging (prioritization)
		// way too early;
		// For this special case, we need different/additional constraints
		int essPotentialEnergyIndex = -1;
		int essPotentialEnergy = ESS_INITIAL_ENERGY * 60;
		for (int j = 0; j < NO_OF_PERIODS; j++) {
			if (PV_POWER[j] > HH_LOAD[j]) {
				essPotentialEnergyIndex = j;
			} else {
				break;
			}
		}

		// If the EV is only available while PV_POWER >= HH_LOAD, and if
		// the ESS will not be fully charged "in time", then it will happen that
		// the ESS is charged with PV power while most of the power used to serve
		// the EV is bought from the grid; to avoid this, we need to check an
		// additional assumption, before we impose the charge prioritization constraint
		// If this assumption is not satisfied, then we need to drop charge
		// prioritization and only impose constraints concerning evenly distributed
		// charging
		int availWhileNoPV = 0;
		int evPotentialEnergy = EV_INITIAL_ENERGY * 60;
		for (int j = 0; j < NO_OF_PERIODS; j++) {
			if (HH_LOAD[j] >= PV_POWER[j] && EV_AVAIL[j] == 1) {
				availWhileNoPV += 1;
			}
		}
		evPotentialEnergy = EV_INITIAL_ENERGY * 60
				+ EV_MAX_CHARGE * availWhileNoPV * MINUTES_PER_PERIOD * EV_CHARGE_EFFICIENCY / 100;
		for (int j = 0; j < NO_OF_PERIODS; j++) {
			if (EV_AVAIL[j] == 1 && PV_POWER[j] - HH_LOAD[j] >= ESS_MAX_CHARGE + EV_MIN_CHARGE) {
				int evPotentialPower = Math.min(PV_POWER[j] - HH_LOAD[j] - ESS_MAX_CHARGE, EV_MAX_CHARGE);
				evPotentialEnergy = evPotentialEnergy
						+ evPotentialPower * MINUTES_PER_PERIOD * EV_CHARGE_EFFICIENCY / 100;
			}
		}

		// Initialize Periods
		this.periods = new Period[NO_OF_PERIODS];
		for (int i = 0; i < periods.length; i++) {
			Period p = Period.from(i, periods.length, MINUTES_PER_PERIOD);
			periods[i] = p;

			// Specify the PV power and the HH load for each period
			p.pv.power.prod = PV_POWER[i];
			p.hh.power.cons = HH_LOAD[i];
			p.ev.isAvail = EV_AVAIL[i];

			pvSum += p.pv.power.prod;
			hhSum += p.hh.power.cons;

			// In case of 2 PVs
//			final PV pv0 = new PV();
//			final PV pv1 = new PV();
//			p.pvs.add(pv0);
//			p.pvs.add(pv1);
//			p.pvs.get(0).power.prod = PV0_POWER[i];
//			p.pvs.get(1).power.prod = PV_POWER[i] - PV0_POWER[i];
//			p.evs.get(0).isAvail = EV_AVAIL[i][0];
//			p.evs.get(1).isAvail = EV_AVAIL[i][1];

			/*
			 * Energy Storage
			 */
			// Upper and lower bounds for charge and discharge
			// Upper bound for charge is given by the minimum
			// of ESS_MAX_CHARGE and p.power.prod for each period p.
			// This way, the ESS is not charged with power bought
			// from the grid
			// 0 = ess.power - ess.discharge.power + ess.charge.power
			p.ess.power = model.addVariable("ESS_" + p.name + "_Power") //
					.lower(ESS_MAX_CHARGE * -1) //
					.upper(ESS_MAX_DISCHARGE);
			p.ess.discharge.power = model.addVariable("ESS_" + p.name + "_Discharge_Power") //
					.lower(0) //
					.upper(ESS_MAX_DISCHARGE);
			p.ess.charge.power = model.addVariable("ESS_" + p.name + "_Charge_Power") //
					.lower(0) //
					.upper(Math.min(ESS_MAX_CHARGE, p.pv.power.prod)); //
//					.upper(Math.min(ESS_MAX_CHARGE, p.pvs.get(0).power.prod + p.pvs.get(1).power.prod));
			model.addExpression("ESS_" + p.name + "_ChargeDischargePower_Expr") //
					.set(p.ess.power, ONE) //
					.set(p.ess.discharge.power, ONE.negate()) //
					.set(p.ess.charge.power, ONE) //
					.level(0);

			// Within a period, only charging XOR discharging is allowed
//			if (p.pv.power.prod > p.hh.power.cons) {
			if (p.pv.power.prod > 750) {
				p.ess.isCharged = model.addVariable("ESS_" + p.name + "_Charge_Mode") //
						.binary();
				// 0 <= p.ess.discharge.power + p.ess.isCharged * ESS_MAX_DISCHARGE <=
				// ESS_MAX_DISCHARGE
				model.addExpression("ESS_" + p.name + "_Charge_Mode_Expr") //
						.set(p.ess.isCharged, ESS_MAX_DISCHARGE) //
						.set(p.ess.discharge.power, ONE) //
						.lower(0) //
						.upper(ESS_MAX_DISCHARGE);
				// 0 <= p.ess.isCharged * ESS_MAX_DISCHARGE - p.ess.charge.power <=
				// ESS_MAX_DISCHARGE
				model.addExpression("ESS_" + p.name + "_Charge_Mode2_Expr") //
						.set(p.ess.isCharged, ESS_MAX_CHARGE) //
						.set(p.ess.charge.power, ONE.negate()) //
						.lower(0) //
						.upper(ESS_MAX_CHARGE);
			}

			// Sum Energy
			// Take the charge and discharge efficiencies of the ESS into account
			// By doing so, it is less likely that the ESS both charges and discharges
			// within the same period, but it is not inhibited
			p.ess.energy = model.addVariable("ESS_" + p.name + "_Energy") //
					.lower(ESS_MIN_ENERGY * 60 /* [Wmin] */) //
					.upper(ESS_MAX_ENERGY * 60 /* [Wmin] */);
			// periods[0].ess.energy = ESS_INITIAL_ENERGY * 60
			// - period[0].ess.discharge.power * MINUTES_PER_PERIOD *
			// ESS_DISCHARGE_EFFICIENCY
			// + period[0].ess.charge.power * MINUTES_PER_PERIOD * ESS_CHARGE_EFFICIENCY
			if (i == 0) {
				model.addExpression("ESS_" + p.name + "_Energy_Expr_1st") //
						.set(p.ess.energy, ONE) //
//						.set(p.ess.power, MINUTES_PER_PERIOD) //
						.set(p.ess.charge.power, -1 * MINUTES_PER_PERIOD * ESS_CHARGE_EFFICIENCY / 100) //
						.set(p.ess.discharge.power, MINUTES_PER_PERIOD * ESS_DISCHARGE_EFFICIENCY / 100) //
						.level(ESS_INITIAL_ENERGY * 60); //
			} else {
				// periods[i].ess.energy = periods[i-1].ess.energy
				// - period[0].ess.discharge.power * MINUTES_PER_PERIOD *
				// ESS_DISCHARGE_EFFICIENCY
				// + period[0].ess.charge.power * MINUTES_PER_PERIOD * ESS_CHARGE_EFFICIENCY
				model.addExpression("ESS_" + p.name + "_Energy_Expr") //
						.set(periods[i - 1].ess.energy, ONE) //
						.set(p.ess.discharge.power, MINUTES_PER_PERIOD * -1 * ESS_DISCHARGE_EFFICIENCY / 100) //
						.set(p.ess.charge.power, MINUTES_PER_PERIOD * ESS_CHARGE_EFFICIENCY / 100) //
						.set(p.ess.energy, ONE.negate()) //
						.level(0);
			}
			// p.ess.energy = periods[i-1].ess.energy - p.ess.power*MINUTES_PER_PERIOD -
			// ESS_EFFICIENCY*60

			/*
			 * EV
			 */
			// EV power and EV mode
			// If the EV is not available, isCharged = 1, chargePower = 0
			// Either allow no charge power (if isCharged = 1) or allow
			// a charge power in [EV_MIN_CHARGE, EV_MAX_CHARGE] (if isCharged = 0)
			p.ev.charge.power = model.addVariable("EV_" + p.name + "_Charge_Power") //
					.lower(0) //
					.upper(EV_MAX_CHARGE);
			p.ev.isCharged = model.addVariable("EV_" + p.name + "_Charge_Mode") //
					.binary();
			// p.ev.isCharged + p.ev.isAvail >= 1
			// This guarantees that the EV is not charged whenever it is not plugged
			model.addExpression("EV_" + p.name + "_Charge_Decision_Expr") //
					.set(p.ev.isCharged, ONE) //
					.lower(1 - p.ev.isAvail);
			// EV_MIN_CHARGE <= p.ev.isCharged * EV_MAX_CHARGE + p.ev.charge.power <=
			// EV_MAX_CHARGE
			model.addExpression("EV_" + p.name + "_Charge_Power_Expr") //
					.set(p.ev.isCharged, EV_MAX_CHARGE) //
					.set(p.ev.charge.power, ONE) //
					.lower(EV_MIN_CHARGE) //
					.upper(EV_MAX_CHARGE);

			// EV Energy
			p.ev.energy = model.addVariable("EV_" + p.name + "Energy") //
					.lower(0) //
					.upper(EV_MAX_ENERGY * 60); // [Wmin]
			if (i == 0) {
				// periods[0].ev.energy = EV_INITIAL_ENERGY * 60
				// + periods[0].ev.charge.power * MINUTES_PER_PERIOD * EV_CHARGE_EFFICIENCY
				model.addExpression(p.name + "_EV_Energy_Expr") //
						.set(p.ev.energy, ONE) //
						.set(p.ev.charge.power, -1 * MINUTES_PER_PERIOD * EV_CHARGE_EFFICIENCY / 100) //
						.level(EV_INITIAL_ENERGY * 60);
			} else {
				// periods[i].ev.energy = periods[i-1].ev.energy
				// + periods[i].ev.charge.power * MINUTES_PER_PERIOD * EV_CHARGE_EFFICIENCY
				model.addExpression(p.name + "_EV_Energy_Expr") //
						.set(p.ev.energy, ONE.negate()) //
						.set(p.ev.charge.power, MINUTES_PER_PERIOD * EV_CHARGE_EFFICIENCY / 100) //
						.set(periods[i - 1].ev.energy, ONE) //
						.level(0);
			}

			// In case of multiple EVs
			// TODO currently 2, but this has to be generalized eventually
			// Defining the EVs ''by hand'' only makes sense if their
			// technical specifications differ (max energy, min charge, max charge, ...)
//			final EV ev0 = new EV();
//			final EV ev1 = new EV();
//			p.evs.add(ev0);
//			p.evs.add(ev1);
//			p.evs.get(0).charge.power = model.addVariable("EV0_" + p.name + "_Charge_Power") //
//					.lower(0) //
//					.upper(EV_MAX_CHARGE);
//			p.evs.get(1).charge.power = model.addVariable("EV1_" + p.name + "_Charge_Power") //
//					.lower(0) //
//					.upper(EV_MAX_CHARGE);
//			p.evs.get(0).isCharged = model.addVariable("EV0_" + p.name + "_Charge_Mode") //
//					.binary();
//			p.evs.get(1).isCharged = model.addVariable("EV1_" + p.name + "_Charge_Mode") //
//					.binary();
//
//			model.addExpression("EV0_" + p.name + "_Charge_Decision_Expr") //
//					.set(p.evs.get(0).isCharged, ONE) //
//					.lower(1 - p.evs.get(0).isAvail);
//			model.addExpression("EV0_" + p.name + "_Charge_Power_Expr") //
//					.set(p.evs.get(0).isCharged, EV_MAX_CHARGE) //
//					.set(p.evs.get(0).charge.power, ONE) //
//					.lower(EV_MIN_CHARGE) //
//					.upper(EV_MAX_CHARGE);
//			model.addExpression("EV1_" + p.name + "_Charge_Decision_Expr") //
//					.set(p.evs.get(1).isCharged, ONE) //
//					.lower(1 - p.evs.get(1).isAvail);
//			model.addExpression("EV1_" + p.name + "_Charge_Power_Expr") //
//					.set(p.evs.get(1).isCharged, EV_MAX_CHARGE) //
//					.set(p.evs.get(1).charge.power, ONE) //
//					.lower(EV_MIN_CHARGE) //
//					.upper(EV_MAX_CHARGE);
//
//			p.evs.get(0).energy = model.addVariable("EV0_" + p.name + "Energy") //
//					.lower(0) //
//					.upper(EV_MAX_ENERGY * 60); // [Wmin]
//			if (i == 0) {
//				model.addExpression(p.name + "_EV0_Energy_Expr") //
//						.set(p.evs.get(0).energy, ONE) //
//						.set(p.evs.get(0).charge.power, -1 * MINUTES_PER_PERIOD * EV_CHARGE_EFFICIENCY / 100) //
//						.level(EV_INITIAL_ENERGY * 60);
//			} else {
//				model.addExpression(p.name + "_EV0_Energy_Expr") //
//						.set(p.evs.get(0).energy, ONE.negate()) //
//						.set(p.evs.get(0).charge.power, MINUTES_PER_PERIOD * EV_CHARGE_EFFICIENCY / 100) //
//						.set(periods[i - 1].evs.get(0).energy, ONE) //
//						.level(0);
//			}
//			p.evs.get(1).energy = model.addVariable("EV1_" + p.name + "Energy") //
//					.lower(0) //
//					.upper(EV_MAX_ENERGY * 60); // [Wmin]
//			if (i == 0) {
//				model.addExpression(p.name + "_EV1_Energy_Expr") //
//						.set(p.evs.get(1).energy, ONE) //
//						.set(p.evs.get(1).charge.power, -1 * MINUTES_PER_PERIOD * EV_CHARGE_EFFICIENCY / 100) //
//						.level(EV_INITIAL_ENERGY * 60);
//			} else {
//				model.addExpression(p.name + "_EV1_Energy_Expr") //
//						.set(p.evs.get(1).energy, ONE.negate()) //
//						.set(p.evs.get(1).charge.power, MINUTES_PER_PERIOD * EV_CHARGE_EFFICIENCY / 100) //
//						.set(periods[i - 1].evs.get(1).energy, ONE) //
//						.level(0);
//			}

			/*
			 * Grid
			 */
			// Power Balance Equation:
			// p.hh.power.cons - p.pv.power.prod = p.ess.power + p.grid.power - p.ev.power(s)
			// p.grid.power =  grid.buy.power - p.grid.sell.power
			p.grid.power = model.addVariable("Grid_" + p.name + "_Power"); //
			model.addExpression(p.name + "_Power_Balance") //
					.set(p.ess.power, ONE) //
					.set(p.grid.power, ONE) //
					.set(p.ev.charge.power, ONE.negate()) //
//					.set(p.evs.get(0).charge.power, ONE.negate()) //
//					.set(p.evs.get(1).charge.power, ONE.negate()) //
					.level(p.hh.power.cons - p.pv.power.prod);
//					.level(p.hh.power.cons - p.pvs.get(0).power.prod - p.pvs.get(1).power.prod);
			p.grid.buy.power = model.addVariable("Grid_" + p.name + "_Buy_Power") //
					.lower(0) //
					.upper(GRID_BUY_LIMIT);
			p.grid.sell.power = model.addVariable("Grid_" + p.name + "_Sell_Power") //
					.lower(0) //
					.upper(Math.min(p.pv.power.prod, GRID_SELL_LIMIT)); //
//					.upper(Math.min(p.pvs.get(0).power.prod + p.pvs.get(1).power.prod, GRID_SELL_LIMIT));
			model.addExpression("Grid_" + p.name + "_BuySellPower_Expr") //
					.set(p.grid.power, ONE) //
					.set(p.grid.buy.power, ONE.negate()) //
					.set(p.grid.sell.power, ONE) //
					.level(0);

			// Conditions to check whether for the present period, the additional
			// ESS constraint (charge prioritization) is imposed or not
			// The conditions that need to be checked here have been explained above
			if (evPotentialEnergy >= EV_MAX_ENERGY * 60) {
				if (essPotentialEnergyIndex >= 0) {
					if (i <= essPotentialEnergyIndex) {
						int essPotentialPower = Math.min(ESS_MAX_CHARGE, p.pv.power.prod - p.hh.power.cons);
						essPotentialEnergy = essPotentialEnergy
								+ essPotentialPower * MINUTES_PER_PERIOD * ESS_CHARGE_EFFICIENCY / 100;
						if (essPotentialEnergy <= ESS_MAX_ENERGY * 60) {
							model.addExpression(p.name + "_ESS_Charge_Constraint_Expr") //
									.set(p.ess.charge.power, ONE) //
									.lower(0.9
											* Math.min(ESS_MAX_CHARGE, Math.max(0, p.pv.power.prod - p.hh.power.cons)));
						}
					}

				} else {
					if (ESS_INITIAL_ENERGY * 60 + (pvSum - hhSum) * MINUTES_PER_PERIOD <= ESS_MAX_ENERGY * 60
							- ESS_MAX_CHARGE * MINUTES_PER_PERIOD * 0.9) {
						model.addExpression(p.name + "_ESS_Charge_Constraint_Expr") //
								.set(p.ess.charge.power, ONE) //
								.lower(0.9 * Math.min(ESS_MAX_CHARGE, Math.max(0, p.pv.power.prod - p.hh.power.cons)));
					}
				}
			} else {
				// Evenly distributed charging
				// In general, this constraint cannot be imposed for all periods, as it might
				// interfere with the constraint for charge prioritization, potentially
				// leading to infeasibility of the modeled program
				if (i >= 1) {
					model.addExpression(p.name + "_Charge_Diff_Max_Expr") //
							.set(periods[i].ess.charge.power, ONE) //
							.set(periods[i - 1].ess.charge.power, ONE.negate()) //
							.lower(-1 * ESS_MAX_CHARGE_DIFFERENCE) //
							.upper(ESS_MAX_CHARGE_DIFFERENCE);
				}
			}

			// Selling PV power and charging the ESS with grid power can be
			// beneficial (whenever sell revenue exceeds buy cost)
			// This constraint allows gridBuy XOR gridSell within a period
			// Alternatively (and for the sake of better runtime), add a fixed and
			// suitable value to p.grid.buy.cost to avoid the mentioned scenario
//			p.grid.isBuy = model.addVariable("Grid_" + p.name + "_isBuy") //
//					.binary();
//			model.addExpression(p.name + "_Grid_Buy_XOR_Grid_Sell_Expr") //
//					.set(p.grid.isBuy, GRID_BUY_LIMIT) //
//					.set(p.grid.buy.power, ONE) //
//					.lower(0) //
//					.upper(GRID_BUY_LIMIT);
//			model.addExpression(p.name +  "_Grid_Buy_XOR_Grid_Sell2_Expr") //
//					.set(p.grid.isBuy, GRID_SELL_LIMIT) //
//					.set(p.grid.sell.power, ONE.negate()) //
//					.lower(0) //
//					.upper(GRID_SELL_LIMIT);

			// Do not allow gridBuy while the EV is charged
			// In general, this should not be taken to be a constraint
			// It is rather a byproduct of the optimization
//			p.grid.isBuy = model.addVariable("Grid_" + p.name + "_isBuy") //
//					.binary();
//			model.addExpression(p.name + "_Grid_Buy_XOR_EV_Charge_Expr") //
//					.set(p.ev.isCharged, ONE) //
//					.set(p.grid.isBuy, ONE) //
//					.lower(1) //
//					.upper(2);
//			model.addExpression(p.name + "_Grid_Buy_XOR_EV_Charge2_Expr") //
//					.set(p.grid.isBuy, GRID_BUY_LIMIT) //
//					.set(p.grid.buy.power, ONE) //
//					.lower(0) //
//					.upper(GRID_BUY_LIMIT);

			// In case of 2 PVs we need to specify the portion of each PV
			// contributing to p.grid.sell.power
//			p.pvs.get(0).power.sell = model.addVariable(p.name + "_PV0_Sell_Power") //
//					.lower(0) //
//					.upper(p.pvs.get(0).power.prod);
//			p.pvs.get(1).power.sell = model.addVariable(p.name + "_PV1_Sell_Power") //
//					.lower(0) //
//					.upper(p.pvs.get(1).power.prod);
//			model.addExpression(p.name + "_PV_Sell_Power_Expr") //
//						.set(p.grid.sell.power, ONE.negate())
//						.set(p.pvs.get(0).power.sell, ONE) //
//						.set(p.pvs.get(1).power.sell, ONE) //
//						.level(0);

			p.grid.buy.cost = GRID_BUY_COST[i];
			p.grid.sell.revenue = GRID_SELL_REVENUE[i];

			// In case of 2 PVs
//			p.grid.buy.cost = GRID_BUY_COST[i];
//			p.grid.sell.revenue[0] = GRID_SELL_REVENUE0[i];
//			p.grid.sell.revenue[1] = GRID_SELL_REVENUE1[i];
		}
	}

	public void prettyPrint() {
		for (int i = 0; i < this.periods.length; i++) {
			Period p = this.periods[i];
			System.out.println(
//					String.format("%2d | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5d  | %s %5d | %s %5d | %s %5.0f | %s %5.0f | %s %5d", i, //
					String.format(
							"%2d | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f| %s %5d | %s %5d",
							i, //
//					String.format("%2d | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5d | %s %5d", i, //
							"Grid", p.grid.power.getValue().doubleValue(), //
							"GridBuy", p.grid.buy.power.getValue().doubleValue(), //
							"GridSell", p.grid.sell.power.getValue().doubleValue(), //
							"ESS", p.ess.power.getValue().doubleValue(), //
							"ESSCharge", p.ess.charge.power.getValue().doubleValue(), //
							"ESSDischarge", p.ess.discharge.power.getValue().doubleValue(), //
							"ESSEnergy", p.ess.energy.getValue().doubleValue() / 60, //
//							"EVChargeMode", p.ev.isCharged.getValue().doubleValue(), //
							"EVPower", p.ev.charge.power.getValue().doubleValue(), //
							"EVEnergy", p.ev.energy.getValue().doubleValue() / 60, "PVPower", p.pv.power.prod, //
//							"PV0Power", p.pvs.get(0).power.prod, //
//							"PV1Power", p.pvs.get(1).power.prod, //
//							"PV0SellPower", p.pvs.get(0).power.sell.getValue().doubleValue(), //
//							"PV1SellPower", p.pvs.get(1).power.sell.getValue().doubleValue(), //
							"HHLoad", p.hh.power.cons//
					));
		}
	}

//	public void prettyPrint() {
//		for (int i = 0; i < this.periods.length; i++) {
//			Period p = this.periods[i];
//			System.out.println(
////					String.format("%2d | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5d | %s %5d", i, //
////					String.format("%2d | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5d | %s %5d | %s %5d | %s %5.0f | %s %5.0f | %s %5d", i, //	
//					String.format("%2d | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5d | %s %5d | %s %5d | %s %5.0f | %s %5.0f | %s %5d", i, //	
//
//							"Grid", p.grid.power.getValue().doubleValue(), //
//							"GridBuy", p.grid.buy.power.getValue().doubleValue(), //
//							"GridSell", p.grid.sell.power.getValue().doubleValue(), //
//							"ESS", p.ess.power.getValue().doubleValue(), //
//							"ESSCharge", p.ess.charge.power.getValue().doubleValue(), //
//							"ESSDischarge", p.ess.discharge.power.getValue().doubleValue(), //
//							"ESSEnergy", p.ess.energy.getValue().doubleValue() / 60, //
////							"EV0ChargeMode", p.evs.get(0).isCharged.getValue().doubleValue(), //
////							"EV0Power", p.evs.get(0).charge.power.getValue().doubleValue(), //
////							"EV0Energy", p.evs.get(0).energy.getValue().doubleValue() / 60, //
////							"EV1ChargeMode", p.evs.get(1).isCharged.getValue().doubleValue(), //
////							"EV1Power", p.evs.get(1).charge.power.getValue().doubleValue(), //
////							"EV1Energy", p.evs.get(1).energy.getValue().doubleValue() / 60, //
//							"PVPower", p.pv.power.prod, //
//							"PV0Power", p.pvs.get(0).power.prod, //
//							"PV1Power", p.pvs.get(1).power.prod, //
//							"PV0SellPower", p.pvs.get(0).power.sell.getValue().doubleValue(), //
//							"PV1SellPower", p.pvs.get(1).power.sell.getValue().doubleValue(), //
//							"HHLoad", p.hh.power.cons//
//					));
//		}
//	}

	public void plot(EnergyModel schedule) {
		Data gridBuy = Plot.data();
		Data gridSell = Plot.data();
		Data essCharge = Plot.data();
		Data essDischarge = Plot.data();
		Data pvProduction = Plot.data();
		Data hhLoad = Plot.data();
		Data evPower = Plot.data();
//		Data ev0Power = Plot.data();
//		Data ev1Power = Plot.data();
		for (int i = 0; i < this.periods.length; i++) {
			Period p = this.periods[i];
			gridBuy.xy(i, p.grid.buy.power.getValue().doubleValue());
			gridSell.xy(i, p.grid.sell.power.getValue().doubleValue());
			essCharge.xy(i, p.ess.charge.power.getValue().doubleValue());
			essDischarge.xy(i, p.ess.discharge.power.getValue().doubleValue());
			pvProduction.xy(i, p.pv.power.prod);
			hhLoad.xy(i, p.hh.power.cons);
			evPower.xy(i, p.ev.charge.power.getValue().doubleValue());
//			ev0Power.xy(i, p.evs.get(0).charge.power.getValue().doubleValue());
//			ev1Power.xy(i, p.evs.get(1).charge.power.getValue().doubleValue());
		}

		Plot plot = Plot.plot(//
				Plot.plotOpts() //
						.title("Energy Model") //
						.legend(Plot.LegendFormat.BOTTOM)) //
				.xAxis("x", Plot.axisOpts() //
						.format(AxisFormat.NUMBER_INT) //
						.range(0, this.periods.length)) //
				.yAxis("y", Plot.axisOpts() //
						.format(AxisFormat.NUMBER_INT)) //
				.series("Grid Buy", gridBuy, Plot.seriesOpts() //
						.color(Color.BLACK))
				.series("Grid Sell", gridSell, Plot.seriesOpts() //
						.color(Color.BLUE))
				.series("ESS Charge", essCharge, Plot.seriesOpts() //
						.color(Color.GREEN))
				.series("ESS Discharge", essDischarge, Plot.seriesOpts() //
						.color(Color.RED))
				.series("PV", pvProduction, Plot.seriesOpts() //
						.color(Color.CYAN))
				.series("HH", hhLoad, Plot.seriesOpts() //
						.color(Color.ORANGE))
				.series("EV", evPower, Plot.seriesOpts() //
						.color(Color.LIGHT_GRAY))
//				.series("EV0", ev0Power, Plot.seriesOpts() //
//						.color(Color.LIGHT_GRAY))
//				.series("EV1", ev1Power, Plot.seriesOpts() //
//						.color(Color.LIGHT_GRAY))

		;

		try {
			plot.save("plot", "png");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
