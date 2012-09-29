package codehint.exprgen.precondition;

import java.util.ArrayList;

import codehint.expreval.EvaluatedExpression;
import codehint.exprgen.TypedExpression;

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
	public int getValue(TypedExpression receiver, ArrayList<EvaluatedExpression> actuals) {
		return l.getValue(receiver, actuals) - r.getValue(receiver, actuals);
	}

}
