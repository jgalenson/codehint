package codehint;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.swt.widgets.Shell;

public class DemonstrateTypeHandler extends CommandHandler {
    
    @Override
	public void handle(IVariable variable, String path, Shell shell) {
		if (EclipseUtils.isPrimitive(variable))  // TODO: Disable the menu in this case.
			EclipseUtils.showError("Cannot demonstrate type", "You cannot demonstrate the type of a primitive variable.", null);
    	try {
	    	String initValue = ((IJavaVariable)variable).getJavaType().getName();
	    	String typeName = EclipseUtils.getType(path, shell, initValue, null, EclipseUtils.getStackFrame());
	    	if (typeName != null) {
	        	Property property = Property.fromType(typeName);
	        	Synthesizer.synthesizeAndInsertExpressions(variable, path, property, null, shell);
	    	}
    	}  catch (DebugException e) {
			e.printStackTrace();
		}
    }

}
