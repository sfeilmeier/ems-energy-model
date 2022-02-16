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

import static io.openems.controller.emsig.ojalgo.Constants.ESS_INITIAL_ENERGY;
import static io.openems.controller.emsig.ojalgo.Constants.ESS_MAX_CHARGE;
import static io.openems.controller.emsig.ojalgo.Constants.ESS_MAX_ENERGY;
import static io.openems.controller.emsig.ojalgo.Constants.HH_LOAD;
import static io.openems.controller.emsig.ojalgo.Constants.NO_OF_PERIODS;
import static io.openems.controller.emsig.ojalgo.Constants.MINUTES_PER_PERIOD;
import static io.openems.controller.emsig.ojalgo.Constants.EV_INITIAL_ENERGY;
import static io.openems.controller.emsig.ojalgo.Constants.EV_MAX_ENERGY;
import static io.openems.controller.emsig.ojalgo.Constants.EV_REQUIRED_ENERGY;
import static io.openems.controller.emsig.ojalgo.Constants.PV_POWER;
import static io.openems.controller.emsig.ojalgo.Constants.ESS_CHARGE_EFFICIENCY;
import static io.openems.controller.emsig.ojalgo.Constants.ESS_DISCHARGE_EFFICIENCY;
import static io.openems.controller.emsig.ojalgo.Constants.EV_CHARGE_EFFICIENCY;

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
		// gridBuyCostSumExpr = 0 = -gridBuyCostSum +
		// \sum_{i = 0}^{em.periods.length - 1} periods[i].grid.buy.power
		// * periods[i].grid.buy.cost
		Variable gridBuyCostSum = em.model.addVariable("Grid_Buy_Cost_Sum");
		Expression gridBuyCostSumExpr = em.model.addExpression("Grid_Buy_Cost_Sum_Expr") //
				.set(gridBuyCostSum, ONE.negate());
		for (Period p : em.periods) {
			gridBuyCostSumExpr.set(p.grid.buy.power, p.grid.buy.cost);
		}
		gridBuyCostSumExpr.level(0);

		// Grid Sell Revenue
		// gridSellRevenueSumExpr = 0 = - gridSellRevenueSum +
		// \sum_{i = 0}^{periods.length - 1} periods[i].grid.sell.power
		// * periods[i].grid.sell.revenue
		Variable gridSellRevenueSum = em.model.addVariable("Grid_Sell_Revenue_Sum");
		Expression gridSellRevenueSumExpr = em.model.addExpression("Grid_Sell_Revenue_Sum_Expr") //
				.set(gridSellRevenueSum, ONE.negate()); //
		for (Period p : em.periods) {
			gridSellRevenueSumExpr.set(p.grid.sell.power, p.grid.sell.revenue);
		}
		gridSellRevenueSumExpr.level(0);

		// In case of 2 PVs
//		Variable gridSellRevenueSum = em.model.addVariable("Grid_Sell_Revenue_Sum");
//		Expression gridSellRevenueSumExpr = em.model.addExpression("Grid_Sell_Revenue_Sum_Expr") //
//				.set(gridSellRevenueSum, ONE.negate()); //
//		for (Period p : em.periods) {
//			gridSellRevenueSumExpr.set(p.pvs.get(0).power.sell, p.grid.sell.revenue[0]);
//			gridSellRevenueSumExpr.set(p.pvs.get(1).power.sell, p.grid.sell.revenue[1]);
//		}
//		gridSellRevenueSumExpr.level(0);

		// Objective function: Grid Exchange Cost
		// If one adds the objective function concerning evenly distributed
		// charging to the model, using the weight 1000 here is recommended
		em.model.addExpression("Grid Exchange Cost Sum") //
				.set(gridBuyCostSum, ONE) //
				.set(gridSellRevenueSum, ONE.negate()) //
				.weight(1);

		// Additional objective function: charge power should be evenly distributed,
		// i.e., minimize squared error between last period and current period power
		// (||.||_{\infty}).
		// Instead of the maximum squared error, one can also minimize the sum of all
		// squared errors between
		// the (respective) last period and current period power (||.||_2):
		// \sum_{i =1}^{em.periods.length - 1} (em.periods[i].ess.power -
		// em.periods[i-1].ess.power)^2.
		// Note that we have the following norm equivalence (for all x \in
		// \mathbb{R}^{em.periods.length - 1}):
		// ||x||_{\infty} <= ||x||_2 <= \sqrt{NO_OF_PERIODS -1} ||x||_{\infty}.
		// In particular, 1/(\sqrt{NO_OF_PERIODS -1}) ||x||_2 <= ||x||_{\infty}.
		// Hence, for the resulting objective function one may use the weight
		// 1/(\sqrt{NO_OF_PERIODS -1}).
		// By doing so, that objective function is bounded by [0, (ESS_MAX_CHARGE)^2]

		// Define em.periods.length - 1 additional variables and minimize
		// the sum of their squares
		// charDiff(i) = em.periods[i+1].ess.charge.power
		// - em.periods[i].ess.charge.power
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
//		// introduce an (em.periods.length) x (em.periods.length)-unit matrix to square
//		// the
//		// charDiff-variables
//
//		Primitive64Store identity = Primitive64Store.FACTORY.rows(new double[NO_OF_PERIODS - 1][NO_OF_PERIODS - 1]); // 95
//		for (int i = 0; i < NO_OF_PERIODS - 1; i++) {
//			identity.add(i, i, 1);
//		}
//		;
//
//		// To BOUND this target function by the square of the maximum charge difference
//		// use .weight(1/Math.sqrt(NO_OF_PERIODS -1)).
//		Expression evenlyDistributedCharge = em.model.addExpression("Evenly Distributed Charge");
//		evenlyDistributedCharge.setQuadraticFactors(charDiffs, identity);
//		// evenlyDistributedCharge.weight(ONE);
//		evenlyDistributedCharge.weight(1 / Math.sqrt(NO_OF_PERIODS - 1));

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
//		em.model.addExpression("End of 1st HLZF") //
//				.set(em.periods[x].ess.energy, ONE) // 
//				.level(0);

		// At the end of the day we want a SoC of xy%
//			em.model.addExpression("ESS_Schedule") //
//				.set(em.periods[em.periods.length -1].ess.energy, ONE) //
//				.lower(ESS_MAX_ENERGY * 60 *y/100); // in Wmin
//		}	

		// Find first index where pv power permanently
		// undercuts hhload and summarize the power differences
		// starting at this index
		// This is important for the ess schedule constraint
		int hhMoreThanpvIndex = 0;
		for (int j = em.periods.length - 1; j >= 0; j--) {
			if (em.periods[j].pv.power.prod >= em.periods[j].hh.power.cons) {
				if (j == em.periods.length - 1) {
					hhMoreThanpvIndex = j;
				} else {
					hhMoreThanpvIndex = j + 1;
				}
				break;
			}
		}

		int endOfDayLoad = 0;
		for (int j = hhMoreThanpvIndex; j < em.periods.length; j++) {
			endOfDayLoad = endOfDayLoad + em.periods[j].pv.power.prod - em.periods[j].hh.power.cons;
		}

		// A first approach for proper ESS scheduling concerning
		// the SoC at the end of the day
		// With the present specifications, the model is prone to
		// provide maximal energy autarchy of the EMS
		// TODO In the future, one shall add an additional objective function
		// that makes sure that the ess has a "reasonable" SoC at the end of
		// the day
		int pvPowerSum = 0;
		int hhLoadSum = 0;
		for (Period p : em.periods) {
			pvPowerSum += p.pv.power.prod;
			hhLoadSum += p.hh.power.cons;
		}
		// Impose schedule constraints for the ess
		// depending on ESS_INITIAL_ENERGY, pvPowerSum, hhLoadSum, and endOfDayLoad
		// The precise values for boundaries and safety margins are chosen arbitrarily
		// If pvPowerSum >= hhLoadSum, the ess is predominantly charged
		// If pvPowerSum < hhLoadSum, the ess is predominantly discharged
		// Depending on the scenario, we need to incorporate charge/discharge
		// efficiencies into the schedule constraint
		// Since endOfDayLoad may be served by the ess, we
		// take the discharge efficiency (+ some safety margin) into account

		// If the forecasted values are such that the ess is potentially fully charged,
		// then we require the ess to be almost fully charged (including some safety
		// margins)
//		if (ESS_INITIAL_ENERGY * 60 + (Math.min((pvPowerSum - hhLoadSum) * ESS_CHARGE_EFFICIENCY / 100,
//				(pvPowerSum - hhLoadSum) * ESS_DISCHARGE_EFFICIENCY / 100)) * MINUTES_PER_PERIOD >= ESS_MAX_ENERGY
//						* 60) {
//			em.model.addExpression("ESS_Schedule_Expr") //
//					.set(em.periods[em.periods.length - 1].ess.energy, ONE) //
//					.lower(Math.min(ESS_MAX_ENERGY * 60 * 85 / 100, Math.max(0, ESS_MAX_ENERGY * 60 * 90 / 100
//							+ Math.min(0, endOfDayLoad * MINUTES_PER_PERIOD * (ESS_DISCHARGE_EFFICIENCY + 10) / 100))));
//			// If the potential energy is in [75% * ESS_MAX_ENERGY*60, ESS_MAX_ENERGY*60],
//			// relax the schedule constraint accordingly
//		} else if (ESS_MAX_ENERGY
//				* 60 > ESS_INITIAL_ENERGY * 60 + (Math.min((pvPowerSum - hhLoadSum) * ESS_CHARGE_EFFICIENCY / 100,
//						(pvPowerSum - hhLoadSum) * ESS_DISCHARGE_EFFICIENCY / 100)) * MINUTES_PER_PERIOD
//				&& ESS_INITIAL_ENERGY * 60 + (Math.min((pvPowerSum - hhLoadSum) * ESS_CHARGE_EFFICIENCY / 100,
//						(pvPowerSum - hhLoadSum) * ESS_DISCHARGE_EFFICIENCY / 100))
//						* MINUTES_PER_PERIOD >= ESS_MAX_ENERGY * 60 * 75 / 100) {
//			em.model.addExpression("ESS_Schedule_Expr") //
//					.set(em.periods[em.periods.length - 1].ess.energy, ONE) //
//					.lower(Math.min(ESS_MAX_ENERGY * 60 * 75 / 100, Math.max(0, ESS_INITIAL_ENERGY * 60
//							+ (Math.min((pvPowerSum - hhLoadSum) * ESS_CHARGE_EFFICIENCY / 100,
//									(pvPowerSum - hhLoadSum) * ESS_DISCHARGE_EFFICIENCY / 100)) * MINUTES_PER_PERIOD
//							+ Math.min(0, endOfDayLoad * MINUTES_PER_PERIOD * (ESS_DISCHARGE_EFFICIENCY + 10) / 100))));
//			// If the potential energy is in [50% * ESS_MAX_ENERGY*60, 75% *
//			// ESS_MAX_ENERGY*60], relax the schedule constraint accordingly
//		} else if (ESS_MAX_ENERGY * 60 * 75
//				/ 100 > ESS_INITIAL_ENERGY * 60 + (Math.min((pvPowerSum - hhLoadSum) * ESS_CHARGE_EFFICIENCY / 100,
//						(pvPowerSum - hhLoadSum) * ESS_DISCHARGE_EFFICIENCY / 100)) * MINUTES_PER_PERIOD
//				&& ESS_INITIAL_ENERGY * 60 + (Math.min((pvPowerSum - hhLoadSum) * ESS_CHARGE_EFFICIENCY / 100,
//						(pvPowerSum - hhLoadSum) * ESS_DISCHARGE_EFFICIENCY / 100))
//						* MINUTES_PER_PERIOD >= ESS_MAX_ENERGY * 60 * 50 / 100) {
//			em.model.addExpression("ESS_Schedule_Expr") //
//					.set(em.periods[em.periods.length - 1].ess.energy, ONE) //
//					.lower(Math.min(ESS_MAX_ENERGY * 60 * 50 / 100, Math.max(0, ESS_INITIAL_ENERGY * 60
//							+ (Math.min((pvPowerSum - hhLoadSum) * ESS_CHARGE_EFFICIENCY / 100,
//									(pvPowerSum - hhLoadSum) * ESS_DISCHARGE_EFFICIENCY / 100)) * MINUTES_PER_PERIOD
//							+ Math.min(0, endOfDayLoad * MINUTES_PER_PERIOD * (ESS_DISCHARGE_EFFICIENCY + 10) / 100))));
//			// If the potential energy is in [25% * ESS_MAX_ENERGY*60, 50% *
//			// ESS_MAX_ENERGY*60], relax the schedule constraint accordingly
//		} else if (ESS_MAX_ENERGY * 60 * 50
//				/ 100 > ESS_INITIAL_ENERGY * 60 + (Math.min((pvPowerSum - hhLoadSum) * ESS_CHARGE_EFFICIENCY / 100,
//						(pvPowerSum - hhLoadSum) * ESS_DISCHARGE_EFFICIENCY / 100)) * MINUTES_PER_PERIOD
//				&& ESS_INITIAL_ENERGY * 60 + (Math.min((pvPowerSum - hhLoadSum) * ESS_CHARGE_EFFICIENCY / 100,
//						(pvPowerSum - hhLoadSum) * ESS_DISCHARGE_EFFICIENCY / 100))
//						* MINUTES_PER_PERIOD >= ESS_MAX_ENERGY * 60 * 25 / 100) {
//			em.model.addExpression("ESS_Schedule_Expr") //
//					.set(em.periods[em.periods.length - 1].ess.energy, ONE) //
//					.lower(Math.min(ESS_MAX_ENERGY * 60 * 25 / 100, Math.max(0, ESS_INITIAL_ENERGY * 60
//							+ (Math.min((pvPowerSum - hhLoadSum) * ESS_CHARGE_EFFICIENCY / 100,
//									(pvPowerSum - hhLoadSum) * ESS_DISCHARGE_EFFICIENCY / 100)) * MINUTES_PER_PERIOD
//							+ Math.min(0, endOfDayLoad * MINUTES_PER_PERIOD * (ESS_DISCHARGE_EFFICIENCY + 10) / 100))));
//		} else {
//			// If the potential energy undercuts 25% * ESS_MAX_ENERGY*60, relax the schedule
//			// constraint accordingly
//			em.model.addExpression("ESS_Schedule_Expr") //
//					.set(em.periods[em.periods.length - 1].ess.energy, ONE) //
//					.lower(Math.max(0, ESS_INITIAL_ENERGY * 60
//							+ (Math.min((pvPowerSum - hhLoadSum) * ESS_CHARGE_EFFICIENCY / 100,
//									(pvPowerSum - hhLoadSum) * ESS_DISCHARGE_EFFICIENCY / 100)) * MINUTES_PER_PERIOD
//							+ Math.min(0, endOfDayLoad * MINUTES_PER_PERIOD * (ESS_DISCHARGE_EFFICIENCY + 10) / 100)));
//		}

		// If we add an EV to the model
		// Take the energy the EV has to be charged with into account
		// Again, in the "worst" case, the EV is charged with ess energy,
		// so that one has to incorporate both the ess discharge efficiency
		// and the ev charge efficiency into the schedule constraints
		// The remaining values and conditions are the same as for the
		// model without any EVs

		// If the forecasted values are such that the ess is potentially fully charged,
		// then we require the ess to be almost fully charged (including some safety
		// margins)
		if (ESS_INITIAL_ENERGY * 60
				+ (Math.min((pvPowerSum - hhLoadSum) * ESS_CHARGE_EFFICIENCY / 100,
						(pvPowerSum - hhLoadSum) * ESS_DISCHARGE_EFFICIENCY / 100)) * MINUTES_PER_PERIOD
				- (EV_MAX_ENERGY - EV_INITIAL_ENERGY) * 60 * ESS_DISCHARGE_EFFICIENCY
						/ EV_CHARGE_EFFICIENCY >= ESS_MAX_ENERGY * 60) {
			em.model.addExpression("ESS_Schedule_Expr") //
					.set(em.periods[em.periods.length - 1].ess.energy, ONE) //
					.lower(Math.min(ESS_MAX_ENERGY * 60 * 80 / 100,
							Math.max(0,
									ESS_MAX_ENERGY * 60 * 90 / 100
											+ Math.min(0,
													endOfDayLoad * MINUTES_PER_PERIOD * (ESS_DISCHARGE_EFFICIENCY + 10)
															/ 100)
											- (EV_MAX_ENERGY - EV_INITIAL_ENERGY) * 60 * ESS_DISCHARGE_EFFICIENCY
													/ EV_CHARGE_EFFICIENCY)));
			// If the potential energy is in [75% * ESS_MAX_ENERGY*60, ESS_MAX_ENERGY*60],
			// relax the schedule constraint accordingly
		} else if (ESS_MAX_ENERGY * 60 >= ESS_INITIAL_ENERGY * 60
				+ (Math.min((pvPowerSum - hhLoadSum) * ESS_CHARGE_EFFICIENCY / 100,
						(pvPowerSum - hhLoadSum) * ESS_DISCHARGE_EFFICIENCY / 100)) * MINUTES_PER_PERIOD
				- (EV_MAX_ENERGY - EV_INITIAL_ENERGY) * 60 * ESS_DISCHARGE_EFFICIENCY / EV_CHARGE_EFFICIENCY
				&& ESS_INITIAL_ENERGY * 60
						+ (Math.min((pvPowerSum - hhLoadSum) * ESS_CHARGE_EFFICIENCY / 100,
								(pvPowerSum - hhLoadSum) * ESS_DISCHARGE_EFFICIENCY / 100)) * MINUTES_PER_PERIOD
						- (EV_MAX_ENERGY - EV_INITIAL_ENERGY) * 60 * ESS_DISCHARGE_EFFICIENCY
								/ EV_CHARGE_EFFICIENCY >= ESS_MAX_ENERGY * 60 * 75 / 100) {
			em.model.addExpression("ESS_Schedule_Expr") //
					.set(em.periods[em.periods.length - 1].ess.energy, ONE) //
					.lower(Math.min(ESS_MAX_ENERGY * 60 * 60 / 100, Math.max(0, ESS_INITIAL_ENERGY * 60
							+ (Math.min((pvPowerSum - hhLoadSum) * ESS_CHARGE_EFFICIENCY / 100,
									(pvPowerSum - hhLoadSum) * ESS_DISCHARGE_EFFICIENCY / 100)) * MINUTES_PER_PERIOD
							- (EV_MAX_ENERGY - EV_INITIAL_ENERGY) * 60 * ESS_DISCHARGE_EFFICIENCY / EV_CHARGE_EFFICIENCY
							+ Math.min(0, endOfDayLoad * MINUTES_PER_PERIOD * (ESS_DISCHARGE_EFFICIENCY + 10) / 100))));
			// If the potential energy is in [50% * ESS_MAX_ENERGY*60, 75% *
			// ESS_MAX_ENERGY*60],
			// relax the schedule constraint accordingly
		} else if (ESS_MAX_ENERGY * 60 * 75 / 100 >= ESS_INITIAL_ENERGY * 60
				+ (Math.min((pvPowerSum - hhLoadSum) * ESS_CHARGE_EFFICIENCY / 100,
						(pvPowerSum - hhLoadSum) * ESS_DISCHARGE_EFFICIENCY / 100)) * MINUTES_PER_PERIOD
				- (EV_MAX_ENERGY - EV_INITIAL_ENERGY) * 60 * ESS_DISCHARGE_EFFICIENCY / EV_CHARGE_EFFICIENCY
				&& ESS_INITIAL_ENERGY * 60
						+ (Math.min((pvPowerSum - hhLoadSum) * ESS_CHARGE_EFFICIENCY / 100,
								(pvPowerSum - hhLoadSum) * ESS_DISCHARGE_EFFICIENCY / 100)) * MINUTES_PER_PERIOD
						- (EV_MAX_ENERGY - EV_INITIAL_ENERGY) * 60 * ESS_DISCHARGE_EFFICIENCY
								/ EV_CHARGE_EFFICIENCY >= ESS_MAX_ENERGY * 60 * 50 / 100) {
			em.model.addExpression("ESS_Schedule_Expr") //
					.set(em.periods[em.periods.length - 1].ess.energy, ONE) //
					.lower(Math.min(ESS_MAX_ENERGY * 60 * 40 / 100, Math.max(0, ESS_INITIAL_ENERGY * 60
							+ (Math.min((pvPowerSum - hhLoadSum) * ESS_CHARGE_EFFICIENCY / 100,
									(pvPowerSum - hhLoadSum) * ESS_DISCHARGE_EFFICIENCY / 100)) * MINUTES_PER_PERIOD
							- (EV_MAX_ENERGY - EV_INITIAL_ENERGY) * 60 * ESS_DISCHARGE_EFFICIENCY / EV_CHARGE_EFFICIENCY
							+ Math.min(0, endOfDayLoad * MINUTES_PER_PERIOD * (ESS_DISCHARGE_EFFICIENCY + 10) / 100))));
			// If the potential energy is in [25% * ESS_MAX_ENERGY*60, 50% *
			// ESS_MAX_ENERGY*60],
			// relax the schedule constraint accordingly
		} else if (ESS_MAX_ENERGY * 60 * 50 / 100 >= ESS_INITIAL_ENERGY * 60
				+ (Math.min((pvPowerSum - hhLoadSum) * ESS_CHARGE_EFFICIENCY / 100,
						(pvPowerSum - hhLoadSum) * ESS_DISCHARGE_EFFICIENCY / 100)) * MINUTES_PER_PERIOD
				- (EV_MAX_ENERGY - EV_INITIAL_ENERGY) * 60 * ESS_DISCHARGE_EFFICIENCY / EV_CHARGE_EFFICIENCY
				&& ESS_INITIAL_ENERGY * 60
						+ (Math.min((pvPowerSum - hhLoadSum) * ESS_CHARGE_EFFICIENCY / 100,
								(pvPowerSum - hhLoadSum) * ESS_DISCHARGE_EFFICIENCY / 100)) * MINUTES_PER_PERIOD
						- (EV_MAX_ENERGY - EV_INITIAL_ENERGY) * 60 * ESS_DISCHARGE_EFFICIENCY
								/ EV_CHARGE_EFFICIENCY >= ESS_MAX_ENERGY * 60 * 25 / 100) {
			em.model.addExpression("ESS_Schedule_Expr") //
					.set(em.periods[em.periods.length - 1].ess.energy, ONE) //
					.lower(Math.min(ESS_MAX_ENERGY * 60 * 20 / 100, Math.max(0, ESS_INITIAL_ENERGY * 60
							+ (Math.min((pvPowerSum - hhLoadSum) * ESS_CHARGE_EFFICIENCY / 100,
									(pvPowerSum - hhLoadSum) * ESS_DISCHARGE_EFFICIENCY / 100)) * MINUTES_PER_PERIOD
							- (EV_MAX_ENERGY - EV_INITIAL_ENERGY) * 60 * ESS_DISCHARGE_EFFICIENCY / EV_CHARGE_EFFICIENCY
							+ Math.min(0, endOfDayLoad * MINUTES_PER_PERIOD * (ESS_DISCHARGE_EFFICIENCY + 10) / 100))));
			// If the potential energy undercuts 25% * ESS_MAX_ENERGY*60, relax the schedule
			// constraint accordingly
		} else {
			em.model.addExpression("ESS_Schedule_Expr") //
					.set(em.periods[em.periods.length - 1].ess.energy, ONE) //
					.lower(Math.max(0, ESS_INITIAL_ENERGY * 60
							+ (Math.min((pvPowerSum - hhLoadSum) * ESS_CHARGE_EFFICIENCY / 100,
									(pvPowerSum - hhLoadSum) * ESS_DISCHARGE_EFFICIENCY / 100)) * MINUTES_PER_PERIOD
							- (EV_MAX_ENERGY - EV_INITIAL_ENERGY) * 60 * ESS_DISCHARGE_EFFICIENCY / EV_CHARGE_EFFICIENCY
							+ Math.min(0, endOfDayLoad * MINUTES_PER_PERIOD * (ESS_DISCHARGE_EFFICIENCY + 10) / 100)));
		}

		// At the end of the day the EV has to be fully charged.
		em.model.addExpression("EV_Schedule") //
				.set(em.periods[em.periods.length - 1].ev.energy, ONE) //
				.level(EV_MAX_ENERGY * 60);

		// In case of 2 EVs
		// At the end of the day the EVs have to be fully charged.
//		em.model.addExpression("EV0_Schedule") //
//			.set(em.periods[em.periods.length - 1].evs.get(0).energy, ONE) //
//			.lower(EV_MAX_ENERGY*60*10/100);
//		em.model.addExpression("EV1_Schedule") //
//			.set(em.periods[em.periods.length - 1].evs.get(1).energy, ONE) //
//			.level(EV_MAX_ENERGY*60);

	}

}
