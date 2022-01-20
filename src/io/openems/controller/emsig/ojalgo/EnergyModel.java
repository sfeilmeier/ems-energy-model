package io.openems.controller.emsig.ojalgo;

import static io.openems.controller.emsig.ojalgo.Constants.ESS_INITIAL_ENERGY;
import static io.openems.controller.emsig.ojalgo.Constants.ESS_MAX_CHARGE;
import static io.openems.controller.emsig.ojalgo.Constants.ESS_MAX_DISCHARGE;
import static io.openems.controller.emsig.ojalgo.Constants.ESS_MAX_ENERGY;
import static io.openems.controller.emsig.ojalgo.Constants.ESS_MIN_ENERGY;
import static io.openems.controller.emsig.ojalgo.Constants.GRID_BUY_COST;
import static io.openems.controller.emsig.ojalgo.Constants.GRID_BUY_LIMIT;
import static io.openems.controller.emsig.ojalgo.Constants.GRID_SELL_LIMIT;
import static io.openems.controller.emsig.ojalgo.Constants.GRID_SELL_REVENUE;
import static io.openems.controller.emsig.ojalgo.Constants.MINUTES_PER_PERIOD;
import static io.openems.controller.emsig.ojalgo.Constants.PV_POWER;
import static io.openems.controller.emsig.ojalgo.Constants.HH_LOAD;
import static io.openems.controller.emsig.ojalgo.Constants.ESS_CHARGE_EFFICIENCY;
import static io.openems.controller.emsig.ojalgo.Constants.ESS_DISCHARGE_EFFICIENCY;
import static io.openems.controller.emsig.ojalgo.Constants.NO_OF_PERIODS;
//import static io.openems.controller.emsig.ojalgo.Constants.EV_INITIAL_ENERGY;
//import static io.openems.controller.emsig.ojalgo.Constants.EV_MAX_ENERGY;
//import static io.openems.controller.emsig.ojalgo.Constants.EV_REQUIRED_ENERGY;
//import static io.openems.controller.emsig.ojalgo.Constants.EV_MIN_CHARGE;
//import static io.openems.controller.emsig.ojalgo.Constants.EV_MAX_CHARGE;
//import static io.openems.controller.emsig.ojalgo.Constants.EV_CHARGE_EFFICIENCY;

import java.awt.Color;
import java.io.IOException;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solution;

import com.github.plot.Plot;
import com.github.plot.Plot.AxisFormat;
import com.github.plot.Plot.Data;

public class EnergyModel {

	public final Model model;
	public final Period[] periods;

	public EnergyModel() {
		model = new Model();

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
			// 0 = ess.power - ess.discharge.power + ess.charge.power
			// TODO post ifThenElse? 
			p.ess.power = model.intVar("ESS_" + p.name + "_Power", ESS_MAX_CHARGE * -1, ESS_MAX_DISCHARGE); //
			p.ess.discharge.power = model.intVar("ESS_" + p.name + "_Discharge_Power", 0, ESS_MAX_DISCHARGE);
			p.ess.charge.power = model.intVar("ESS_" + p.name + "_Charge_Power", 0, ESS_MAX_CHARGE);
			p.ess.isCharge = model.boolVar();
			model.ifThenElse(p.ess.isCharge, //
					model.arithm(p.ess.charge.power, "=", model.intScaleView(p.ess.power, -1)), //
					model.arithm(p.ess.charge.power, "=", 0)); //
			model.ifThenElse(p.ess.isCharge, //
					model.arithm(p.ess.discharge.power, "=", 0), //
					model.arithm(p.ess.discharge.power, "=", p.ess.power));
			
			// allow charge and discharge within the same period
//			 model.arithm(p.ess.discharge.power, "-", p.ess.charge.power, "=", p.ess.power).post();
			
			// cf ojAlgo 
//			p.ess.power = model.addVariable("ESS_" + p.name + "_Power") //
//					.lower(ESS_MAX_CHARGE * -1) //
//					.upper(ESS_MAX_DISCHARGE);
//			p.ess.discharge.power = model.addVariable("ESS_" + p.name + "_Discharge_Power") //
//					.lower(0) //
//					.upper(ESS_MAX_DISCHARGE);
//			p.ess.charge.power = model.addVariable("ESS_" + p.name + "_Charge_Power") //
//					.lower(0) //
//					.upper(ESS_MAX_CHARGE);
//			model.addExpression("ESS_" + p.name + "_ChargeDischargePower_Expr") //
//					.set(p.ess.power, ONE) //
//					.set(p.ess.discharge.power, ONE.negate()) //
//					.set(p.ess.charge.power, ONE) //
//					.level(0);

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
			// TODO divide ess.power in ess.charge.power and ess.discharge.power to 
			// take the respective efficiency into account
			
            p.ess.energy = model.intVar("ESS_" + p.name + "_Energy", ESS_MIN_ENERGY * 60 /* [Wmin] */,
                    ESS_MAX_ENERGY * 60 /* [Wmin] */);
            
            if (i == 0) {
                model.arithm(p.ess.energy, "+", model.intScaleView(p.ess.power, MINUTES_PER_PERIOD), "=",
                        ESS_INITIAL_ENERGY * 60).post();
            
        	} else {
                model.arithm(p.ess.energy, "+", model.intScaleView(p.ess.power, MINUTES_PER_PERIOD), "=",
                        periods[i - 1].ess.energy).post();
                }
            
            
//			p.ess.energy = model.intVar("ESS_" + p.name + "_Energy", ESS_MIN_ENERGY * 60 /* [Wmin] */,
//					ESS_MAX_ENERGY * 60 /* [Wmin] */);
//			if (i == 0) {
//				model.ifThenElse(p.ess.isCharge,
//						model.arithm(p.ess.energy, "+", model.intScaleView(p.ess.power, MINUTES_PER_PERIOD*ESS_CHARGE_EFFICIENCY/100), "=",
//								ESS_INITIAL_ENERGY * 60),
//						model.arithm(p.ess.energy, "+", model.intScaleView(p.ess.power, MINUTES_PER_PERIOD*ESS_DISCHARGE_EFFICIENCY/100), "=",
//								ESS_INITIAL_ENERGY * 60));
//			} else {
//				model.ifThenElse(p.ess.isCharge,
//						model.arithm(p.ess.energy, "+", model.intScaleView(p.ess.power, MINUTES_PER_PERIOD*ESS_CHARGE_EFFICIENCY/100), "=",
//								periods[i - 1].ess.energy),
//						model.arithm(p.ess.energy, "+", model.intScaleView(p.ess.power, MINUTES_PER_PERIOD*ESS_DISCHARGE_EFFICIENCY/100), "=",
//								periods[i - 1].ess.energy));
//
//			}
			

			
			
			// cf ojAlgo
//			p.ess.energy = model.addVariable("ESS_" + p.name + "_Energy") //
//					.lower(ESS_MIN_ENERGY * 60 /* [Wmin] */) //
//					.upper(ESS_MAX_ENERGY * 60 /* [Wmin] */);
//			// periods[0].ess.energy =  ESS_INITIAL_ENERGY *60 - period[0].ess.power*MINUTES_PER_PERIOD*ESS_EFFICIENCY
//			if (i == 0) {
//				model.addExpression("ESS_" + p.name + "_Energy_Expr_1st") // 
//						.set(p.ess.energy, ONE) //
////						.set(p.ess.power, MINUTES_PER_PERIOD) //
//						.set(p.ess.charge.power, -1* MINUTES_PER_PERIOD*ESS_CHARGE_EFFICIENCY /100) //
//						.set(p.ess.discharge.power, MINUTES_PER_PERIOD*ESS_DISCHARGE_EFFICIENCY/100) //
//						.level(ESS_INITIAL_ENERGY*60); // 
//			} else {
//				model.addExpression("ESS_" + p.name + "_Energy_Expr") // 
//						.set(periods[i - 1].ess.energy, ONE) //
//						.set(p.ess.discharge.power, MINUTES_PER_PERIOD * -1 * ESS_DISCHARGE_EFFICIENCY /100) //
//						.set(p.ess.charge.power, MINUTES_PER_PERIOD*ESS_CHARGE_EFFICIENCY /100) //
//						.set(p.ess.energy, ONE.negate()) //
//						.level(0); // ESS_EFFICIENCY
//			}
			
			/*
			 * EV 
			 */
			// EV power
			// For now, we impose no assumptions
//			p.ev.charge.power = model.addVariable("EV_" + p.name + "_Charge_Power") //
//					.lower(0) //
//					.upper(EV_MAX_CHARGE);
//			
			// EV mode
			// either allow no charge power (if charge mode = 0) or allow
			// a charge power in [EV_MIN_CHARGE, EV_MAX_CHARGE] 
			// TODO will probably fail, since the mode takes values between 0 and 1 as well
//			p.ev.charge.mode = model.addVariable("EV_" + p.name + "_Charge_Mode") //
//					.lower(0) //
//					.upper(1);
//			model.addExpression("EV_" + p.name + "_Charge_Mode_Expr") //
//				.set(p.ev.charge.mode, EV_MAX_CHARGE) //
//				.set(p.ev.charge.power, ONE) //
//				.lower(EV_MIN_CHARGE) //
//				.upper(EV_MAX_CHARGE);
			
					
			// EV energy
			// p.ev.energy = p.ev.charge.power*MINUTES_PER_PERIODS
			// In particular, assume a minimum charge power of 100
//			p.ev.energy = model.addVariable("EV_" + p.name + "Energy") //
//					.lower(0) //
//					.upper(EV_MAX_ENERGY * 60); // [Wmin]
//			model.addExpression(p.name + "Energy") //
//				.set(p.ev.energy, ONE) //
//				.set(p.ev.charge.power, -1* MINUTES_PER_PERIOD*EV_CHARGE_EFFICIENCY/100) //
//				.level(0);

			/*
			 * Grid
			 */
			// p.hh.power.cons - p.pv.power.prod = p.ess.power + p.grid.power - p.ev.power
			// 0 = p.grid.power - grid.buy.power + p.grid.sell.power 
			p.grid.power = model.intVar("Grid_" + p.name + "_Power", GRID_SELL_LIMIT * -1, GRID_BUY_LIMIT); //

			p.grid.buy.power = model.intVar("Grid_" + p.name + "_Buy_Power", 0, GRID_BUY_LIMIT);
			p.grid.sell.power = model.intVar("Grid_" + p.name + "_Sell_Power", 0, p.pv.power.prod); //change upper limit
			p.grid.isBuy = model.boolVar();
			model.ifThenElse(p.grid.isBuy, //
					model.arithm(p.grid.buy.power, "=", p.grid.power), //
					model.arithm(p.grid.buy.power, "=", 0)); //
			model.ifThenElse(p.grid.isBuy, //
					model.arithm(p.grid.sell.power, "=", 0), //
					model.arithm(p.grid.sell.power, "=", model.intScaleView(p.grid.power, -1)));		

			// TODO Grid-Sell can never be more than Production. This simple model assumes
			// no production, so Grid-Sell must be zero - at least outside of HLZF period.
//			p.grid.sell.power.upper(0);

			// Power-System formula
			//model.arithm(p.grid.power, "+", p.ess.power, "=", 0);
			 model.arithm(p.grid.power, "+", p.ess.power, "=", p.hh.power.cons - p.pv.power.prod).post();
			
			// cf. ojAlgo
//			p.grid.power = model.addVariable("Grid_" + p.name + "_Power"); //
//			model.addExpression(p.name + "_Power_Balance") //
//			.set(p.ess.power, ONE) //
//			.set(p.grid.power, ONE) //
//		//	.set(p.ev.charge.power, ONE.negate())
//			.level(p.hh.power.cons - p.pv.power.prod);
//			p.grid.buy.power = model.addVariable("Grid_" + p.name + "_Buy_Power") //
//					.lower(0) //
//					.upper(GRID_BUY_LIMIT);
//			p.grid.sell.power = model.addVariable("Grid_" + p.name + "_Sell_Power") //
//					.lower(0) //
//					.upper(p.pv.power.prod); // originally .upper(GRID_SELL_LIMIT);
//			model.addExpression("Grid_" + p.name + "_BuySellPower_Expr") //
//					.set(p.grid.power, ONE) //
//					.set(p.grid.buy.power, ONE.negate()) //
//					.set(p.grid.sell.power, ONE) //
//					.level(0);
			
			p.grid.buy.cost = GRID_BUY_COST[i];
			p.grid.sell.revenue = GRID_SELL_REVENUE[i];
		}
	}

	public void prettyPrint(Solution s) {
		for (int i = 0; i < this.periods.length; i++) {
			Period p = this.periods[i];
			System.out.println(String.format("%2d | %s %5d | %s %5d | %s %5d | %s %5d | %s %5d | %s %5d | %s %5d | %s %5d | %s %5d", i, //
					"Grid", s.getIntVal(p.grid.power), //
					"GridBuy", s.getIntVal(p.grid.buy.power), //
					"GridSell", s.getIntVal(p.grid.sell.power), //
					"ESS", s.getIntVal(p.ess.power), //
					"ESSCharge", s.getIntVal(p.ess.charge.power), //
					"ESSDischarge", s.getIntVal(p.ess.discharge.power), //
					// "ESSIsCharge", s.getBoolVar(p.ess.isCharge), //
					"ESSEnergy", s.getIntVal(p.ess.energy) / 60, //
					"PVPower", p.pv.power.prod, //
					"HHLoad", p.hh.power.cons // 
					
			));
		}
	}

	public void plot(Solution s) {
		Data gridBuy = Plot.data();
		Data gridSell = Plot.data();
		Data essCharge = Plot.data();
		Data essDischarge = Plot.data();
		Data pvProduction = Plot.data();
		Data hhLoad = Plot.data();
		for (int i = 0; i < this.periods.length; i++) {
			Period p = this.periods[i];
			gridBuy.xy(i, s.getIntVal(p.grid.buy.power));
			gridSell.xy(i, s.getIntVal(p.grid.sell.power));
			essCharge.xy(i, s.getIntVal(p.ess.charge.power));
			essDischarge.xy(i, s.getIntVal(p.ess.discharge.power));
			pvProduction.xy(i,  p.pv.power.prod);
			hhLoad.xy(i, p.hh.power.cons);

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
