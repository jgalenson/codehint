package codehint.exprgen;

import java.util.Comparator;

import codehint.ast.Statement;

/**
 * Sorts expressions based on how often their methods and fields
 * appear in our database.
 */
public class ExpressionSorter implements Comparator<Statement> {
	
	private final ExpressionEvaluator expressionEvaluator;
	private final Weights weights;
	
	public ExpressionSorter(ExpressionEvaluator expressionEvaluator, Weights weights) {
		this.expressionEvaluator = expressionEvaluator;
		this.weights = weights;
	}

	@Override
	public int compare(Statement s1, Statement s2) {
		double p1 = ProbabilityComputer.getProbability(s1, expressionEvaluator, weights);
		double p2 = ProbabilityComputer.getProbability(s2, expressionEvaluator, weights);
		//System.out.println(e1 + " " + p1);
		//System.out.println(e2 + " " + p2);
		if (p1 < p2)
			return 1;
		else if (p1 > p2)
			return -1;
		else
			return s1.toString().length() - s2.toString().length();
	}

}
