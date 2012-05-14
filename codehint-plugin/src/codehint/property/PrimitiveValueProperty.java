package codehint.property;

import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.debug.core.IJavaValue;

import codehint.EclipseUtils;

public class PrimitiveValueProperty extends ValueProperty {

	protected PrimitiveValueProperty(String lhs, Expression rhs, IJavaValue value) {
		super(lhs, rhs, value);
	}

	public static PrimitiveValueProperty fromPrimitive(String valueString, IJavaValue value) {
		String lhs = DEFAULT_LHS;
		Expression rhs = (Expression)EclipseUtils.parseExpr(parser, lhs + " == " + valueString);
		return new PrimitiveValueProperty(lhs, rhs, value);
	}

}
