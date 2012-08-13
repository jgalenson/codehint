package codehint.exprgen.typeconstraint;

import java.util.Set;

import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;

import codehint.exprgen.TypeCache;

/**
 * A constraint on the receivers of methods calls or field accesses.
 */
public abstract class NameConstraint extends TypeConstraint {
	
	protected Set<String> legalNames;
	
	/**
	 * Creates a constraint on receiving objects.
	 */
	protected NameConstraint() {
		this.legalNames = null;
	}

	/**
	 * Sets the legal names of the desired methods or fields.
	 * @param legalNames the legal names of the desired methods or fields.
	 */
	public void setLegalNames(Set<String> legalNames) {
		this.legalNames = legalNames;
	}

	@Override
	public IJavaType[] getTypes(IJavaStackFrame stack, IJavaDebugTarget target, TypeCache typeCache) {
		throw new RuntimeException("Cannot get the types of a name constraint.");
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((legalNames == null) ? 0 : legalNames.hashCode());
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
		NameConstraint other = (NameConstraint) obj;
		if (legalNames == null) {
			if (other.legalNames != null)
				return false;
		} else if (!legalNames.equals(other.legalNames))
			return false;
		return true;
	}

}
