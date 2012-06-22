package codehint.dialogs;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.swt.widgets.Shell;

import codehint.property.LambdaProperty;
import codehint.property.Property;
import codehint.utils.EclipseUtils;

public class LambdaPropertyDialog extends SynthesisDialog {

	private final String pdspecMessage;
	private final String initialPdspecText;
	private final IInputValidator pdspecValidator;
    
    public LambdaPropertyDialog(String varName, String varTypeName, IJavaType varStaticType, IJavaStackFrame stack, Shell shell, String initialValue, String extraMessage, boolean getSkeleton) {
    	super(shell, varName, varTypeName, stack, getSkeleton);
    	String pdspecMessage = "Demonstrate a property (in the form of a boolean lambda expression) that should hold for " + varName + " after this statement is executed.";
    	this.pdspecMessage = getFullMessage(pdspecMessage, extraMessage);
    	try {
			IJavaProject project = EclipseUtils.getProject(stack);
			IType thisType = EclipseUtils.getThisType(project, stack);
			if (initialValue.length() > 0)
				this.initialPdspecText = initialValue;
			else
				this.initialPdspecText = getDefaultLambdaArgName(stack) + getDefaultTypeName(varStaticType, project, thisType, varTypeName) + " => ";
	    	this.pdspecValidator = new LambdaPropertyValidator(stack, project, varTypeName, thisType, varName);
    	} catch (JavaModelException e) {
 			throw new RuntimeException(e);
 		} catch (DebugException e) {
 			throw new RuntimeException(e);
 		}
    }
    
    private static String getDefaultLambdaArgName(IJavaStackFrame stackFrame) throws DebugException {
    	if (stackFrame.findVariable("x") == null)
    		return "x";
    	for (char name = 'a'; name <= 'z'; name++)
        	if (stackFrame.findVariable("" + name) == null)
        		return "" + name;
    	for (int i = 0; true; i++)
        	if (stackFrame.findVariable("x" + i) == null)
        		return "x" + i;
    }
    
    private static String getDefaultTypeName(IJavaType varStaticType, IJavaProject project, IType thisType, String varStaticTypeName) throws DebugException {
    	if (varStaticType == null || !EclipseUtils.isObject(varStaticType))
    		return "";
		String unqualifiedTypename = EclipseUtils.getUnqualifiedName(varStaticTypeName);
		if (EclipseUtils.getValidTypeError(project, varStaticTypeName, thisType, unqualifiedTypename) == null)
			return ": " + unqualifiedTypename;
		else
			return ": " + varStaticTypeName;
    }

	@Override
	protected String getPdspecMessage() {
    	return pdspecMessage;
	}

	@Override
	protected String getInitialPdspecText() {
		return initialPdspecText;
	}

	@Override
	protected IInputValidator getPdspecValidator() {
		return pdspecValidator;
	}

    private static class LambdaPropertyValidator implements IInputValidator {
    	
    	private final IJavaStackFrame stackFrame;
    	private final IJavaProject project;
    	private final String varTypeName;
    	private final IType thisType;
    	private final IAstEvaluationEngine evaluationEngine;
    	private final String varName;
    	
    	public LambdaPropertyValidator(IJavaStackFrame stackFrame, IJavaProject project, String varTypeName, IType thisType, String varName) {
    		this.stackFrame = stackFrame;
    		this.project = project;
    		this.varTypeName = varTypeName;
    		this.thisType = thisType;
    		this.evaluationEngine = EclipseUtils.getASTEvaluationEngine(stackFrame);
    		this.varName = varName;
    	}
        
        @Override
		public String isValid(String newText) {
        	return LambdaProperty.isLegalProperty(newText, stackFrame, project, varTypeName, thisType, evaluationEngine, varName);
        }
    }

	@Override
	public Property getProperty() {
		String pdspecText = getPdspecText();
		if (pdspecText == null)
			return null;
		else
			return LambdaProperty.fromPropertyString(pdspecText);
	}

	@Override
	protected String getHelpID() {
		return "lambda";
	}
	
}
