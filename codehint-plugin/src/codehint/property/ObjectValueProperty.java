package codehint.property;

import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.debug.core.IJavaValue;

import codehint.EclipseUtils;

public class ObjectValueProperty extends ValueProperty {

	protected ObjectValueProperty(String lhs, Expression rhs, IJavaValue value) {
		super(lhs, rhs, value);
	}
	
	public static ObjectValueProperty fromObject(String expr, IJavaValue value) {
		String lhs = DEFAULT_LHS;
		Expression rhs = (Expression)EclipseUtils.parseExpr(parser, lhs + " == null ? " + expr + " == null : " + lhs + ".equals(" + expr + ")");
		return new ObjectValueProperty(lhs, rhs, value);
	}

}
