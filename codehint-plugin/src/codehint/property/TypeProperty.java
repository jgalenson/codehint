package codehint.property;

import codehint.ast.ASTConverter;
import codehint.ast.Expression;
import org.eclipse.jdt.debug.core.IJavaReferenceType;
import org.eclipse.jdt.debug.core.IJavaType;

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
		Expression rhs = (Expression)ASTConverter.parseExpr(parser, type instanceof IJavaReferenceType ? lhs + " instanceof " + typeName : "true");
		return new TypeProperty(lhs, rhs, typeName, type);
	}
	
	@Override
	public String getTypeName() {
		return typeName;
	}
	
	public IJavaType getType() {
		return type;
	}

	/*@Override
	public boolean meetsSpecification(Expression expression, ExpressionEvaluator expressionEvaluator) {
		try {
			return expressionEvaluator.isSubtypeOf(expressionEvaluator.getValue(expression, Collections.<Effect>emptySet()), type);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}*/

}
