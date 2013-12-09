package codehint.exprgen.precondition;

import java.util.ArrayList;

import codehint.ast.Expression;
import codehint.exprgen.ExpressionEvaluator;

public class GT extends Predicate {
	
	private final Value cur;
	private final Value target;

	/**
	 * Ensures that the first value is greater than the second.
	 * @param cur The first value.
	 * @param target The second value.
	 */
	public GT(Value cur, Value target) {
		this.cur = cur;
		this.target = target;
	}

	@Override
	public boolean satisfies(Expression receiver, ArrayList<Expression> actuals, ExpressionEvaluator expressionEvaluator) {
		return cur.getValue(receiver, actuals, expressionEvaluator) > target.getValue(receiver, actuals, expressionEvaluator);
	}

	@Override
	public String toString() {
		return cur + " > " + target;
	}

}
