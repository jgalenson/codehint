package codehint.property;

import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.debug.core.IJavaType;

import codehint.utils.EclipseUtils;

public class TypeProperty extends LambdaProperty {
	
	private final String typeName;
	private final IJavaType type;

	protected TypeProperty(String lhs, Expression rhs, String typeName, IJavaType type) {
		super(lhs, null, rhs);
		this.typeName = typeName;
		this.type = type;
	}
	
	public static TypeProperty fromType(String typeName, IJavaType type) {
		String lhs = DEFAULT_LHS;
		Expression rhs = (Expression)EclipseUtils.parseExpr(parser, lhs + " instanceof " + typeName);
		return new TypeProperty(lhs, rhs, typeName, type);
	}
	
	@Override
	public String getTypeName() {
		return typeName;
	}
	
	public IJavaType getType() {
		return type;
	}

}
