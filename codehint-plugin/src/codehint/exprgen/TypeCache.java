package codehint.exprgen;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.debug.core.IJavaType;

public class TypeCache {
	
	private final Map<String, IJavaType> typeCache;
	
	public TypeCache() {
		typeCache = new HashMap<String, IJavaType>();
	}
	
	public IJavaType get(String typeName) {
		return typeCache.get(typeName);
	}
	
	public void add(String typeName, IJavaType type) {
		typeCache.put(typeName, type);
	}

}
