package codehint.handler;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jface.viewers.IStructuredSelection;

import codehint.utils.EclipseUtils;

public class CodeHintPropertyTester extends PropertyTester {

	@Override
	public boolean test(Object receiver, String property, Object[] args, Object expectedValue) {
		if (receiver instanceof IStructuredSelection) {
			Object firstElement = ((IStructuredSelection)receiver).getFirstElement();
			if (firstElement instanceof IVariable)
				return !EclipseUtils.isPrimitive((IVariable)firstElement);
			else
				return false;
		} else
			return false;
	}

}
