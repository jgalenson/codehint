package codehint.exprgen.precondition;

import java.util.ArrayList;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaValue;

import codehint.ast.Expression;
import codehint.exprgen.ExpressionEvaluator;
import codehint.exprgen.StringValue;

public class Len extends Arg {

	/**
	 * Creates a value representing the length of an argument to the method.
	 * @param index The index of the argument.  0 is the receiver,
	 * 1 the first argument, etc.
	 */
	public Len(int index) {
		super(index);
	}

	@Override
	public int getValue(Expression receiver, ArrayList<Expression> actuals, ExpressionEvaluator expressionEvaluator) {
		return getLength(getJavaValue(index, receiver, actuals, expressionEvaluator));
	}

	/**
	 * Gets the length of the given object.
	 * @param containerWrapper The object whose length we want.
	 * It must be either an array or a String.
	 * @return The length of the given object.
	 */
	public static int getLength(codehint.exprgen.Value containerWrapper) {
		try {
			IJavaValue container = containerWrapper.getValue();
			if (container instanceof IJavaArray)
				return ((IJavaArray)container).getLength();
			else if (containerWrapper instanceof StringValue)
				return ((StringValue) containerWrapper).getStringValue().length();
			throw new IllegalValue();
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

}
