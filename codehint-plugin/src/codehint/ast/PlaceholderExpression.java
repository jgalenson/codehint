package codehint.ast;

import org.eclipse.jdt.debug.core.IJavaType;

/**
 * This class represents a fake expression.
 */
public class PlaceholderExpression extends Expression {

	public PlaceholderExpression(IJavaType staticType) {
		super(staticType);
	}

	@Override
	protected void accept0(ASTVisitor visitor) {
	}

}
