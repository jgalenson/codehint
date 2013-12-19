package codehint.exprgen.weightedlist;

import codehint.ast.Expression;
import codehint.exprgen.ExpressionEvaluator;
import codehint.exprgen.ProbabilityComputer;
import codehint.exprgen.Weights;

public class ExpressionWeightedList extends WeightedList<Expression> {
	
	private final ExpressionEvaluator expressionEvaluator;
	private final Weights weights;
	
	public ExpressionWeightedList(ExpressionEvaluator expressionEvaluator, Weights weights) {
		this.expressionEvaluator = expressionEvaluator;
		this.weights = weights;
	}

	@Override
	public double getWeight(Expression expr) {
		return ProbabilityComputer.getNormalizedProbability(expr, expressionEvaluator, weights);
	}

}
