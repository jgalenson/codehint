package codehint;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.swt.widgets.Shell;

public class DemonstrateTypeHandler extends CommandHandler {

    private final static Pattern pattern = Pattern.compile("\\s*CodeHint.<(.*)>type\\((\\w*)\\);\\s*\\r?\\n\\s*");
    
    @Override
	public void handle(IVariable variable, String path, Shell shell) {
		if (EclipseUtils.isPrimitive(variable)) {  // TODO: Disable the menu in this case.
			EclipseUtils.showError("Cannot demonstrate type", "You cannot demonstrate the type of a primitive variable.", null);
			return;
		}
    	try {
    		String varTypeName = ((IJavaVariable)variable).getJavaType().getName();
    		String initValue = null;
    		Matcher matcher = getInitialConditionFromCurLine(variable, pattern);
    		if (matcher != null) {
    			if (!matcher.group(2).equals(variable.getName())) {
    				EclipseUtils.showError("Illegal variable.", "The first argument to the pdspec method, " + matcher.group(2) + ", must be the same as the variable on which you right-clicked, " + variable.getName() + ".", null);
    				return;
    			}
    			initValue = matcher.group(1);
    		} else
    			initValue = varTypeName;
	    	String typeName = EclipseUtils.getType(path, shell, varTypeName, initValue, EclipseUtils.getStackFrame());
	    	if (typeName != null) {
	    		LambdaProperty property = LambdaProperty.fromType(typeName);
	        	Synthesizer.synthesizeAndInsertExpressions(variable, path, property, null, shell, matcher != null);
	    	}
    	}  catch (DebugException e) {
			e.printStackTrace();
		}
    }

}
