package codehint.exprgen.precondition;

import java.util.ArrayList;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaValue;

import codehint.expreval.EvaluatedExpression;
import codehint.exprgen.TypedExpression;

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
	public int getValue(TypedExpression receiver, ArrayList<EvaluatedExpression> actuals) {
		return getLength(getJavaValue(receiver, actuals));
	}

	/**
	 * Gets the length of the given object.
	 * @param container The object whose length we want.
	 * It must be either an array or a String.
	 * @return The length of the given object.
	 */
	public static int getLength(IJavaValue container) {
		try {
			if (container instanceof IJavaArray)
				return ((IJavaArray)container).getLength();
			else if (container instanceof IJavaObject)
				if ("java.lang.String".equals(((IJavaObject)container).getJavaType().toString()))
					return container.getValueString().length();  // toString returns it with the quotes, getValueString returns it without them.
			throw new IllegalValue();
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

}
