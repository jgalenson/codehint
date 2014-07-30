package codehint.exprgen;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaArrayType;
import org.eclipse.jdt.debug.core.IJavaClassType;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaInterfaceType;
import org.eclipse.jdt.debug.core.IJavaReferenceType;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;

import codehint.utils.EclipseUtils;

import com.sun.jdi.ClassNotLoadedException;

/**
 * A class that can compute subtype checks and
 * caches the results for efficiency.
 */
public class SubtypeChecker {
	
	private final Map<IJavaType, Set<IJavaType>> supertypesMap;
	private final IJavaStackFrame stack;
	private final IJavaDebugTarget target;
	private final TypeCache typeCache;
	private final Map<String, Set<String>> supertypes;
	
	public SubtypeChecker(IJavaStackFrame stack, IJavaDebugTarget target, TypeCache typeCache) {
		this.supertypesMap = new HashMap<IJavaType, Set<IJavaType>>();
		this.stack = stack;
		this.target = target;
		this.typeCache = typeCache;
		try {
			ObjectInputStream is = new ObjectInputStream(new GZIPInputStream(EclipseUtils.getFileFromBundle("data" + System.getProperty("file.separator") + "supertypes.gz")));
			supertypes = (Map<String, Set<String>>)is.readObject();
			is.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
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
				return getSupertypes(cur).contains(expected);
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
	
	/**
	 * Adds all of the super interfaces of the given interface
	 * type to the given set.
	 * @param t The type whose super interfaces we want.
	 * @param supertypes The set into which we store the super interfaces.
	 * @throws DebugException
	 */
	private static void addSuperInterfaces(IJavaInterfaceType t, Set<IJavaType> supertypes) throws DebugException {
		supertypes.add(t);
		for (IJavaInterfaceType child : t.getSuperInterfaces())
			addSuperInterfaces(child, supertypes);
	}
	
	/**
	 * Gets the supertypes of the given type.
	 * @param type The type to check.
	 * @return The supertypes of the given type.
	 */
	public Set<IJavaType> getSupertypes(IJavaType type) {
		try {
			if (type instanceof IJavaArrayType) {
				Set<IJavaType> result = new HashSet<IJavaType>();
				for (IJavaType parentElemType: getSupertypes(((IJavaArrayType)type).getComponentType()))
					result.add(EclipseUtils.getFullyQualifiedType(parentElemType.getName() + "[]", stack, target, typeCache));
				result.add(EclipseUtils.getFullyQualifiedType("java.lang.Object", stack, target, typeCache));
				return result;
			} else if (type instanceof IJavaReferenceType) {
				if (!supertypesMap.containsKey(type)) {
					Set<IJavaType> supertypes = new HashSet<IJavaType>();
					if (type instanceof IJavaInterfaceType) {
						addSuperInterfaces((IJavaInterfaceType)type, supertypes);
					} else if (type instanceof IJavaClassType) {
						for (IJavaClassType t = (IJavaClassType)type; t != null; t = t.getSuperclass())
							supertypes.add(t);
						for (IJavaType t : ((IJavaClassType)type).getAllInterfaces())
							supertypes.add(t);
					} else
						assert false;
					supertypesMap.put(type, supertypes);
				}
				return supertypesMap.get(type);
			} else {
				return Collections.singleton(type);
			}
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}
	
	enum StringSubtype { SUBTYPE, NOT_SUBTYPE, UNKNOWN }
	
	/**
	 * Checks whether the first string is a type that is a subtype
	 * of the second string.
	 * @param cur The name of the type to check.
	 * @param expected The name of the expected type.
	 * @return Whether the first string is the name of a subtype of the second.
	 */
	public StringSubtype stringIsSubtypeOf(String cur, String expected) {
		if (cur.equals(expected) || expected.equals("java.lang.Object"))
			return StringSubtype.SUBTYPE;
		while (expected.endsWith("[]")) {
			if (!cur.endsWith("[]"))
				return StringSubtype.NOT_SUBTYPE;
			cur = cur.substring(0, cur.length() - 2);
			expected = expected.substring(0, expected.length() - 2);
		}
		if (cur.endsWith("[]"))
			return StringSubtype.NOT_SUBTYPE;
		Set<String> curSupertypes = supertypes.get(cur.replace('$', '.'));
		if (curSupertypes == null)
			return StringSubtype.UNKNOWN;
		else if (curSupertypes.contains(expected.replace('$', '.')))
			return StringSubtype.SUBTYPE;
		else
			return StringSubtype.NOT_SUBTYPE;
	}

}
