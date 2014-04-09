package codehint.property;

import org.eclipse.jdt.debug.core.IJavaStackFrame;

public abstract class Property {

	public abstract String getReplacedString(String arg, IJavaStackFrame stack);
	
	public abstract boolean usesLHS();

	/*public abstract boolean meetsSpecification(Expression expression, ExpressionEvaluator expressionEvaluator);
	
	public final ArrayList<Expression> meetSpecification(ArrayList<Expression> expressions, ExpressionEvaluator expressionEvaluator) {
		ArrayList<Expression> results = new ArrayList<Expression>();
		for (Expression expr: expressions)
			if (meetsSpecification(expr, expressionEvaluator))
				results.add(expr);
		return results;
	}*/
	
}
