package codehint.exprgen.typeconstraint;

import java.util.Set;

import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;

import codehint.exprgen.SubtypeChecker;
import codehint.exprgen.TypeCache;

/**
 * A type constraint that matches supertypes of any of the given types.
 */
public class SubtypeSet extends SetTypeConstraint {

	/**
	 * Creates a constraint that matches supertypes of any of the given types.
	 * @param subtypes The constraint's types.
	 */
	public SubtypeSet(Set<IJavaType> subtypes) {
		super(subtypes);
	}

	@Override
	public boolean isFulfilledBy(IJavaType type, SubtypeChecker subtypeChecker, TypeCache typeCache, IJavaStackFrame stack, IJavaDebugTarget target) {
		for (IJavaType subtype: typeSet)
			if (subtypeChecker.isSubtypeOf(subtype, type))// || subtypeChecker.isSubtypeOf(supertype, type);
				return true;
		return false;
	}

	@Override
	public String toString() {
		return ">=" + typeSet.toString();
	}

}
