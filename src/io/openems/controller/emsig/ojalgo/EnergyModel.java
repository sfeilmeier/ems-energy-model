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
import static io.openems.controller.emsig.ojalgo.Constants.NO_OF_PERIODS;

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

			/*
			 * Energy Storage
			 */
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

			// sum energy
			p.ess.energy = model.intVar("ESS_" + p.name + "_Energy", ESS_MIN_ENERGY * 60 /* [Wm] */,
					ESS_MAX_ENERGY * 60 /* [Wm] */);

			if (i == 0) {
				model.arithm(p.ess.energy, "+", model.intScaleView(p.ess.power, MINUTES_PER_PERIOD), "=",
						ESS_INITIAL_ENERGY * 60);
			} else {
				model.arithm(p.ess.energy, "-", model.intScaleView(p.ess.power, MINUTES_PER_PERIOD), "=",
						periods[i - 1].ess.energy);
			}

			/*
			 * Grid
			 */
			p.grid.power = model.intVar("Grid_" + p.name + "_Power", GRID_SELL_LIMIT * -1, GRID_BUY_LIMIT); //

			p.grid.buy.power = model.intVar("Grid_" + p.name + "_Buy_Power", 0, GRID_BUY_LIMIT);
			p.grid.sell.power = model.intVar("Grid_" + p.name + "_Sell_Power", 0, GRID_SELL_LIMIT);
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

			p.grid.buy.cost = GRID_BUY_COST[i];
			p.grid.sell.revenue = GRID_SELL_REVENUE[i];

			// Power-System formula
			model.arithm(p.grid.power, "+", p.ess.power, "=", 0);
		}
	}

	public void prettyPrint(Solution s) {
		for (int i = 0; i < this.periods.length; i++) {
			Period p = this.periods[i];
			System.out.println(String.format("%2d | %s %5d | %s %5d | %s %5d | %s %5d | %s %5d | %s %5d | %s %5d", i, //
					"Grid", s.getIntVal(p.grid.power), //
					"GridBuy", s.getIntVal(p.grid.buy.power), //
					"GridSell", s.getIntVal(p.grid.sell.power), //
					"ESS", s.getIntVal(p.ess.power), //
					"ESSCharge", s.getIntVal(p.ess.charge.power), //
					"ESSDischarge", s.getIntVal(p.ess.discharge.power), //
					"ESSEnergy", s.getIntVal(p.ess.energy) / 60 //
			));
		}
	}

	public void plot(Solution s) {
		Data gridBuy = Plot.data();
		Data gridSell = Plot.data();
		Data essCharge = Plot.data();
		Data essDischarge = Plot.data();
		for (int i = 0; i < this.periods.length; i++) {
			Period p = this.periods[i];
			gridBuy.xy(i, s.getIntVal(p.grid.buy.power));
			gridSell.xy(i, s.getIntVal(p.grid.sell.power));
			essCharge.xy(i, s.getIntVal(p.ess.charge.power));
			essDischarge.xy(i, s.getIntVal(p.ess.discharge.power));
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

		;

		try {
			plot.save("plot", "png");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
