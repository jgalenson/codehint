package codehint.ast;

import org.eclipse.jdt.debug.core.IJavaType;

public abstract class Name extends Expression {
	
	protected Name(IJavaType staticType) {
		super(staticType);
	}

}
