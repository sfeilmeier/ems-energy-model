package io.openems.controller.emsig.ojalgo;

import java.util.stream.IntStream;

public class Constants {

	public final static int NO_OF_PERIODS = 96; // 24 or 96
	public final static int MINUTES_PER_PERIOD = 15; // 60 or 15

	/**
	 * Grid Feed-In Limit, e.g. 70 % law
	 */
	public final static int GRID_SELL_LIMIT = 30000; // [W]

	public final static int GRID_BUY_LIMIT = 30000; // [W]

	public final static int ESS_INITIAL_ENERGY = 5000; // [Wh] originally 6k
	public final static int ESS_MIN_ENERGY = 0; // [Wh]
	public final static int ESS_MAX_ENERGY = 10000; // [Wh], originally 12k
	public final static int ESS_MAX_CHARGE = 5000; // [W]
	public final static int ESS_MAX_DISCHARGE = 5000; // [W]
//	private final static int ESS_EFFICIENCY = 90; // [%, 0-100]
	
	// add PV, assume (for now) constant production
//	public final static int PV_POWER_CONST = 70; // [W]
//	public final static int[] PV_POWER = IntStream.of(new int[NO_OF_PERIODS]).map(i -> PV_POWER_CONST)
//			.toArray();
	
//	public final static int[] PV_POWER = {0, 0, 0, 0, 0, 0, 0, 50, 60, 70, 100, 150, 300, 300, 200, 100, 50, 50, 0, 0, 0, 0, 0, 0}; 

	// add some (household) load which is once again assumed to be constant
//	public final static int HH_LOAD_CONST = 150; // [W]
//	public final static int[] HH_LOAD = IntStream.of(new int[NO_OF_PERIODS]).map(i -> HH_LOAD_CONST)
//			.toArray();
	
	// Fems4, 22.07.21
	public final static int[] PV_POWER = {0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			6,
			246,
			504,
			759,
			969,
			1161,
			1377,
			1655,
			1968,
			2728,
			4035,
			5102,
			7122,
			8826,
			9909,
			11885,
			12889,
			14194,
			15494,
			16664,
			17709,
			18609,
			19517,
			20298,
			21151,
			21851,
			22627,
			23483,
			23614,
			23744,
			24046,
			24047,
			23898,
			24123,
			23773,
			23526,
			23253,
			23077,
			22456,
			22041,
			21509,
			20661,
			19885,
			19001,
			18034,
			17041,
			15849,
			14527,
			12980,
			10395,
			7633,
			5310,
			3472,
			2331,
			1878,
			1538,
			1182,
			834,
			510,
			276,
			34,
			-9,
			-4,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0};
	
	// Fems4, 22.07.2021
	public final static int[] HH_LOAD = {2743,
			2654,
			2693,
			2707,
			2623,
			2642,
			2699,
			2670,
			2640,
			2724,
			2669,
			2630,
			2699,
			2656,
			2644,
			2672,
			2657,
			2748,
			2845,
			3094,
			2955,
			2736,
			2685,
			2373,
			1228,
			912,
			519,
			518,
			930,
			750,
			743,
			606,
			565,
			2282,
			2829,
			3363,
			3065,
			3038,
			919,
			796,
			829,
			789,
			786,
			1411,
			3804,
			3934,
			4165,
			2497,
			1889,
			2152,
			1081,
			868,
			899,
			816,
			865,
			890,
			877,
			916,
			1592,
			3143,
			3126,
			2913,
			4832,
			5522,
			3667,
			3604,
			2857,
			2859,
			3836,
			4054,
			4746,
			3900,
			4292,
			4078,
			4388,
			4645,
			5265,
			6669,
			5219,
			5273,
			5306,
			5269,
			3085,
			2966,
			1653,
			736,
			675,
			725,
			654,
			630,
			457,
			444,
			504,
			434,
			419,
			491};


	

//	
// specify sell revenue and buy cost
	public final static int GRID_SELL_REVENUE_CONST = 10; // [ct]
	public final static int[] GRID_SELL_REVENUE = IntStream.of(new int[NO_OF_PERIODS]).map(i -> GRID_SELL_REVENUE_CONST)
			.toArray();

	public final static int GRID_BUY_COST_CONST = 30; // [ct]
	public final static int[] GRID_BUY_COST = IntStream.of(new int[NO_OF_PERIODS]).map(i -> GRID_BUY_COST_CONST)
			.toArray();
}
