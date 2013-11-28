package codehint.property;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaValue;

import codehint.ast.ASTConverter;
import codehint.ast.Expression;

public class ArrayValueProperty extends ValueProperty {

	protected ArrayValueProperty(String lhs, Expression rhs, String valueString, IJavaValue value) {
		super(lhs, rhs, valueString, value);
	}
	
	public static ArrayValueProperty fromArray(String expr, IJavaValue value) {
		try {
			String lhs = DEFAULT_LHS;
			Expression rhs = (Expression)ASTConverter.parseExpr(parser, "java.util.Arrays.equals((" + value.getJavaType().getName() + ")" + lhs + ", " + expr + ")");
			((IJavaObject)value).disableCollection();
			return new ArrayValueProperty(lhs, rhs, expr, value);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

}
