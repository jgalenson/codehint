package codehint.exprgen.precondition;

import java.util.ArrayList;

import codehint.ast.Expression;
import codehint.exprgen.ExpressionEvaluator;

/**
 * Represents a value being passed to a method.
 */
public abstract class Value {
	
	protected static class IllegalValue extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}
	
	/**
	 * Gets the integer value.
	 * @param receiver The receiver of the call.
	 * @param actuals The actuals of the call.
	 * @param expressionEvaluator The expression evaluator.
	 * @return The integer value.
	 */
	public abstract int getValue(Expression receiver, ArrayList<Expression> actuals, ExpressionEvaluator expressionEvaluator);

}
