package codehint.handler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.swt.widgets.Shell;

import codehint.EclipseUtils;
import codehint.Synthesizer;
import codehint.property.Property;
import codehint.property.ValueProperty;

public class DemonstrateValueHandler extends CommandHandler {

    public final static Pattern PATTERN = Pattern.compile("\\s*CodeHint.value\\((\\w*)\\s*,\\s*(.*)\\);\\s*\\r?\\n\\s*");
	
    @Override
	public void handle(IVariable variable, String path, Shell shell) {
    	try {
    		handle(variable, path, shell, getInitialConditionFromCurLine(PATTERN));
        } catch (DebugException e) {
        	throw new RuntimeException(e);
        }
    }
	
	private static void handle(IVariable variable, String path, Shell shell, Matcher matcher) throws DebugException {
		String initValue = null;
		if (matcher != null) {
			if (!matcher.group(1).equals(variable.getName())) {
				EclipseUtils.showError("Illegal variable.", "The first argument to the value method, " + matcher.group(1) + ", must be the same as the variable on which you right-clicked, " + variable.getName() + ".", null);
				return;
			}
			initValue = matcher.group(2);
		} else {
	    	Property lastCrashedProperty = Synthesizer.getLastCrashedProperty(path);
	    	initValue = lastCrashedProperty instanceof ValueProperty ? ((ValueProperty)lastCrashedProperty).getValueString() : null;
		}
		String varTypeName = ((IJavaVariable)variable).getJavaType().getName();
		ValueProperty property = EclipseUtils.isObject(variable) ? EclipseUtils.getObjectValueProperty(path, varTypeName, shell, initValue, null) : EclipseUtils.getPrimitiveValueProperty(path, varTypeName, shell, initValue, null);
		if (property != null)
        	Synthesizer.synthesizeAndInsertExpressions(variable, path, property, shell, initValue != null);
    }

	public static void handleFromText(Matcher matcher) {
    	try {
			String varName = matcher.group(1);
			IVariable var = EclipseUtils.getStackFrame().findVariable(varName);
			handle(var, varName, EclipseUtils.getShell(), matcher);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

}
