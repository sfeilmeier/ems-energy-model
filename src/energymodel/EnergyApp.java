package energymodel;

import static io.openems.controller.emsig.ojalgo.Constants.ESS_MAX_ENERGY;
import static io.openems.controller.emsig.ojalgo.Constants.GRID_SELL_LIMIT;

import java.time.Duration;
import java.time.Instant;

import org.chocosolver.solver.Solution;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.variables.IntVar;

import io.openems.controller.emsig.ojalgo.EnergyModel;
import io.openems.controller.emsig.ojalgo.Period;

public class EnergyApp {

	public static void main(String[] args) {
		Instant now = Instant.now();

		// At least once very period, i.e. 15 minutes
//		Flexibility flexibility = calculateFlexibility();
//		flexibility.prettyPrint();

		// Create the actual optimal Schedule
		EnergyModel em = new EnergyModel();
		var solution = calculateSchedule(em);
//		schedule = new Schedule(LocalDateTime.now(this.clockProvider.getClock()), schedule);

		System.out.println("Time: " + Duration.between(now, Instant.now()).toMillis() + "ms");

		em.prettyPrint(solution);
		em.plot(solution);
	}

//	private static class Flexibility {
//		protected final int[] maxGridBuy = new int[NO_OF_PERIODS];
//		protected final int[] maxGridSell = new int[NO_OF_PERIODS];
//
//		public void prettyPrint() {
//			for (int i = 0; i < NO_OF_PERIODS; i++) {
//				System.out.println(String.format("%2d | %s %5d | %s %5d", i, //
//						"MaxBuy", maxGridBuy[i], //
//						"MaxSell", maxGridSell[i] //
//				));
//			}
//		}
//	}

//	private static Flexibility calculateFlexibility() {
//		Flexibility flexibility = new Flexibility();
//
//		EnergyModel em = null;
//		for (int i = 0; i < NO_OF_PERIODS; i++) {
//			em = new EnergyModel();
//
//			// Add Schedule-Constraints
//			addScheduleConstraints(em);
//
//			// Target function
//			em.model.addExpression("Extreme Grid Power") //
//					.set(em.periods[i].grid.power, ONE) //
//					.weight(ONE);
//
//			em.model.maximise();
//			flexibility.maxGridBuy[i] = em.periods[i].grid.buy.power.getValue().intValue();
//			em.model.minimise();
//			flexibility.maxGridSell[i] = em.periods[i].grid.sell.power.getValue().intValue();
//		}
//
//		return flexibility;
//	}

//	private static class Schedule {
//		protected final LocalDateTime startTime;
//		protected final EnergyModel energyModel;
//
//		public Schedule(LocalDateTime startTime, EnergyModel energyModel) {
//			this.startTime = startTime;
//			this.energyModel = energyModel;
//		}
//	}

	private static Solution calculateSchedule(EnergyModel em) {
		// Add Schedule-Constraints
		addScheduleConstraints(em);

		// Grid Buy Cost
		var gridBuyCosts = new IntVar[em.periods.length];
		for (int i = 0; i < em.periods.length; i++) {
			Period p = em.periods[i];
			gridBuyCosts[i] = em.model.intScaleView(p.grid.buy.power, p.grid.buy.cost);
		}
		var gridBuyCostSum = em.model.sum("Grid_Buy_Cost_Sum", gridBuyCosts);

		// Grid Sell Revenue
		var gridSellRevenues = new IntVar[em.periods.length];
		for (int i = 0; i < em.periods.length; i++) {
			Period p = em.periods[i];
			gridSellRevenues[i] = em.model.intScaleView(p.grid.sell.power, p.grid.sell.revenue);
		}
		var gridSellRevenueSum = em.model.sum("Grid_Sell_Revenue_Sum", gridSellRevenues);

		// Target function: Grid Exchange Cost
		var gridExchangeCost = em.model.intVar("Grid Exchange Cost Sum", -99999, 99999 /* TODO */);
//		em.model.arithm(gridBuyCostSum, "-", gridSellRevenueSum, "=", gridExchangeCost).post();

		// TODO additional target function: charge power should be evenly distributed;
		// i.e. minimise squared error between last period and current period power

		Solver solver = em.model.getSolver();
		Solution best = solver.findOptimalSolution(gridExchangeCost, false);
		// Result result = em.model.minimise();

		return best;
	}

	/**
	 * Add Schedule-Constraints.
	 * 
	 * @param em
	 */
	private static void addScheduleConstraints(EnergyModel em) {
		/*
		 * Schedule-Constraints for HLZF Controller
		 */

		// At end of HLZF battery is expected to be empty
		em.model.arithm(em.periods[5].ess.energy, "=", 0).post();

		// At beginning of HLZF battery must be full
		em.model.arithm(em.periods[18].ess.energy, "=", ESS_MAX_ENERGY * 60).post();

		// At end of HLZF battery is expected to be empty
		em.model.arithm(em.periods[23].ess.energy, "=", 0).post();

		// TODO Grid-Sell can never be more than Production. This simple model assumes
		// no production, so Grid-Sell must be zero - at least outside of HLZF period.
		for (int i = 0; i < 6; i++) {
			em.model.arithm(em.periods[i].grid.sell.power, "<=", GRID_SELL_LIMIT).post();
		}
		for (int i = 18; i < 23; i++) {
			em.model.arithm(em.periods[i].grid.sell.power, "<=", GRID_SELL_LIMIT).post();
		}
	}

}
