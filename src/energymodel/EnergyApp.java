package energymodel;

import static io.openems.controller.emsig.ojalgo.Constants.ESS_MAX_ENERGY;
import static io.openems.controller.emsig.ojalgo.Constants.GRID_SELL_LIMIT;
import static io.openems.controller.emsig.ojalgo.Constants.ESS_MAX_ENERGY;
import static io.openems.controller.emsig.ojalgo.Constants.ESS_MAX_CHARGE;
import static io.openems.controller.emsig.ojalgo.Constants.NO_OF_PERIODS;
import static io.openems.controller.emsig.ojalgo.Constants.EV_INITIAL_ENERGY;
import static io.openems.controller.emsig.ojalgo.Constants.EV_MAX_ENERGY;
import static io.openems.controller.emsig.ojalgo.Constants.EV_REQUIRED_ENERGY;

import static org.chocosolver.solver.search.strategy.Search.*;

import java.time.Duration;
import java.time.Instant;

import org.chocosolver.solver.Model;
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

		// cf. ojAlgo
		// gridBuyCostSumExpr. = 0 = -gridBuyCostSum +
		// \sum_{i = 1}^{periods.length} periods[i].grid.buy.power *
		// periods[i].grid.buy.cost
//				Variable gridBuyCostSum = em.model.addVariable("Grid_Buy_Cost_Sum");
//				Expression gridBuyCostSumExpr = em.model.addExpression("Grid_Buy_Cost_Sum_Expr") //
//						.set(gridBuyCostSum, ONE.negate());
//				for (Period p : em.periods) {
//					gridBuyCostSumExpr.set(p.grid.buy.power, p.grid.buy.cost);
//				}
//				gridBuyCostSumExpr.level(0);
		
		// Grid Sell Revenue
		var gridSellRevenues = new IntVar[em.periods.length];
		for (int i = 0; i < em.periods.length; i++) {
			Period p = em.periods[i];
			gridSellRevenues[i] = em.model.intScaleView(p.grid.sell.power, p.grid.sell.revenue);
		}
		var gridSellRevenueSum = em.model.sum("Grid_Sell_Revenue_Sum", gridSellRevenues);
		
		// cf. ojAlgo
		// gridSellRevenueSumExpr. = 0 = - gridSellRevenueSum +
				// \sum_{i = 1}^{periods.length} periods[i].grid.sell.power *
				// periods[i].grid.sell.revenue
//				Variable gridSellRevenueSum = em.model.addVariable("Grid_Sell_Revenue_Sum");
//				Expression gridSellRevenueSumExpr = em.model.addExpression("Grid_Sell_Revenue_Sum_Expr") //
//						.set(gridSellRevenueSum, ONE.negate()); //
//				for (Period p : em.periods) {
//					gridSellRevenueSumExpr.set(p.grid.sell.power, p.grid.sell.revenue);
//				}
//				gridSellRevenueSumExpr.level(0);

		// Target function: Grid Exchange Cost
		var gridExchangeCost = em.model.intVar("Grid Exchange Cost Sum", -10000, 10000 /* TODO */);
		em.model.arithm(gridBuyCostSum, "-", gridSellRevenueSum, "=", gridExchangeCost).post();
		
		// cf. ojAlgo
		// Target function: Grid Exchange Cost
//				em.model.addExpression("Grid Exchange Cost Sum") //
//						.set(gridBuyCostSum, ONE) //
//						.set(gridSellRevenueSum, ONE.negate()) //
//						.weight(1000);
		
		// New constraint: the EV is supposed to be charged throughout the day
		// with EV_REQUIRED_ENERGY at least,
		// and with EV_MAX_ENERGY - EV_INITIAL_ENERGY at most
		// 
//		var evChargeEnergies = new IntVar[em.periods.length];
//		for (int i = 0; i < em.periods.length; i++) {
//			Period p = em.periods[i];
//			evChargeEnergies[i] = p.ev.energy;
//		}
//		var evChargeEnergiesSum = em.model.sum("EV_Charge_Energies_Sum", evChargeEnergies);
//		
//		em.model.arithm(evChargeEnergiesSum, ">=", EV_REQUIRED_ENERGY*60).post();
//		em.model.arithm(evChargeEnergiesSum, "<=", (EV_MAX_ENERGY - EV_INITIAL_ENERGY)*60).post();
		
		em.model.arithm(em.periods[NO_OF_PERIODS-1].ev.energy, ">=", EV_REQUIRED_ENERGY*60).post();
		
		// cf. ojAlgo
//		Expression minRequiredCharge = em.model.addExpression("Minimum_Required_Charging");
//		for (Period p : em.periods) {
//			minRequiredCharge.set(p.ev.energy, ONE);
//		}
//	//	minRequiredCharge.lower(EV_REQUIRED_ENERGY * 60);
//	//	minRequiredCharge.upper((EV_MAX_ENERGY - EV_INITIAL_ENERGY)*60);
//		minRequiredCharge.level(EV_REQUIRED_ENERGY * 60);
		
		// Additional target function: charge power should be evenly distributed,
		// i.e., minimize squared error between last period and current period power (||.||_{\infty}).
		// Instead of the maximum squared error, one can also minimize the sum of all
		// squared errors between
		// the (respective) last period and current period power (||.||_2):
		// \sum_{i =1}^{em.periods.length} (em.periods[i].ess.power -
		// em.periods[i-1].ess.power)^2.
		// Note that we have the following norm equivalence (for all x \in R^{NO_OF_PERIODS-1}):
		// ||x||_{\infty} <= ||x||_2 <= \sqrt{NO_OF_PERIODS -1} ||x||_{\infty}.
		// In particular, 1/(\sqrt{NO_OF_PERIODS -1}) ||x||_2 <= ||x||_{\infty}.
		// Hence, one might weight the resulting target function with/by (?)
		// 1/(\sqrt{NO_OF_PERIODS -1}).
		// By doing so, that target function is bounded by [0, (ESS_MAX_CHARGE)^2] 
		
		//charDiffs TODO does not work
//		var charDiffs = new IntVar[em.periods.length - 1];
//		for (int i = 0; i < em.periods.length - 1; i++) {
//			em.model.arithm(em.periods[i+1].ess.charge.power, "-", em.periods[i].ess.charge.power, "=", charDiffs[i]).post();
//			 charDiffs[i].sqr();
//		}
//		var charDiffSum = em.model.sum("Char_Diff_Sum", charDiffs);
		

		Solver solver = em.model.getSolver();
		solver.limitTime("180s");
		 Solution best = solver.findOptimalSolution(gridExchangeCost, false);
		 System.out.println(gridExchangeCost);
//		Solution best = solver.findOptimalSolution(charDiffSum, false);
//		System.out.println(charDiffSum);
		return best;
				
				// define NO_OF_PERIODS -1 additional variables and minimize
				// the sum of their squares
				//charDiff(i) = em.periods[i+1].ess.charge.power - em.periods[i].ess.charge.power
//				List<Variable> charDiffs = new ArrayList<>();
//				for (int i = 0; i < em.periods.length - 1; i++) {
//					Variable charDiff = em.model.addVariable("Charge_Diff_" + i); //
//					em.model.addExpression("Charge_Diff_" + i + "_Expr") //
//							.set(charDiff, ONE) //
//							.set(em.periods[i + 1].ess.charge.power, ONE.negate()) //
//							.set(em.periods[i].ess.charge.power, ONE) //
//							.level(0);
//					charDiffs.add(charDiff);
//				}
				
				// introduce an (em.periods.length) x (em.periods.length)-unit matrix to square the 
				//charDiff-variables			
//				Primitive64Store identity = Primitive64Store.FACTORY.rows(new double[NO_OF_PERIODS- 1][NO_OF_PERIODS -1]); // 95
//						for (int i = 0; i < NO_OF_PERIODS -1 ; i++) {
//							identity.add(i, i, 1);
//						};

				// To BOUND this target function by the square of the maximum charge difference
				// use .weight(1/Math.sqrt(NO_OF_PERIODS -1)).
//				Expression evenlyDistributedCharge = em.model.addExpression("Evenly Distributed Charge");
//					evenlyDistributedCharge.setQuadraticFactors(charDiffs, identity);
//				//	evenlyDistributedCharge.weight(ONE);
//					evenlyDistributedCharge.weight(1/Math.sqrt(NO_OF_PERIODS -1));
					

//				em.model.minimise();
				// Result result = em.model.minimise();

//				return em;
		
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

//		// At end of HLZF battery is expected to be empty
//		em.model.arithm(em.periods[5].ess.energy, "=", 0).post();
//
//		// At beginning of HLZF battery must be full
//		em.model.arithm(em.periods[18].ess.energy, "=", ESS_MAX_ENERGY * 60).post();
//
//		// At end of HLZF battery is expected to be empty
//		em.model.arithm(em.periods[23].ess.energy, "=", 0).post();
//
//		// TODO Grid-Sell can never be more than Production. This simple model assumes
//		// no production, so Grid-Sell must be zero - at least outside of HLZF period.
//		for (int i = 0; i < 6; i++) {
//			em.model.arithm(em.periods[i].grid.sell.power, "<=", GRID_SELL_LIMIT).post();
//		}
//		for (int i = 18; i < 23; i++) {
//			em.model.arithm(em.periods[i].grid.sell.power, "<=", GRID_SELL_LIMIT).post();
//		}
	}

}
