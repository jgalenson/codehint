package codehint.exprgen;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaArrayType;
import org.eclipse.jdt.debug.core.IJavaClassType;
import org.eclipse.jdt.debug.core.IJavaInterfaceType;
import org.eclipse.jdt.debug.core.IJavaReferenceType;
import org.eclipse.jdt.debug.core.IJavaType;

import com.sun.jdi.ClassNotLoadedException;

/**
 * A class that can compute subtype checks and
 * caches the results for efficiency.
 */
public class SubtypeChecker {
	
	private final Map<IJavaType, Set<IJavaType>> supertypesMap;
	
	public SubtypeChecker() {
		this.supertypesMap = new HashMap<IJavaType, Set<IJavaType>>();
	}
	
	/**
	 * Checks whether one type is a subtype of another.
	 * Note that this could use the Eclipse ITypeHierarchy API,
	 * but that uses ITypes and not IJavaTypes, which are
	 * incompatible, so this seems easier.
	 * @param cur Type to check.
	 * @param expected Expected type.
	 * @return Whether cur is a subtype of expected.
	 */
	public boolean isSubtypeOf(IJavaType cur, IJavaType expected) {
		try {
			// If one of the types is null, the other can be any reference type.
			if (cur == null)
				return expected == null || expected instanceof IJavaReferenceType;
			if (expected == null)
				return cur instanceof IJavaReferenceType;
			if (cur.equals(expected))
				return true;
			// Check arrays.
			if (cur instanceof IJavaArrayType) {
				if ("java.lang.Object".equals(expected.getName()))
					return true;  // Arrays are subtypes of Object.
				if (!(expected instanceof IJavaArrayType))
					return false;
				IJavaType curElemType = ((IJavaArrayType)cur).getComponentType();
				IJavaType expectedElemType = ((IJavaArrayType)expected).getComponentType();
				return isSubtypeOf(curElemType, expectedElemType);
			}
			// Check objects and interfaces.
			if (cur instanceof IJavaReferenceType) {
				if ("java.lang.Object".equals(expected.getName()))  // Shortcut a common case.  Also, this ensures that interfaces are subtypes of Object.
					return true;
				// Compute supertypes and add to cache if not there.
				if (!supertypesMap.containsKey(cur)) {
					Set<IJavaType> supertypes = new HashSet<IJavaType>();
					supertypes.add(cur);
					if (cur instanceof IJavaInterfaceType) {
						for (IJavaType t : ((IJavaInterfaceType)cur).getSuperInterfaces())
							supertypes.add(t);
					} else if (cur instanceof IJavaClassType) {
						for (IJavaClassType parent = ((IJavaClassType)cur).getSuperclass(); parent != null; parent = parent.getSuperclass())
							supertypes.add(parent);
						for (IJavaType t : ((IJavaClassType)cur).getAllInterfaces())
							supertypes.add(t);
					} else
						assert false;
					supertypesMap.put(cur, supertypes);
				}
				return supertypesMap.get(cur).contains(expected);
			} else
				return false;  // We already checked if the two types were .equal.
		} catch (DebugException e) {
			if (e.getCause() instanceof ClassNotLoadedException) {
				//System.err.println("I cannot get the class of one of the types " + cur.getName() + " and " + expected.getName());
				return true;
			} else
				throw new RuntimeException(e);
		}
	}

}
