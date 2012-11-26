package codehint.handler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.swt.widgets.Shell;

import codehint.utils.EclipseUtils;
import codehint.Synthesizer;
import codehint.Synthesizer.SynthesisWorker;
import codehint.dialogs.InitialSynthesisDialog;
import codehint.dialogs.TypePropertyDialog;

public class DemonstrateTypeHandler extends CommandHandler {

    public final static Pattern PATTERN = Pattern.compile("\\s*CodeHint.(?:<(.*)>)?type\\((\\w*)\\);\\s*\\r?\\n\\s*");
    
    @Override
	public void handle(IVariable variable, String path, Shell shell) {
    	try {
    		IJavaStackFrame stack = EclipseUtils.getStackFrame();
    		handle(variable, path, shell, getInitialConditionFromCurLine(PATTERN, stack), stack);
    	}  catch (DebugException e) {
			e.printStackTrace();
		}
    }
    
    private static void handle(IVariable variable, String path, Shell shell, Matcher matcher, IJavaStackFrame stack) throws DebugException {
		IJavaType varType = EclipseUtils.getTypeOfVariableAndLoadIfNeeded((IJavaVariable)variable, stack);
		String varTypeName = EclipseUtils.sanitizeTypename(varType.getName());
		String initValue = "";
		if (matcher != null) {
			if (!matcher.group(2).equals(variable.getName())) {
				EclipseUtils.showError("Illegal variable.", "The first argument to the pdspec method, " + matcher.group(2) + ", must be the same as the variable on which you right-clicked, " + variable.getName() + ".", null);
				return;
			}
			initValue = matcher.group(1);
			if (initValue == null)
				initValue = varTypeName;
		} else
			initValue = varTypeName;
		InitialSynthesisDialog dialog = new InitialSynthesisDialog(shell, varTypeName, varType, stack, new TypePropertyDialog(path, varTypeName, stack, initValue, null), new SynthesisWorker(path, varType));
    	Synthesizer.synthesizeAndInsertExpressions(variable, path, dialog, stack, matcher != null);
    }

	public static void handleFromText(Matcher matcher, IJavaStackFrame stack) {
    	try {
			String varName = matcher.group(2);
			IVariable var = stack.findVariable(varName);
			handle(var, varName, EclipseUtils.getShell(), matcher, stack);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

}
