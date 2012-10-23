package codehint.property;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaValue;

import codehint.utils.EclipseUtils;

public class ObjectValueProperty extends ValueProperty {

	protected ObjectValueProperty(String lhs, Expression rhs, String valueString, IJavaValue value) {
		super(lhs, rhs, valueString, value);
	}
	
	public static ObjectValueProperty fromObject(String expr, IJavaValue value) throws DebugException {
		String lhs = DEFAULT_LHS;
		Expression rhs = (Expression)EclipseUtils.parseExpr(parser, lhs + " == null ? " + expr + " == null : " + lhs + ".equals(" + expr + ")");
		((IJavaObject)value).disableCollection();
		return new ObjectValueProperty(lhs, rhs, expr, value);
	}

}
