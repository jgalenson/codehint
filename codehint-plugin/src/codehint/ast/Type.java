package codehint.ast;

import org.eclipse.jdt.debug.core.IJavaType;

public abstract class Type extends ASTNode {
	
	private IJavaType staticType;
	
	protected Type(IJavaType staticType) {
		this.staticType = staticType;
	}
	
	public IJavaType getStaticType() {
		return staticType;
	}
	
	public void setStaticType(IJavaType staticType) {
		if (this.staticType != null)
			throw new RuntimeException("Cannot reset the static type.");
		this.staticType = staticType;
	}

}
