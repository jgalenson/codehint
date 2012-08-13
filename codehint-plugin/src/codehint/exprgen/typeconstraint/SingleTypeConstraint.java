package codehint.exprgen.typeconstraint;

import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;

import codehint.exprgen.TypeCache;

/**
 * An abstract type constraint for constraints that
 * represent some relationship with a single type. 
 */
public abstract class SingleTypeConstraint extends TypeConstraint {
	
	protected final IJavaType typeConstraint;
	
	/**
	 * Creates a type constraint with the given type.
	 * @param typeConstraint The type of this constraint.
	 */
	protected SingleTypeConstraint(IJavaType typeConstraint) {
		this.typeConstraint = typeConstraint;
	}
	
	/**
	 * Gets this constraint's type.
	 * @return The type of this constraint.
	 */
	public IJavaType getTypeConstraint() {
		return typeConstraint;
	}

	@Override
	public IJavaType[] getTypes(IJavaStackFrame stack, IJavaDebugTarget target, TypeCache typeCache) {
		return new IJavaType[] { typeConstraint };
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
