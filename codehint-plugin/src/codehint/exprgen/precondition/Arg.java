package codehint.exprgen.precondition;

import java.util.ArrayList;

import org.eclipse.jdt.debug.core.IJavaValue;

import codehint.expreval.EvaluatedExpression;
import codehint.exprgen.TypedExpression;

/**
 * An argument to the method.
 */
public class Arg extends Value {
	
	private final int index;
	
	/**
	 * Creates a value representing an argument to the method.
	 * @param index The index of the argument.  0 is the receiver,
	 * 1 the first argument, etc.
	 */
	public Arg(int index) {
		this.index = index;
	}

	@Override
	public int getValue(TypedExpression receiver, ArrayList<EvaluatedExpression> partialActuals) {
		return Integer.parseInt(getJavaValue(receiver, partialActuals).toString());
	}
	
	/**
	 * Gets the EvaluatedExpression represented by this argument.
	 * @param index The index of the argument.
	 * @param receiver The receiver of the call.
	 * @param actuals The actuals to the call.
	 * @return The EvaluatedExpression represented by this argument.
	 */
	public static EvaluatedExpression getJavaValue(int index, TypedExpression receiver, ArrayList<EvaluatedExpression> actuals) {
		if (index == 0 && receiver instanceof EvaluatedExpression)
			return ((EvaluatedExpression)receiver);
		else if (index - 1 < actuals.size())
			return actuals.get(index - 1);
		else
			throw new IllegalValue();
	}

	/**
	 * Gets the EvaluatedExpression represented by this argument.
	 * @param receiver The receiver of the call.
	 * @param actuals The actuals to the call.
	 * @return The EvaluatedExpression represented by this argument.
	 */
	protected IJavaValue getJavaValue(TypedExpression receiver, ArrayList<EvaluatedExpression> actuals) {
		return getJavaValue(index, receiver, actuals).getValue();
	}

}
