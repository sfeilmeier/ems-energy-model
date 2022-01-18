import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Variable;

import java.util.ArrayList;
import java.util.List;

import org.ojalgo.matrix.store.Primitive64Store;
import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.Optimisation.Result;


public class test {
	  public static void main(String[] args) {

	        ExpressionsBasedModel model = new ExpressionsBasedModel();
	        Variable x = model.addVariable("x") //
	        		.lower(-10) //
	        		.upper(10);
	        Variable y = model.addVariable("y") //
	        		.lower(-10) //
	        		.upper(10);
	        Variable isX = model.addVariable("isX")
	        		.lower(0) //
	        		.upper(1);
	        Variable isY = model.addVariable("isY") //
	        		.lower(0) //
	        		.upper(1);
	        
//			model.addExpression("Some Constraint")
//			.set(x, 1.0) //
//			.set(y, 1.0)
//			.level(1);
	        
	       // Define the function f(x,y) = x^2 + y^2 and find the global maximum/minimum
	        List<Variable> variables = new ArrayList<>();
	        variables.add(x);
	        variables.add(y);
	        
	        Primitive64Store identity = Primitive64Store.FACTORY.rows(new double[2][2]);
		for (int i = 0; i < 2; i++) {
				identity.add(i, i, 1);
			};
			
			Expression objective = model.addExpression("Objective");
			objective.setQuadraticFactors(variables, identity);
			objective.weight(1);
			
			model.addExpression("isX XOR isY") //
				.set(isX, 1.0) //
				.set(isY, 1.0) //
				.level(1);
	        
			// Define the function f(x,y) = 2xy and find the global maximum/minimum
//			Primitive64Store antidiagonal = Primitive64Store.FACTORY.rows(new double[2][2]);
//			antidiagonal.add(0,1, 2);
//			antidiagonal.add(1,0,2);
			
			
			
			
//			Expression objective2 = model.addExpression("Objective2");
//			objective2.setQuadraticFactors(variables, antidiagonal);
//			objective2.weight(1);
		
			Expression objective3 = model.addExpression("Objective3");
			objective3.set(y, isX, 1.0); //
			objective3.set(x, isY, 1.0);
			objective3.weight(1);
				
			
			
//	       model.addExpression("objective")
//	        // Add the x^2 term
//	        .set(x, x, 1.0) 
//	        .set(y, y, 1.0)
//	        // Add the -8x term
//	        //.set(x, -8.0)
//	        // -> Objective
//	        .weight(1.0);

			// Add some constraint concerning the variables x and y

	        // Result result = model.minimise();
	         Result result = model.maximise();



	        System.out.println("The state: " + result.getState());
	        System.out.println("The full result: " + result);
	        System.out.println(x);
	        System.out.println(y);
	        System.out.println(isX);
	        System.out.println(isY);
	  }
}
