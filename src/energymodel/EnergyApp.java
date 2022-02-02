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

//import org.ojalgo.OjAlgoUtils;
//import org.ojalgo.RecoverableCondition;
//import org.ojalgo.matrix.Primitive64Matrix;
//import org.ojalgo.matrix.decomposition.QR;
//import org.ojalgo.matrix.store.ElementsSupplier;
//import org.ojalgo.matrix.store.MatrixStore;
//import org.ojalgo.matrix.store.PhysicalStore;
import org.ojalgo.matrix.store.Primitive64Store;
//import org.ojalgo.matrix.task.InverterTask;
//import org.ojalgo.matrix.task.SolverTask;

import static io.openems.controller.emsig.ojalgo.Constants.ESS_MAX_ENERGY;
import static io.openems.controller.emsig.ojalgo.Constants.HH_LOAD;
import static io.openems.controller.emsig.ojalgo.Constants.NO_OF_PERIODS;
import static io.openems.controller.emsig.ojalgo.Constants.EV_INITIAL_ENERGY;
import static io.openems.controller.emsig.ojalgo.Constants.EV_MAX_ENERGY;
import static io.openems.controller.emsig.ojalgo.Constants.EV_REQUIRED_ENERGY;
import static io.openems.controller.emsig.ojalgo.Constants.PV_POWER;

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
		
		// RESOLVED
		// Decide whether the ESS is charged or discharged within a period.
		// Introduce the charging and discharging mode and allow charge XOR discharge.
		// Since it is not possible (yet) to impose a constraint concerning the
		// multiplication of two variables ("quadratic constraint"), we define this as an
		// additional target function with minimum value 0.
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
//		List<Variable> charDiffs = new ArrayList<>();
//		for (int i = 0; i < em.periods.length - 1; i++) {
//			Variable charDiff = em.model.addVariable("Charge_Diff_" + i); //
//			em.model.addExpression("Charge_Diff_" + i + "_Expr") //
//					.set(charDiff, ONE) //
//					.set(em.periods[i + 1].ess.charge.power, ONE.negate()) //
//					.set(em.periods[i].ess.charge.power, ONE) //
//					.level(0);
//			charDiffs.add(charDiff);
//		}
//		
//		// introduce an (em.periods.length) x (em.periods.length)-unit matrix to square the 
//		//charDiff-variables
//		
//		Primitive64Store identity = Primitive64Store.FACTORY.rows(new double[NO_OF_PERIODS- 1][NO_OF_PERIODS -1]); // 95
//				for (int i = 0; i < NO_OF_PERIODS -1 ; i++) {
//					identity.add(i, i, 1);
//				};
//
//		// To BOUND this target function by the square of the maximum charge difference
//		// use .weight(1/Math.sqrt(NO_OF_PERIODS -1)).
//		Expression evenlyDistributedCharge = em.model.addExpression("Evenly Distributed Charge");
//			evenlyDistributedCharge.setQuadraticFactors(charDiffs, identity);
//		//	evenlyDistributedCharge.weight(ONE);
//			evenlyDistributedCharge.weight(1/Math.sqrt(NO_OF_PERIODS -1));	
			
			
		// New constraint: the EV is supposed to be charged with EV_REQUIRED_ENERGY at least,
		// and with EV_MAX_ENERGY - EV_INITIAL_ENERGY at most
		// TODO Problem: runtime (almost 10 minutes)
//			Expression minRequiredCharge = em.model.addExpression("Minimum_Required_Charging");
//				for (Period p : em.periods) {
//					minRequiredCharge.set(p.ev.energy, ONE);
//				}
//				minRequiredCharge.lower(EV_REQUIRED_ENERGY * 60);
//				minRequiredCharge.upper((EV_MAX_ENERGY - EV_INITIAL_ENERGY)*60);
//				minRequiredCharge.level(EV_REQUIRED_ENERGY * 60);
			
//		em.model.addExpression("Minimum_Required_Charging") //
//			.set(em.periods[NO_OF_PERIODS-1].ev.energy,  ONE) //
//			.lower(EV_REQUIRED_ENERGY*60);
			

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
//		int index = 0;
//		for (int i = 96; i < 144; i++) {
//			if (PV_POWER[i] >= HH_LOAD[i]) {
//				index = i;
//			}
//			break;
//		}
//		if (index > 0) {
//		int pvSum = 0;
//		for (int j = 96; j < index; j++) {
//			pvSum += PV_POWER[j];
//		}
//			
//		int hhSum = 0;
//		for (int k = 96; k < index; k++) {
//			hhSum += HH_LOAD[k];
//		}
		// At the end of the day we want a SoC of xy% 
//			em.model.addExpression("ESS_Schedule") //
//				.set(em.periods[95].ess.energy, ONE) // alternatively periods[60]
//				.lower(ESS_MAX_ENERGY * 60 *25/100); // in Wmin
			//	.lower((hhSum - pvSum)*15); // in Wmin
//		}	
//		// At end of HLZF2 battery is expected to be empty.
//		em.model.addExpression() //
//				.set(em.periods[28].ess.energy, ONE) 
//				.level(ESS_MAX_ENERGY *60 *20/100);
		
		
//		// At the end of the day the EV has to be fully charged.
//			em.model.addExpression("EV_Schedule") //
//				.set(em.periods[95].ev.energy, ONE) //
//				.level(EV_MAX_ENERGY*60);
		
		// At the end of the day the EVs have to be fully charged.
		em.model.addExpression("EV0_Schedule") //
			.set(em.periods[95].evs.get(0).energy, ONE) //
			.level(EV_MAX_ENERGY*60);
		em.model.addExpression("EV1_Schedule") //
			.set(em.periods[95].evs.get(1).energy, ONE) //
			.lower(EV_MAX_ENERGY*60*80/100);

//		// TODO Grid-Sell can never be more than Production. This simple model assumes
//		// no production, so Grid-Sell must be zero - at least outside of HLZF period.
//		for (int i = 0; i < 6; i++) { // alternatively i = 0; i <19; i++
//			em.periods[i].grid.sell.power.upper(GRID_SELL_LIMIT);
//		}
//		for (int i = 18; i < 23; i++) { 
//			em.periods[i].grid.sell.power.upper(GRID_SELL_LIMIT);
//		}
//				
	}

}
