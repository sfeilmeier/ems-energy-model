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
import static io.openems.controller.emsig.ojalgo.Constants.MINUTES_PER_PERIOD;
import static io.openems.controller.emsig.ojalgo.Constants.PV_POWER;
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

import org.ojalgo.optimisation.ExpressionsBasedModel;

import com.github.plot.Plot;
import com.github.plot.Plot.AxisFormat;
import com.github.plot.Plot.Data;

public class EnergyModel {

	public final ExpressionsBasedModel model;
	public final Period[] periods;

	public EnergyModel() {
		model = new ExpressionsBasedModel();

		// Initialize Periods
		this.periods = new Period[NO_OF_PERIODS];
		for (int i = 0; i < periods.length; i++) {
			Period p = Period.from(i, periods.length, MINUTES_PER_PERIOD);
			periods[i] = p;
			
			// specify the PV power and the HH load for each period
			p.pv.power.prod = PV_POWER[i];
			p.hh.power.cons = HH_LOAD[i];

			/*
			 * Energy Storage
			 */
			// upper and lower bounds for charge and discharge
			// Upper bound for charge is given by the minimum
			// of ESS_MAX_CHARGE and p.power.prod for each period p.
			// This way, the ESS is not charged with power bought 
			// from the grid (at least in the evening and in the morning).
			// 0 = ess.power - ess.discharge.power + ess.charge.power
			
			p.ess.power = model.addVariable("ESS_" + p.name + "_Power") //
					.lower(ESS_MAX_CHARGE * -1) //
					.upper(ESS_MAX_DISCHARGE);
			p.ess.discharge.power = model.addVariable("ESS_" + p.name + "_Discharge_Power") //
					.lower(0) //
					.upper(ESS_MAX_DISCHARGE);
			p.ess.charge.power = model.addVariable("ESS_" + p.name + "_Charge_Power") //
					.lower(0) //
					.upper(Math.min(ESS_MAX_CHARGE, p.pv.power.prod));
			model.addExpression("ESS_" + p.name + "_ChargeDischargePower_Expr") //
					.set(p.ess.power, ONE) //
					.set(p.ess.discharge.power, ONE.negate()) //
					.set(p.ess.charge.power, ONE) //
					.level(0);
			
			// within a period, only charging XOR discharging is allowed
//			p.ess.charge.mode = model.addVariable("ESS_" + p.name + "_Charge_Mode") //
//					.lower(0) //
//					.upper(1);
//			p.ess.discharge.mode = model.addVariable("ESS_" + p.name + "_Discharge_Mode") //
//					.lower(0) //
//					.upper(1);
//			model.addExpression("ESS_" + p.name + "_Charge_Mode_Expr") //
//			.set(p.ess.charge.mode, ONE) //
//			.set(p.ess.discharge.mode, ONE) //
//			.level(1);
//			model.addExpression("ESS_" + p.name + "Charge_Constraint_Expr") //
//			.set(p.ess.charge.mode, p.ess.discharge.power, 1.0) //
//			.level(0);
//	em.model.addExpression("ESS_" + p.name + "Discharge_Constraint_Expr") //
//			.set(p.ess.discharge.mode, p.ess.charge.power, 1.0) //
//			.level(0);	
			

			// sum energy
			// take the charge and discharge efficiency of the ESS into account
			// by doing so, it is less likely that the ESS both charges and discharges
			// within a period, but it is not impossible
			p.ess.energy = model.addVariable("ESS_" + p.name + "_Energy") //
					.lower(ESS_MIN_ENERGY * 60 /* [Wmin] */) //
					.upper(ESS_MAX_ENERGY * 60 /* [Wmin] */);
			// periods[0].ess.energy =  ESS_INITIAL_ENERGY *60 - period[0].ess.power*MINUTES_PER_PERIOD*ESS_EFFICIENCY
			if (i == 0) {
				model.addExpression("ESS_" + p.name + "_Energy_Expr_1st") // 
						.set(p.ess.energy, ONE) //
//						.set(p.ess.power, MINUTES_PER_PERIOD) //
						.set(p.ess.charge.power, -1* MINUTES_PER_PERIOD*ESS_CHARGE_EFFICIENCY /100) //
						.set(p.ess.discharge.power, MINUTES_PER_PERIOD*ESS_DISCHARGE_EFFICIENCY/100) //
						.level(ESS_INITIAL_ENERGY*60); // 
			} else {
				model.addExpression("ESS_" + p.name + "_Energy_Expr") // 
						.set(periods[i - 1].ess.energy, ONE) //
						.set(p.ess.discharge.power, MINUTES_PER_PERIOD * -1 * ESS_DISCHARGE_EFFICIENCY /100) //
						.set(p.ess.charge.power, MINUTES_PER_PERIOD*ESS_CHARGE_EFFICIENCY /100) //
						.set(p.ess.energy, ONE.negate()) //
						.level(0); 
			}
			//  p.ess.energy = periods[i-1].ess.energy - p.ess.power*MINUTES_PER_PERIOD - ESS_EFFICIENCY*60		
			
			// Evenly distributed charging
//			if (i == 0) {
//				p.ess.charge.power.upper(ESS_MAX_CHARGE_DIFFERENCE);
//		} else {
//			model.addExpression(p.name + "_Charge_Diff_Max") //
//				.set(periods[i].ess.charge.power , ONE) //
//				.set(periods[i-1].ess.charge.power, ONE.negate()) //
//				.lower(-1 * ESS_MAX_CHARGE_DIFFERENCE) //
//				.upper(ESS_MAX_CHARGE_DIFFERENCE);
//		}

			/*
			 * EV 
			 */
			// EV power and EV mode
			// If the EV is not available, isCharged = 1,  chargePower = 0
			// Either allow no charge power (if isCharged = 1) or allow
			// a charge power in [EV_MIN_CHARGE, EV_MAX_CHARGE] 
			p.ev.charge.power = model.addVariable("EV_" + p.name + "_Charge_Power") //
					.lower(0) //
					.upper(EV_MAX_CHARGE);

//			p.ev.isCharged = model.addVariable("EV_" + p.name + "_Charge_Mode") //
//					.lower(0) //
//					.upper(1) //
//					.integer(true); //
			
			p.ev.isCharged = model.addVariable("EV_" + p.name + "_Charge_Mode") //
					.binary();
			
//			 if (EV_AVAIL[i] == 0) {
//				 p.ev.charge.power.level(0);
//				 p.ev.isCharged.level(1);
//			 } else {
//				 	model.addExpression("EV_" + p.name + "_Charge_Power_Expr") //
//				 		.set(p.ev.isCharged, EV_MAX_CHARGE) //
//				 		.set(p.ev.charge.power, ONE) //
//				 		.lower(EV_MIN_CHARGE) //
//				 		.upper(EV_MAX_CHARGE);
//			 }
//			 
			 // Instead of if/else statement:
			 	model.addExpression("EV_" + p.name + "_Charge_Decision") //
		 		.set(p.ev.isCharged, ONE) //
		 		.lower(1 - EV_AVAIL[i]);
			 	
			 	model.addExpression("EV_" + p.name + "_Charge_Power_Expr") //
		 		.set(p.ev.isCharged, EV_MAX_CHARGE) //
		 		.set(p.ev.charge.power, ONE) //
		 		.lower(EV_MIN_CHARGE) //
		 		.upper(EV_MAX_CHARGE);
		
 					
			// EV energy
			// p.ev.energy = p.ev.charge.power*MINUTES_PER_PERIODS
			// In particular, assume a minimum charge power of 100
			p.ev.energy = model.addVariable("EV_" + p.name + "Energy") //
					.lower(0) //
					.upper(EV_MAX_ENERGY * 60); // [Wmin]
			if (i == 0) {
			model.addExpression(p.name + "Energy") //
				.set(p.ev.energy, ONE) //
				.set(p.ev.charge.power, -1* MINUTES_PER_PERIOD*EV_CHARGE_EFFICIENCY/100) //
				.level(EV_INITIAL_ENERGY *60);
			} else {
				model.addExpression(p.name + "Energy") //
				.set(p.ev.energy, ONE.negate()) //
				.set(p.ev.charge.power, MINUTES_PER_PERIOD*EV_CHARGE_EFFICIENCY/100) //
				.set(periods[i-1].ev.energy, ONE) //
				.level(0);
			}
			
			/*
			 * Grid
			 */
			// p.hh.power.cons - p.pv.power.prod = p.ess.power + p.grid.power - p.ev.power
			// 0 = p.grid.power - grid.buy.power + p.grid.sell.power 
			p.grid.power = model.addVariable("Grid_" + p.name + "_Power"); //
			model.addExpression(p.name + "_Power_Balance") //
			.set(p.ess.power, ONE) //
			.set(p.grid.power, ONE) //
			.set(p.ev.charge.power, ONE.negate())
			.level(p.hh.power.cons - p.pv.power.prod);
			p.grid.buy.power = model.addVariable("Grid_" + p.name + "_Buy_Power") //
					.lower(0) //
					.upper(GRID_BUY_LIMIT);
			p.grid.sell.power = model.addVariable("Grid_" + p.name + "_Sell_Power") //
					.lower(0) //
					.upper(p.pv.power.prod); // originally .upper(GRID_SELL_LIMIT);
			model.addExpression("Grid_" + p.name + "_BuySellPower_Expr") //
					.set(p.grid.power, ONE) //
					.set(p.grid.buy.power, ONE.negate()) //
					.set(p.grid.sell.power, ONE) //
					.level(0);
			
			// We do not allow gridBuy while the EV is charged
			p.grid.isBuy = model.addVariable("Grid_" + p.name + "isBuy") //
					.binary();
			
			model.addExpression(p.name + "_Grid_Buy_XOR_EV_Charge_Expr") //
				.set(p.ev.isCharged, ONE) //
				.set(p.grid.isBuy, ONE) //
				.lower(1);
			
			model.addExpression(p.name + "_Grid_Buy_XOR_EV_Charge2_Expr") //
				.set(p.grid.isBuy, GRID_BUY_LIMIT) //
				.set(p.grid.buy.power, ONE) //
				.lower(0) //
				.upper(GRID_BUY_LIMIT);
		
			p.grid.buy.cost = GRID_BUY_COST[i];
			p.grid.sell.revenue = GRID_SELL_REVENUE[i];
			
	
		}
	}
	
	public void prettyPrint() {
		for (int i = 0; i < this.periods.length; i++) {
			Period p = this.periods[i];
			System.out.println(
					String.format("%2d | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f  | %s %5d | %s %5d", i, //
							"Grid", p.grid.power.getValue().doubleValue(), //
							"GridBuy", p.grid.buy.power.getValue().doubleValue(), //
							"GridSell", p.grid.sell.power.getValue().doubleValue(), //
							"ESS", p.ess.power.getValue().doubleValue(), //
							"ESSCharge", p.ess.charge.power.getValue().doubleValue(), //
							"ESSDischarge", p.ess.discharge.power.getValue().doubleValue(), //
							"ESSEnergy", p.ess.energy.getValue().doubleValue() / 60, //
							"EVChargeMode", p.ev.isCharged.getValue().doubleValue(), //
							"EVPower", p.ev.charge.power.getValue().doubleValue(), //
							"EVEnergy", p.ev.energy.getValue().doubleValue() / 60,
							"PVPower", p.pv.power.prod, //
							"HHLoad", p.hh.power.cons//
					));
		}
	}

	public void plot(EnergyModel schedule) {
		Data gridBuy = Plot.data();
		Data gridSell = Plot.data();
		Data essCharge = Plot.data();
		Data essDischarge = Plot.data();
		Data pvProduction = Plot.data();
		Data hhLoad = Plot.data();
		for (int i = 0; i < this.periods.length; i++) {
			Period p = this.periods[i];
			gridBuy.xy(i, p.grid.buy.power.getValue().doubleValue());
			gridSell.xy(i, p.grid.sell.power.getValue().doubleValue());
			essCharge.xy(i, p.ess.charge.power.getValue().doubleValue());
			essDischarge.xy(i, p.ess.discharge.power.getValue().doubleValue());
			pvProduction.xy(i, p.pv.power.prod);
			hhLoad.xy(i,  p.hh.power.cons);
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
				.series("HH",  hhLoad, Plot.seriesOpts() //
						.color(Color.ORANGE))
						

		;

		try {
			plot.save("plot", "png");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
