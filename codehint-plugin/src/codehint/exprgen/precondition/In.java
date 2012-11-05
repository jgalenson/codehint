package codehint.exprgen.precondition;

import java.util.ArrayList;

import codehint.expreval.EvaluatedExpression;
import codehint.exprgen.TypedExpression;

public class In extends Predicate {

	private final Value cur;
	private final int targetArgIndex;

	/**
	 * Ensures that the given value is within the bounds of the target.
	 * @param cur The given integer value.
	 * @param targetArgIndex The index of the container, where 0 is the
	 * receiver, 1 is the first argument, etc. 
	 */
	public In(Value cur, int targetArgIndex) {
		this.cur = cur;
		this.targetArgIndex = targetArgIndex;
	}

	@Override
	public boolean satisfies(TypedExpression receiver, ArrayList<EvaluatedExpression> actuals) {
		try {
			int index = cur.getValue(receiver, actuals);
			return 0 <= index && index < Len.getLength(Arg.getJavaValue(targetArgIndex, receiver, actuals).getWrapperValue());
		} catch (NumberFormatException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String toString() {
		return cur + " in bounds of " + targetArgIndex;
	}

}
