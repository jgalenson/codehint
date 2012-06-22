package codehint.dialogs;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.swt.widgets.Shell;

import codehint.property.Property;
import codehint.property.TypeProperty;
import codehint.utils.EclipseUtils;

public class TypePropertyDialog extends SynthesisDialog {

	private final String pdspecMessage;
	private final String initialPdspecText;
	private final IInputValidator pdspecValidator;
	
    public TypePropertyDialog(String varName, String varTypeName, Shell shell, String initialValue, String extraMessage, boolean getSkeleton) {
    	super(shell, varName, varTypeName, getSkeleton);
    	String pdspecMessage = "Demonstrate a type for " + varName + ".  We will find expressions return that type when evaluated.";
    	this.pdspecMessage = getFullMessage(pdspecMessage, extraMessage);
    	try {
	    	IJavaStackFrame stackFrame = EclipseUtils.getStackFrame();
			IJavaProject project = EclipseUtils.getProject(stackFrame);
			IType thisType = EclipseUtils.getThisType(project, stackFrame);
			IType varType = project.findType(varTypeName);
			if (thisType != null && thisType.resolveType(EclipseUtils.getUnqualifiedName(initialValue)) != null)
				initialValue = EclipseUtils.getUnqualifiedName(initialValue);
	    	this.initialPdspecText = initialValue;
	    	this.pdspecValidator = new TypeValidator(project, varType, thisType);
    	} catch (JavaModelException e) {
 			throw new RuntimeException(e);
 		} catch (DebugException e) {
 			throw new RuntimeException(e);
 		}
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

    /**
     * A validator that ensures that the entered text is a type
     * that is a subtype of the given variable's type.
     */
    private static class TypeValidator implements IInputValidator {
    	
    	private final IJavaProject project;
    	private final IType varType;
    	private final IType thisType;
    	
    	public TypeValidator(IJavaProject project, IType varType, IType thisType) {
    		this.project = project;
    		this.varType = varType;
    		this.thisType = thisType;
    	}
        
        @Override
		public String isValid(String newText) {
        	return EclipseUtils.getValidTypeError(project, varType, thisType, newText);
        }
    }

	@Override
	public Property getProperty() {
		String pdspecText = getPdspecText();
		if (pdspecText == null)
			return null;
		else
			return TypeProperty.fromType(pdspecText);
	}

	@Override
	protected String getHelpID() {
		return "type";
	}

}
