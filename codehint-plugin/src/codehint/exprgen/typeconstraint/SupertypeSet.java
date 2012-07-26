package codehint.exprgen.typeconstraint;

import java.util.Set;

import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;

import codehint.exprgen.SubtypeChecker;
import codehint.exprgen.TypeCache;

public class SupertypeSet extends TypeConstraint {
	
	private final Set<IJavaType> supertypes;
	
	public SupertypeSet(Set<IJavaType> supertypes) {
		this.supertypes = supertypes;
	}

	@Override
	public boolean isFulfilledBy(IJavaType type, SubtypeChecker subtypeChecker, TypeCache typeCache, IJavaStackFrame stack, IJavaDebugTarget target) {
		for (IJavaType supertype: supertypes)
			if (subtypeChecker.isSubtypeOf(type, supertype))// || subtypeChecker.isSubtypeOf(supertype, type);
				return true;
		return false;
	}

	@Override
	public IJavaType[] getTypes(IJavaDebugTarget target, TypeCache typeCache) {
		return supertypes.toArray(new IJavaType[supertypes.size()]);
	}

	@Override
	public String toString() {
		return "<=" + supertypes.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((supertypes == null) ? 0 : supertypes.hashCode());
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
		SupertypeSet other = (SupertypeSet) obj;
		if (supertypes == null) {
			if (other.supertypes != null)
				return false;
		} else if (!supertypes.equals(other.supertypes))
			return false;
		return true;
	}

}
