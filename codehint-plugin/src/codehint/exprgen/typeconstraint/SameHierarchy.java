package codehint.exprgen.typeconstraint;

import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;

import codehint.exprgen.SubtypeChecker;
import codehint.exprgen.TypeCache;

public class SameHierarchy extends TypeConstraint {
	
	private final IJavaType targetType;
	
	public SameHierarchy(IJavaType targetType) {
		this.targetType = targetType;
	}

	@Override
	public boolean isFulfilledBy(IJavaType type, SubtypeChecker subtypeChecker, TypeCache typeCache, IJavaStackFrame stack, IJavaDebugTarget target) {
		return subtypeChecker.isSubtypeOf(type, targetType) || subtypeChecker.isSubtypeOf(targetType, type);
	}

	@Override
	public IJavaType[] getTypes(IJavaStackFrame stack, IJavaDebugTarget target, TypeCache typeCache) {
		return new IJavaType[] { targetType };
	}

	@Override
	public String toString() {
		return "comparable to " + targetType.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((targetType == null) ? 0 : targetType.hashCode());
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
		SameHierarchy other = (SameHierarchy) obj;
		if (targetType == null) {
			if (other.targetType != null)
				return false;
		} else if (!targetType.equals(other.targetType))
			return false;
		return true;
	}

}
