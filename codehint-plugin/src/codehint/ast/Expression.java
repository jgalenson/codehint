package codehint.ast;

import org.eclipse.jdt.debug.core.IJavaType;

public abstract class Expression extends Statement {
	
	private IJavaType staticType;
	
	protected Expression(IJavaType staticType) {
		this.staticType = staticType;
	}
	
	public IJavaType getStaticType() {
		return staticType;
	}
	
	public void setStaticType(IJavaType staticType) {
		if (this.staticType != null)
			throw new RuntimeException("Cannot reset the static type of " + this.toString() + " from " + this.staticType + " to " + staticType);
		this.staticType = staticType;
	}
	
	/*public void resetStaticType(IJavaType staticType) {
		this.staticType = staticType;
	}*/

}
