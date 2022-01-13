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
import static io.openems.controller.emsig.ojalgo.Constants.NO_OF_PERIODS;

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
			// 0 = ess.power - ess.discharge.power + ess.charge.power
			p.ess.power = model.addVariable("ESS_" + p.name + "_Power") //
					.lower(ESS_MAX_CHARGE * -1) //
					.upper(ESS_MAX_DISCHARGE);
			p.ess.discharge.power = model.addVariable("ESS_" + p.name + "_Discharge_Power") //
					.lower(0) //
					.upper(ESS_MAX_DISCHARGE);
			p.ess.charge.power = model.addVariable("ESS_" + p.name + "_Charge_Power") //
					.lower(0) //
					.upper(ESS_MAX_CHARGE);
			model.addExpression("ESS_" + p.name + "_ChargeDischargePower_Expr") //
					.set(p.ess.power, ONE) //
					.set(p.ess.discharge.power, ONE.negate()) //
					.set(p.ess.charge.power, ONE) //
					.level(0);

			// sum energy
			p.ess.energy = model.addVariable("ESS_" + p.name + "_Energy") //
					.lower(ESS_MIN_ENERGY * 60 /* [Wmin] */) //
					.upper(ESS_MAX_ENERGY * 60 /* [Wmin] */);
			// ESS_INITIAL_ENERGY *60 = periods[0].ess.energy + period[0].ess.power*MINUTES_PER_PERIOD
			if (i == 0) {
				model.addExpression("ESS_" + p.name + "_Energy_Expr_1st") //
						.set(p.ess.energy, ONE) //
						.set(p.ess.power, MINUTES_PER_PERIOD) //
						.level(ESS_INITIAL_ENERGY * 60);
			} else {
				model.addExpression("ESS_" + p.name + "_Energy_Expr") //
						.set(periods[i - 1].ess.energy, ONE) //
						.set(p.ess.power, MINUTES_PER_PERIOD * -1) //
						.set(p.ess.energy, ONE.negate()) //
						.level(0);
			}
			//  p.ess.energy = periods[i-1].ess.energy - p.ess.power*MINUTES_PER_PERIOD
			
			
			/*
			 * Grid
			 */
			// 0 = p.grid.power + p.ess.power 
			// 0 = p.grid.power - grid.buy.power + p.grid.sell.power 
			p.grid.power = model.addVariable("Grid_" + p.name + "_Power"); //
//			model.addExpression("Grid_" + p.name + "_Power_Expr") // TODO  why?
//				.set(p.grid.power, ONE) //
//				.set(p.ess.power, ONE) // add PV power and HH load?
//				.level(0);
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
		
			p.grid.buy.cost = GRID_BUY_COST[i];
			p.grid.sell.revenue = GRID_SELL_REVENUE[i];
			
			// TODO Grid-Sell can never be more than Production. This simple model assumes
			// no production, so Grid-Sell must be zero - at least outside of potential HLZF periods.
			// p.grid.sell.power.upper(0);
			
			// change the upper limit concerning grid sell
			// p.grid.sell.power.upper(p.pv.power.prod);

			
	
		}
	}

//	public void prettyPrint() {
//		for (int i = 0; i < this.periods.length; i++) {
//			Period p = this.periods[i];
//			System.out.println(
//					String.format("%2d | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f", i, //
//							"Grid", p.grid.power.getValue().doubleValue(), //
//							"GridBuy", p.grid.buy.power.getValue().doubleValue(), //
//							"GridSell", p.grid.sell.power.getValue().doubleValue(), //
//							"ESS", p.ess.power.getValue().doubleValue(), //
//							"ESSCharge", p.ess.charge.power.getValue().doubleValue(), //
//							"ESSDischarge", p.ess.discharge.power.getValue().doubleValue(), //
//							"ESSEnergy", p.ess.energy.getValue().doubleValue() / 60 //
//					));
//		}
//	}
	
	public void prettyPrint() {
		for (int i = 0; i < this.periods.length; i++) {
			Period p = this.periods[i];
			System.out.println(
					String.format("%2d | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5.0f | %s %5d | %s %5d", i, //
							"Grid", p.grid.power.getValue().doubleValue(), //
							"GridBuy", p.grid.buy.power.getValue().doubleValue(), //
							"GridSell", p.grid.sell.power.getValue().doubleValue(), //
							"ESS", p.ess.power.getValue().doubleValue(), //
							"ESSCharge", p.ess.charge.power.getValue().doubleValue(), //
							"ESSDischarge", p.ess.discharge.power.getValue().doubleValue(), //
							"ESSEnergy", p.ess.energy.getValue().doubleValue() / 60, //
							"PVPower", p.pv.power.prod, //
							"HHLoad", p.hh.power.cons //
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
