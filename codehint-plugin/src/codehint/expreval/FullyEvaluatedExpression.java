package codehint.expreval;

import org.eclipse.jdt.debug.core.IJavaType;

import codehint.ast.Expression;
import codehint.exprgen.Result;

/**
 * A class that stores an expression, its type,
 * its value, and its toString().
 */
public class FullyEvaluatedExpression extends EvaluatedExpression {
	
	private final String resultString;
	
	public FullyEvaluatedExpression(Expression expression, IJavaType type, Result result, String resultString) {
		super(expression, type, result);
		this.resultString = resultString;
	}
	
	public String getResultString() {
		return resultString;
	}
	
}
