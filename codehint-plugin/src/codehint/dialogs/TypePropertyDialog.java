package codehint.dialogs;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jface.dialogs.IInputValidator;

import codehint.property.Property;
import codehint.property.TypeProperty;
import codehint.utils.EclipseUtils;

public class TypePropertyDialog extends PropertyDialog {

	private final String pdspecMessage;
	private final String initialPdspecText;
	private final IInputValidator pdspecValidator;
	
    public TypePropertyDialog(String varName, String varTypeName, IJavaStackFrame stack, String initialValue, String extraMessage) {
    	super(varName, extraMessage);
    	String pdspecMessage = "Demonstrate a type for " + varName + ".  We will find expressions return that type when evaluated.";
    	this.pdspecMessage = getFullMessage(pdspecMessage, extraMessage);
    	try {
			IJavaProject project = EclipseUtils.getProject(stack);
			IType thisType = EclipseUtils.getThisType(project, stack);
			if (thisType != null && thisType.resolveType(EclipseUtils.getUnqualifiedName(initialValue)) != null)
				initialValue = EclipseUtils.getUnqualifiedName(initialValue);
	    	this.initialPdspecText = initialValue;
	    	this.pdspecValidator = new TypeValidator(project, varTypeName, thisType);
    	} catch (JavaModelException e) {
 			throw new RuntimeException(e);
 		} catch (DebugException e) {
 			throw new RuntimeException(e);
 		}
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

    /**
     * A validator that ensures that the entered text is a type
     * that is a subtype of the given variable's type.
     */
    private static class TypeValidator implements IInputValidator {
    	
    	private final IJavaProject project;
    	private final String varTypeName;
    	private final IType thisType;
    	
    	public TypeValidator(IJavaProject project, String varTypeName, IType thisType) {
    		this.project = project;
    		this.varTypeName = varTypeName;
    		this.thisType = thisType;
    	}
        
        @Override
		public String isValid(String newText) {
        	return EclipseUtils.getValidTypeError(project, varTypeName, thisType, newText);
        }
    }

	@Override
	public Property computeProperty(String propertyText) {
		if (propertyText == null)
			return null;
		else
			return TypeProperty.fromType(propertyText);
	}

	@Override
	public String getHelpID() {
		return "type";
	}

}
