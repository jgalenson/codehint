package codehint.exprgen.typeconstraint;

import org.eclipse.jdt.debug.core.IJavaType;

public abstract class SingleTypeConstraint extends TypeConstraint {
	
	protected final IJavaType typeConstraint;
	
	protected SingleTypeConstraint(IJavaType typeConstraint) {
		this.typeConstraint = typeConstraint;
	}
	
	public IJavaType getTypeConstraint() {
		return typeConstraint;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((typeConstraint == null) ? 0 : typeConstraint.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SingleTypeConstraint other = (SingleTypeConstraint) obj;
		if (typeConstraint == null) {
			if (other.typeConstraint != null)
				return false;
		} else if (!typeConstraint.equals(other.typeConstraint))
			return false;
		return true;
	}

}
