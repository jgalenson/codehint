package codehint.exprgen.precondition;

import java.util.ArrayList;
import java.util.Collections;

import org.eclipse.jdt.debug.core.IJavaValue;

import codehint.ast.Expression;
import codehint.effects.Effect;
import codehint.exprgen.ExpressionMaker;

/**
 * An argument to the method.
 */
public class Arg extends Value {
	
	protected final int index;
	
	/**
	 * Creates a value representing an argument to the method.
	 * @param index The index of the argument.  0 is the receiver,
	 * 1 the first argument, etc.
	 */
	public Arg(int index) {
		this.index = index;
	}

	@Override
	public int getValue(Expression receiver, ArrayList<Expression> partialActuals, ExpressionMaker expressionMaker) {
		return Integer.parseInt(getJavaValue(receiver, partialActuals, expressionMaker).toString());
	}
	
	/**
	 * Gets the value represented by this argument.
	 * @param index The index of the argument.
	 * @param receiver The receiver of the call.
	 * @param actuals The actuals to the call.
	 * @param expressionMaker The expression maker.
	 * @return The value represented by this argument.
	 */
	public static codehint.exprgen.Value getJavaValue(int index, Expression receiver, ArrayList<Expression> actuals, ExpressionMaker expressionMaker) {
		if (index == 0)
			return expressionMaker.getResult(receiver, Collections.<Effect>emptySet()).getValue();
		else if (index - 1 < actuals.size())
			return expressionMaker.getResult(actuals.get(index - 1), Collections.<Effect>emptySet()).getValue();
		else
			throw new IllegalValue();
	}

	/**
	 * Gets the IJavaValue represented by this argument.
	 * @param receiver The receiver of the call.
	 * @param actuals The actuals to the call.
	 * @param expressionMaker The expression maker.
	 * @return The IJavaValue represented by this argument.
	 */
	protected IJavaValue getJavaValue(Expression receiver, ArrayList<Expression> actuals, ExpressionMaker expressionMaker) {
		return getJavaValue(index, receiver, actuals, expressionMaker).getValue();
	}

}
