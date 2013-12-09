package codehint.exprgen.precondition;

import java.util.ArrayList;

import codehint.ast.Expression;
import codehint.exprgen.ExpressionEvaluator;

public class Minus extends Value {
	
	private final Value l;
	private final Value r;

	/**
	 * Creates a value representing the difference of two values.
	 * @param l The left operand.
	 * @param r The right operand.
	 */
	public Minus(Value l, Value r) {
		this.l = l;
		this.r = r;
	}

	@Override
	public int getValue(Expression receiver, ArrayList<Expression> actuals, ExpressionEvaluator expressionEvaluator) {
		return l.getValue(receiver, actuals, expressionEvaluator) - r.getValue(receiver, actuals, expressionEvaluator);
	}

}
