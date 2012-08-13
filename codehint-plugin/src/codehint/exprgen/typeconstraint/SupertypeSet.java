package codehint.exprgen.typeconstraint;

import java.util.Set;

import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;

import codehint.exprgen.SubtypeChecker;
import codehint.exprgen.TypeCache;

/**
 * A type constraint that matches subtypes of any of the given types.
 */
public class SupertypeSet extends SetTypeConstraint {

	/**
	 * Creates a constraint that matches subtypes of any of the given types.
	 * @param supertypes The constraint's types.
	 */
	public SupertypeSet(Set<IJavaType> supertypes) {
		super(supertypes);
	}

	@Override
	public boolean isFulfilledBy(IJavaType type, SubtypeChecker subtypeChecker, TypeCache typeCache, IJavaStackFrame stack, IJavaDebugTarget target) {
		for (IJavaType supertype: typeSet)
			if (subtypeChecker.isSubtypeOf(type, supertype))// || subtypeChecker.isSubtypeOf(supertype, type);
				return true;
		return false;
	}

	@Override
	public String toString() {
		return "<=" + typeSet.toString();
	}

}
