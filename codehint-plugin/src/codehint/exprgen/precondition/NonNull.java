package codehint.exprgen.precondition;

import java.util.ArrayList;

import codehint.expreval.EvaluatedExpression;
import codehint.exprgen.TypedExpression;

public class NonNull extends Predicate {
	
	private final int argIndex;

	/**
	 * Ensures that the given argument is non-null.
	 * @param argIndex The argument, where 1 is the first argument, not 0.
	 */
	public NonNull(int argIndex) {
		this.argIndex = argIndex;
	}

	@Override
	public boolean satisfies(TypedExpression receiver, ArrayList<EvaluatedExpression> actuals) {
		return !Arg.getJavaValue(argIndex, receiver, actuals).getValue().isNull();
	}

	/**
	 * Gets the index of the argument that must be non-null.
	 * @return The index that must be non-null, where 1 is
	 * the first argument, not 0.
	 */
	public int getArgIndex() {
		return argIndex;
	}

	@Override
	public String toString() {
		return argIndex + " non-null";
	}

}
