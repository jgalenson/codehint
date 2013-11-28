package codehint.ast;

import org.eclipse.jdt.debug.core.IJavaType;

public abstract class NumberLiteral extends Expression {
	
	protected NumberLiteral(IJavaType staticType) {
		super(staticType);
	}

}
