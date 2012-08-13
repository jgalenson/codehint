package codehint.exprgen;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.debug.core.IJavaType;

/**
 * A cache that stores type name to type mappings.
 * This is primarily used for efficiency, as we often
 * try to get the type for the same name many times.
 * We also cache types that are illegal and cannot be
 * used.  Eclipse either gives us a compile error when
 * we use these or else crashes when we try to evaluate
 * code that uses them.
 */
public class TypeCache {
	
	private final Map<String, IJavaType> typeCache;
	private final Set<String> illegalTypes;
	
	public TypeCache() {
		typeCache = new HashMap<String, IJavaType>();
		illegalTypes = new HashSet<String>();
	}
	
	/**
	 * Gets the cached type with the given name,
	 * or null if we have not already gotten this type.
	 * @param typeName The name of the type to get. 
	 * @return The type with the given name, or null
	 * if we have not already gotten that type.
	 */
	public IJavaType get(String typeName) {
		return typeCache.get(typeName);
	}
	
	/**
	 * Adds a name-type mapping.
	 * @param typeName The name of the type.
	 * @param type The type itself.
	 */
	public void add(String typeName, IJavaType type) {
		typeCache.put(typeName, type);
	}
	
	/**
	 * Mark the given type name as illegal.
	 * @param typeName The name of the illegal type.
	 */
	public void markIllegal(String typeName) {
		illegalTypes.add(typeName);
	}
	
	/**
	 * Checks whether the given type name is illegal.
	 * @param typeName The name of the illegal type.
	 * @return Whether the given type name is known
	 * to be illegal.
	 */
	public boolean isIllegal(String typeName) {
		return illegalTypes.contains(typeName);
	}

}
