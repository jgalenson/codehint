package codehint.exprgen.precondition;

import java.util.ArrayList;

import codehint.ast.Expression;
import codehint.exprgen.ExpressionMaker;

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
	public boolean satisfies(Expression receiver, ArrayList<Expression> actuals, ExpressionMaker expressionMaker) {
		return cur.getValue(receiver, actuals, expressionMaker) > target.getValue(receiver, actuals, expressionMaker);
	}

	@Override
	public String toString() {
		return cur + " > " + target;
	}

}
