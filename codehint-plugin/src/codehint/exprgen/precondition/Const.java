package codehint.exprgen.precondition;

import java.util.ArrayList;

import codehint.expreval.EvaluatedExpression;
import codehint.exprgen.TypedExpression;

public class Const extends Value {
	
	private final int val;

	/**
	 * Creates a constant value.
	 * @param index The constant.
	 */
	public Const(int val) {
		this.val = val;
	}

	@Override
	public int getValue(TypedExpression receiver, ArrayList<EvaluatedExpression> actuals) {
		return val;
	}

}
