package energymodel;

import static java.math.BigDecimal.ONE;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.Variable;

import io.openems.controller.emsig.ojalgo.EnergyModel;
import io.openems.controller.emsig.ojalgo.Period;

import org.ojalgo.OjAlgoUtils;
import org.ojalgo.RecoverableCondition;
import org.ojalgo.matrix.Primitive64Matrix;
import org.ojalgo.matrix.decomposition.QR;
import org.ojalgo.matrix.store.ElementsSupplier;
import org.ojalgo.matrix.store.MatrixStore;
import org.ojalgo.matrix.store.PhysicalStore;
import org.ojalgo.matrix.store.Primitive64Store;
import org.ojalgo.matrix.task.InverterTask;
import org.ojalgo.matrix.task.SolverTask;

public class EnergyApp {

	public static void main(String[] args) {
		Instant now = Instant.now();

		// At least once very period, i.e. 15 minutes
//		Flexibility flexibility = calculateFlexibility();
//		flexibility.prettyPrint();

		// Create the actual optimal Schedule
		EnergyModel schedule = calculateSchedule();
//		schedule = new Schedule(LocalDateTime.now(this.clockProvider.getClock()), schedule);

		System.out.println("Time: " + Duration.between(now, Instant.now()).toMillis() + "ms");

		schedule.prettyPrint();
		schedule.plot(schedule);
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

	private static EnergyModel calculateSchedule() {
		EnergyModel em = new EnergyModel();

		// Add Schedule-Constraints
		addScheduleConstraints(em);

		// Grid Buy Cost
		// gridBuyCostSumExpr. = 0 = -gridBuyCostSum +
		// \sum_{i = 1}^{periods.length} periods[i].grid.buy.power *
		// periods[i].grid.buy.cost
		Variable gridBuyCostSum = em.model.addVariable("Grid_Buy_Cost_Sum");
		Expression gridBuyCostSumExpr = em.model.addExpression("Grid_Buy_Cost_Sum_Expr") //
				.set(gridBuyCostSum, ONE.negate());
		for (Period p : em.periods) {
			gridBuyCostSumExpr.set(p.grid.buy.power, p.grid.buy.cost);
		}
		gridBuyCostSumExpr.level(0);

		// Grid Sell Revenue
		// gridSellRevenueSumExpr. = 0 = - gridSellRevenueSum +
		// \sum_{i = 1}^{periods.length} periods[i].grid.sell.power *
		// periods[i].grid.sell.revenue
		Variable gridSellRevenueSum = em.model.addVariable("Grid_Sell_Revenue_Sum");
		Expression gridSellRevenueSumExpr = em.model.addExpression("Grid_Sell_Revenue_Sum_Expr") //
				.set(gridSellRevenueSum, ONE.negate()); //
		for (Period p : em.periods) {
			gridSellRevenueSumExpr.set(p.grid.sell.power, p.grid.sell.revenue);
		}
		gridSellRevenueSumExpr.level(0);

		// Target function: Grid Exchange Cost
		em.model.addExpression("Grid Exchange Cost Sum") //
				.set(gridBuyCostSum, ONE) //
				.set(gridSellRevenueSum, ONE.negate()) //
				.weight(1000);

		// TODO additional target function: charge power should be evenly distributed;
		// i.e. minimize squared error between last period and current period power
		// Instead of the maximum squared error, one can also minimize the sum of all
		// squared errors between
		// the (respective) last period and current period power:
		// \sum_{i =1}^{em.periods.length} (em.periods[i].ess.power -
		// em.periods[i-1].ess.power)^2
		
		
		// decide whether the ESS is charged or discharged within a period
		// introduce the charging state and allow Charge XOR Discharge
		// since it is not possible to impose a constraint concerning the
		// multiplication of two variables, we define this as an objective function
		// with minimum value 0
		// TODO runtime: an hour
//		for (Period p : em.periods) {
//		em.model.addExpression("ESS_" + p.name + "Charge_Constraint_Expr") //
//				.set(p.ess.charge.mode, p.ess.discharge.power, 1.0) //
//				.weight(1);
//		em.model.addExpression("ESS_" + p.name + "Discharge_Constraint_Expr") //
//				.set(p.ess.discharge.mode, p.ess.charge.power, 1.0) //
//				.weight(1);	
//
//		}
		// define NO_OF_PERIODS -1 additional variables and minimize
		// the sum of their squares
		//charDiff(i) = em.periods[i+1].ess.charge.power - em.periods[i].ess.charge.power
		List<Variable> charDiffs = new ArrayList<>();
		for (int i = 0; i < em.periods.length - 1; i++) {
			Variable charDiff = em.model.addVariable("Charge_Diff_" + i); //
			em.model.addExpression("Charge_Diff_" + i + "_Expr") //
					.set(charDiff, ONE) //
					.set(em.periods[i + 1].ess.charge.power, ONE.negate()) //
					.set(em.periods[i].ess.charge.power, ONE) //
					.level(0);
			charDiffs.add(charDiff);
		}
		
		// introduce an (em.periods.length) x (em.periods.length)-unit matrix to square the 
		//charDiff-variables
		
		Primitive64Store identity = Primitive64Store.FACTORY.rows(new double[em.periods.length - 1][em.periods.length -1]); // 95
				for (int i = 0; i < em.periods.length -1 ; i++) {
					identity.add(i, i, 1);
				};


		Expression evenlyDistributedCharge = em.model.addExpression("Evenly Distributed Charge");
			evenlyDistributedCharge.setQuadraticFactors(charDiffs, identity);
		evenlyDistributedCharge.weight(ONE);

		em.model.minimise();
		// Result result = em.model.minimise();

		return em;
	}

	/**
	 * Add Schedule-Constraints.
	 * 
	 * @param em
	 */
	private static void addScheduleConstraints(EnergyModel em) {
//		/*
//		 * Schedule-Constraints for HLZF (Hochlastzeitfenster) Controller
//		 */
//		// We assume that there are 2 HLZFs
//		// At end of HLZF1 battery is expected to be empty
//		// 0 = 1*e[5]
//		em.model.addExpression("End of 1st HLZF") //
//				.set(em.periods[5].ess.energy, ONE) // alternatively periods[20]
//				.level(0);
//
//		// At beginning of HLZF^2 battery must be full
//		em.model.addExpression("Beginning of 2nd HLZF") //
//				.set(em.periods[15].ess.energy, ONE) // alternatively periods[60]
//				.level(ESS_MAX_ENERGY * 60); // in Wmin
//
//		// At end of HLZF2 battery is expected to be empty
//		em.model.addExpression("End of 2nd HLZF") //
//				.set(em.periods[23].ess.energy, ONE) // alternatively periods[92]
//				.level(0);
//
//		// TODO Grid-Sell can never be more than Production. This simple model assumes
//		// no production, so Grid-Sell must be zero - at least outside of HLZF period.
//		for (int i = 0; i < 6; i++) { // alternatively i = 0; i <19; i++
//			em.periods[i].grid.sell.power.upper(GRID_SELL_LIMIT);
//		}
//		for (int i = 18; i < 23; i++) { // TODO why 18? 
//			em.periods[i].grid.sell.power.upper(GRID_SELL_LIMIT);
//		}
//				
	}

}
