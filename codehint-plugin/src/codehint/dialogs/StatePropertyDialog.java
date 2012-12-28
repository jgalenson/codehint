package codehint.dialogs;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;
import org.eclipse.jface.dialogs.IInputValidator;

import codehint.exprgen.TypeCache;
import codehint.property.Property;
import codehint.property.StateProperty;
import codehint.utils.EclipseUtils;

public class StatePropertyDialog extends PropertyDialog {

	private static final String FREE_VAR_NAME = "_rv";
	
	private final String pdspecMessage;
	private final String initialPdspecText;
	private final IInputValidator pdspecValidator;
    
    public StatePropertyDialog(String varName, IJavaStackFrame stack, String initialValue, String extraMessage) {
    	super(varName, extraMessage);
    	String pdspecMessage = "Demonstrate a property that should hold for " + (varName == null ? FREE_VAR_NAME : varName) + " after this statement is executed.  You may refer to the values of variables after this statement is executed using the prime syntax, e.g., " + varName + "\'";
    	this.pdspecMessage = getFullMessage(pdspecMessage, extraMessage);
    	this.initialPdspecText = initialValue;
    	this.pdspecValidator = new StatePropertyValidator(stack, varName == null);
    }

	@Override
	public String getPdspecMessage() {
    	return pdspecMessage;
	}

	@Override
	public String getInitialPdspecText() {
		return initialPdspecText;
	}

	@Override
	public IInputValidator getPdspecValidator() {
		return pdspecValidator;
	}

	private static class StatePropertyValidator implements IInputValidator {
		
	    private final IJavaStackFrame stackFrame;
	    private final IAstEvaluationEngine evaluationEngine;
	    private final String freeVarError;  
	    
	    public StatePropertyValidator(IJavaStackFrame stackFrame, boolean isFreeVar) {
	    	this.stackFrame = stackFrame;
	    	this.evaluationEngine = EclipseUtils.getASTEvaluationEngine(stackFrame);
	    	this.freeVarError = isFreeVar ? "[" + FREE_VAR_NAME + " cannot be resolved to a variable]" : null;
	    }
	    
	    @Override
		public String isValid(String newText) {
	    	String msg = StateProperty.isLegalProperty(newText, stackFrame, evaluationEngine);
			// TODO: This allows some illegal pdspecs like "_rv'+0" because the only error we get back is about _rv, which we ignore.
	    	if (freeVarError != null && freeVarError.equals(msg)) {
	    		try {
	    			// Ensure the special variable is only used prime.
					if (stackFrame.findVariable(FREE_VAR_NAME) == null) {
						for (int i = -1; (i = newText.indexOf(FREE_VAR_NAME, ++i)) != -1; ) {
							if (i + FREE_VAR_NAME.length() >= newText.length() || Character.isJavaIdentifierPart(newText.charAt(i + FREE_VAR_NAME.length())))
								return "You cannot use the pseudo-variable " + FREE_VAR_NAME + " without priming it.";
						}
					}
				} catch (DebugException e) {
					throw new RuntimeException(e);
				}
	    		return null;
	    	}
	    	return msg;
	    }
	}

	@Override
	public Property computeProperty(String propertyText, TypeCache typeCache) {
		if (propertyText == null)
			return null;
		else
			return StateProperty.fromPropertyString(varName == null ? FREE_VAR_NAME : varName, propertyText);
	}

	@Override
	public String getHelpID() {
		return "state";
	}

}
