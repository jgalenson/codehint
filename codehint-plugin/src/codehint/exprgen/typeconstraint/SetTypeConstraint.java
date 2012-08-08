package codehint.exprgen.typeconstraint;

import java.util.Set;

import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;

import codehint.exprgen.TypeCache;

public abstract class SetTypeConstraint extends TypeConstraint {
	
	protected final Set<IJavaType> typeSet;
	
	public SetTypeConstraint(Set<IJavaType> typeSet) {
		this.typeSet = typeSet;
	}
	
	public Set<IJavaType> getTypeSet() {
		return typeSet;
	}

	@Override
	public IJavaType[] getTypes(IJavaStackFrame stack, IJavaDebugTarget target, TypeCache typeCache) {
		return typeSet.toArray(new IJavaType[typeSet.size()]);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((typeSet == null) ? 0 : typeSet.hashCode());
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
		SetTypeConstraint other = (SetTypeConstraint) obj;
		if (typeSet == null) {
			if (other.typeSet != null)
				return false;
		} else if (!typeSet.equals(other.typeSet))
			return false;
		return true;
	}

}
