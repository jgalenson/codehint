package codehint.exprgen.precondition;

import java.util.ArrayList;

import codehint.expreval.EvaluatedExpression;
import codehint.exprgen.TypedExpression;

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
	 * @return The integer value.
	 */
	public abstract int getValue(TypedExpression receiver, ArrayList<EvaluatedExpression> actuals);

}
