package codehint.exprgen.typeconstraint;

import java.util.Set;

import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaType;

import codehint.exprgen.SubtypeChecker;

public class SupertypeSet extends TypeConstraint {
	
	private final Set<IJavaType> supertypes;
	
	public SupertypeSet(Set<IJavaType> supertypes) {
		this.supertypes = supertypes;
	}

	@Override
	public boolean isFulfilledBy(IJavaType type, SubtypeChecker subtypeChecker, IJavaDebugTarget target) {
		for (IJavaType supertype: supertypes)
			if (subtypeChecker.isSubtypeOf(type, supertype))// || subtypeChecker.isSubtypeOf(supertype, type);
				return true;
		return false;
	}

	@Override
	public IJavaType[] getTypes(IJavaDebugTarget target) {
		return supertypes.toArray(new IJavaType[supertypes.size()]);
	}

	@Override
	public String toString() {
		return "<=" + supertypes.toString();
	}

}
