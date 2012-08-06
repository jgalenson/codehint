package codehint.exprgen;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.debug.core.IJavaType;

public class TypeCache {
	
	private final Map<String, IJavaType> typeCache;
	private final Set<String> illegalTypes;
	
	public TypeCache() {
		typeCache = new HashMap<String, IJavaType>();
		illegalTypes = new HashSet<String>();
	}
	
	public IJavaType get(String typeName) {
		return typeCache.get(typeName);
	}
	
	public void add(String typeName, IJavaType type) {
		typeCache.put(typeName, type);
	}
	
	public void markIllegal(String typeName) {
		illegalTypes.add(typeName);
	}
	
	public boolean isIllegal(String typeName) {
		return illegalTypes.contains(typeName);
	}

}
