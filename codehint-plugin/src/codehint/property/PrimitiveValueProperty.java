package codehint.property;

import org.eclipse.jdt.debug.core.IJavaValue;

import codehint.ast.ASTConverter;
import codehint.ast.Expression;

public class PrimitiveValueProperty extends ValueProperty {

	protected PrimitiveValueProperty(String lhs, Expression rhs, String valueString, IJavaValue value) {
		super(lhs, rhs, valueString, value);
	}

	public static PrimitiveValueProperty fromPrimitive(String valueString, IJavaValue value) {
		String lhs = DEFAULT_LHS;
		Expression rhs = (Expression)ASTConverter.parseExpr(parser, lhs + " == " + valueString);
		return new PrimitiveValueProperty(lhs, rhs, valueString, value);
	}

}
