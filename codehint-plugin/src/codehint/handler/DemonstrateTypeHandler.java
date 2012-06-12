package codehint.handler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.swt.widgets.Shell;

import codehint.EclipseUtils;
import codehint.Synthesizer;
import codehint.property.TypeProperty;

public class DemonstrateTypeHandler extends CommandHandler {

    public final static Pattern PATTERN = Pattern.compile("\\s*CodeHint.<(.*)>type\\((\\w*)\\);\\s*\\r?\\n\\s*");
    
    @Override
	public void handle(IVariable variable, String path, Shell shell) {
    	try {
    		handle(variable, path, shell, getInitialConditionFromCurLine(PATTERN));
    	}  catch (DebugException e) {
			e.printStackTrace();
		}
    }
    
    private static void handle(IVariable variable, String path, Shell shell, Matcher matcher) throws DebugException {
		assert EclipseUtils.isObject(variable);
		String varTypeName = ((IJavaVariable)variable).getJavaType().getName();
		String initValue = null;
		if (matcher != null) {
			if (!matcher.group(2).equals(variable.getName())) {
				EclipseUtils.showError("Illegal variable.", "The first argument to the pdspec method, " + matcher.group(2) + ", must be the same as the variable on which you right-clicked, " + variable.getName() + ".", null);
				return;
			}
			initValue = matcher.group(1);
		} else
			initValue = varTypeName;
    	TypeProperty property = EclipseUtils.getTypeProperty(path, shell, varTypeName, initValue, null, EclipseUtils.getStackFrame());
    	if (property != null)
        	Synthesizer.synthesizeAndInsertExpressions(variable, path, property, shell, matcher != null);
    }

	public static void handleFromText(Matcher matcher) {
    	try {
			String varName = matcher.group(2);
			IVariable var = EclipseUtils.getStackFrame().findVariable(varName);
			handle(var, varName, EclipseUtils.getShell(), matcher);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

}
