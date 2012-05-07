package codehint;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.swt.widgets.Shell;

public class DemonstrateStatePropertyHandler extends CommandHandler {

    private final static Pattern pattern = Pattern.compile("\\s*CodeHint.pdspec\\((\\w*)\\s*,\\s*(.*)\\);\\s*\\r?\\n\\s*");
    
    @Override
	public void handle(IVariable variable, String path, Shell shell) {
    	try {
    		String initValue = null;
    		Matcher matcher = getInitialConditionFromCurLine(variable, pattern);
    		if (matcher != null) {
    			if (!matcher.group(1).equals(variable.getName())) {
    				EclipseUtils.showError("Illegal variable.", "The first argument to the pdspec method, " + matcher.group(1) + ", must be the same as the variable on which you right-clicked, " + variable.getName() + ".", null);
    				return;
    			}
    			initValue = matcher.group(2);
    		} else {
    			Property lastDemonstratedProperty = Synthesizer.getLastDemonstratedProperty();
    			initValue = lastDemonstratedProperty == null ? null : lastDemonstratedProperty.toString();
    		}
	    	StateProperty property = EclipseUtils.getStateProperty(path, shell, initValue, null);
	    	if (property != null)
	        	Synthesizer.synthesizeAndInsertExpressions(variable, path, property, null, shell, matcher != null);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
    }

}
