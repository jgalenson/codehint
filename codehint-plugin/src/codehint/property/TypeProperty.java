package codehint.property;

import org.eclipse.jdt.core.dom.Expression;

import codehint.utils.EclipseUtils;

public class TypeProperty extends LambdaProperty {
	
	private final String typeName;

	protected TypeProperty(String lhs, Expression rhs, String typeName) {
		super(lhs, null, rhs);
		this.typeName = typeName;
	}
	
	public static TypeProperty fromType(String typeName) {
		String lhs = DEFAULT_LHS;
		Expression rhs = (Expression)EclipseUtils.parseExpr(parser, lhs + " instanceof " + typeName);
		return new TypeProperty(lhs, rhs, typeName);
	}
	
	@Override
	public String getTypeName() {
		return typeName;
	}

}
