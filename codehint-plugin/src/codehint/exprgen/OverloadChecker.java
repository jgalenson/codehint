package codehint.exprgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.debug.core.IJavaType;

import com.sun.jdi.Method;

public class OverloadChecker {
	
	private final Map<String, Map<Integer, List<Method>>> methodsByName;
	private boolean[] allHaveSameType;
	
	public OverloadChecker(IJavaType receiverType) {
		List<Method> visibleMethods = ExpressionGenerator.getMethods(receiverType);
		methodsByName = new HashMap<String, Map<Integer, List<Method>>>();
		for (Method method: visibleMethods) {
			String name = method.name();
			if (!methodsByName.containsKey(name))
				methodsByName.put(name, new HashMap<Integer, List<Method>>());
			Map<Integer, List<Method>> methodsWithName = methodsByName.get(name);
			int numArgs = method.argumentTypeNames().size();
			if (!methodsWithName.containsKey(numArgs))
				methodsWithName.put(numArgs, new ArrayList<Method>());
			methodsWithName.get(numArgs).add(method);
		}
	}
	
	public void setMethod(Method method) {
		// TODO: Improve overloading detection.
		List<?> argumentTypeNames = method.argumentTypeNames();
		allHaveSameType = new boolean[argumentTypeNames.size()];
		Arrays.fill(allHaveSameType, true);
		for (Method m : methodsByName.get(method.name()).get(argumentTypeNames.size()))
			for (int i = 0; i < argumentTypeNames.size(); i++)
				if (!m.argumentTypeNames().get(i).equals(argumentTypeNames.get(i)))
					allHaveSameType[i] = false;
	}
	
	public boolean needsCast(IJavaType argType, IJavaType curType, int index) {
		return !allHaveSameType[index] && !argType.equals(curType);
	}

}
