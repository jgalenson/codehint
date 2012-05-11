package codehint;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.swt.widgets.Shell;

public class DemonstrateValueHandler extends CommandHandler {


    private final static Pattern pattern = Pattern.compile("\\s*CodeHint.value\\((\\w*)\\s*,\\s*(.*)\\);\\s*\\r?\\n\\s*");
	
    @Override
	public void handle(IVariable variable, String path, Shell shell) {
    	try {
    		Matcher matcher = getInitialConditionFromCurLine(variable, pattern);
    		String initValue = null;
    		if (matcher != null) {
    			if (!matcher.group(1).equals(variable.getName())) {
    				EclipseUtils.showError("Illegal variable.", "The first argument to the value method, " + matcher.group(1) + ", must be the same as the variable on which you right-clicked, " + variable.getName() + ".", null);
    				return;
    			}
    			initValue = matcher.group(2);
    		}
	    	if (EclipseUtils.isPrimitive(variable))
	    		demonstratePrimitiveValue(variable, path, initValue, shell);
	    	else
	    		demonstrateObjectValue(variable, path, initValue, shell);
        } catch (DebugException e) {
        	throw new RuntimeException(e);
        }
    }
    
    private static void demonstratePrimitiveValue(IVariable variable, String path, String initValue, Shell shell) throws DebugException {
    	String stringValue = EclipseUtils.getExpression(path, shell, initValue, null);
    	if (stringValue != null) {
        	IJavaValue demonstrationValue = EclipseUtils.evaluate(stringValue);
        	LambdaProperty property = LambdaProperty.fromPrimitive(EclipseUtils.javaStringOfValue(demonstrationValue));
        	Synthesizer.synthesizeAndInsertExpressions(variable, path, property, demonstrationValue, shell, initValue != null);
    	}
    }

    private static void demonstrateObjectValue(IVariable variable, String path, String initValue, Shell shell) throws DebugException {
        String result = EclipseUtils.getExpression(path, shell, initValue, null);
    	if (result != null) {
    		LambdaProperty property = LambdaProperty.fromObject(result);
        	IJavaValue value = EclipseUtils.evaluate(result);
            Synthesizer.synthesizeAndInsertExpressions(variable, path, property, value, shell, initValue != null);
    	}
    }

}
