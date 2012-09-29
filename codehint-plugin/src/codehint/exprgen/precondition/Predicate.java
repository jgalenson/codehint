package codehint.exprgen.precondition;

import java.util.ArrayList;

import codehint.expreval.EvaluatedExpression;
import codehint.exprgen.TypedExpression;

/**
 * A predicate that represents a precondition to
 * a method call.
 * TODO: These should be more complex and we should
 * infer them from a static analysis. 
 */
public abstract class Predicate {
	
	/**
	 * Ensures that the given predicate is satisfied
	 * by a call with the given receiver and arguments.
	 * @param receiver The receiver.
	 * @param actuals The actuals.
	 * @return Whether the given call satisfies this predicate.
	 */
	public abstract boolean satisfies(TypedExpression receiver, ArrayList<EvaluatedExpression> actuals);

}
