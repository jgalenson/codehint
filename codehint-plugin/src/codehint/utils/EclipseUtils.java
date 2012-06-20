package codehint.utils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;
import org.eclipse.jdt.debug.eval.ICompiledExpression;
import org.eclipse.jdt.debug.eval.IEvaluationListener;
import org.eclipse.jdt.debug.eval.IEvaluationResult;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

import com.sun.jdi.InvocationException;
import com.sun.jdi.ObjectReference;

import codehint.Activator;
import codehint.Synthesizer;
import codehint.expreval.EvaluationManager;
import codehint.expreval.EvaluationManager.EvaluationError;
import codehint.exprgen.ExpressionSkeleton;
import codehint.property.LambdaProperty;
import codehint.property.ObjectValueProperty;
import codehint.property.PrimitiveValueProperty;
import codehint.property.StateProperty;
import codehint.property.TypeProperty;

public class EclipseUtils {
    
	/**
	 * Return the project associated with the given stack frame.
	 * (copied from JavaWatchExpressionDelegate)
	 * (copied from JavaObjectValueEditor)
	 * @param javaStackFrame The stack frame
	 * @return the project associate with the given stack frame.
	 */
	public static IJavaProject getProject(IJavaStackFrame javaStackFrame) {
		Object sourceElement = javaStackFrame.getLaunch().getSourceLocator().getSourceElement(javaStackFrame);
		if (!(sourceElement instanceof IJavaElement) && sourceElement instanceof IAdaptable)
			sourceElement = ((IAdaptable)sourceElement).getAdapter(IJavaElement.class);
		assert sourceElement instanceof IJavaElement;
		return ((IJavaElement)sourceElement).getJavaProject();
	}
    
    public static String getSignature(IVariable variable) throws DebugException {
        IJavaVariable javaVariable = (IJavaVariable)variable.getAdapter(IJavaVariable.class);
        return javaVariable.getSignature();
    }
    
    public static boolean isObject(IVariable variable) {
		try {
			String signature = getSignature(variable);
	    	return isObject(signature);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
    }
    
    public static boolean isObject(IJavaType type) throws DebugException {
    	return type == null || isObject(type.getSignature());
    }
    
    private static boolean isObject(String signature) {
    	return Signature.getTypeSignatureKind(signature) == Signature.CLASS_TYPE_SIGNATURE;
    }
    
    public static boolean isPrimitive(IJavaType type) throws DebugException {
    	return type != null && Signature.getTypeSignatureKind(type.getSignature()) == Signature.BASE_TYPE_SIGNATURE;
    }
   	
    // TODO: There must be a better way to do this through Eclipse.
	/**
	 * Rewrites a type name so that it can be executed in code.
	 * For example "Test.Foo foo" compiles but "Test$Foo foo",
	 * the full typename, does not.
	 * @param name The type name.
	 * @return A sanitized version of the given type name.
	 */
   	public static String sanitizeTypename(String name) {
    	return name.replace('$', '.');
   	}
    
    /**
   	 * Returns the current stack frame context, or <code>null</code> if none.
   	 * 
   	 * @return the current stack frame context, or <code>null</code> if none
   	 */
   	public static IJavaStackFrame getStackFrame() {
        IAdaptable adaptable = DebugUITools.getDebugContext();
        if (adaptable != null) {
        	Object x = adaptable.getAdapter(IJavaStackFrame.class);
        	if (x != null)
        		return (IJavaStackFrame)x;
        }
        try {
	   		for (ILaunch launch: DebugPlugin.getDefault().getLaunchManager().getLaunches()) {
	   			for (IThread thread: launch.getDebugTarget().getThreads()) {
	   				IStackFrame[] frames = thread.getStackFrames();
	   				if (frames.length > 0)
	   					return (IJavaStackFrame)frames[0];
	   			}
	   		}
        } catch (DebugException e) {
        	throw new RuntimeException(e);
        }
   		return null;
   	}
    
    private static IEditorPart getActiveEditorPart() {
   		IWorkbench workbench = PlatformUI.getWorkbench();
       	IWorkbenchPage page = workbench.getActiveWorkbenchWindow().getActivePage();
       	return page.getActiveEditor();
    }
    
    public static ITextEditor getActiveTextEditor() {
    	IEditorPart editorPart = getActiveEditorPart();
    	ITextEditor editor = (ITextEditor) editorPart.getAdapter(ITextEditor.class);
    	assert editor != null;
    	return editor;
    }
    
    /**
     * Parses the given string into a Java expression.
     * @param parser The AST parser to use.
     * @param str The string of a Java expression.
     * @return The resulting AST.
     */
    public static ASTNode parseExpr(ASTParser parser, String str) {
    	// We apparently have to manually tell it to use Java 1.5....
		Map<?, ?> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_1_5, options);
		parser.setCompilerOptions(options);
		// We only want to parse an expression, not a whole program.
		parser.setKind(ASTParser.K_EXPRESSION);
		// Do the actual parsing.
		parser.setSource(str.toCharArray());
		return parser.createAST(null);
    }
    
    /**
     * Returns a string representing the given value this is
     * a legal Java expression.
     * @param value The value whose string representation is desired.
     * @return A string that is the legal Java expression of
     * the given value.
     * @throws DebugException if we cannot get the value.
     */
    public static String javaStringOfValue(IJavaValue value) throws DebugException {
    	if ("C".equals(value.getSignature()))
    		return "'" + value.getValueString() + "'";
    	else if ("Ljava/lang/String;".equals(value.getSignature()))
    		return "\"" + value.getValueString() + "\"";
    	else
    		return value.getValueString();
    }
    
    public static IAstEvaluationEngine getASTEvaluationEngine(IJavaStackFrame stackFrame) {
    	return org.eclipse.jdt.debug.eval.EvaluationManager.newAstEvaluationEngine(getProject(stackFrame), (IJavaDebugTarget)stackFrame.getDebugTarget());
    }
    
    /**
     * Gets a unique set of compile errors from the given compiled expression.
     * @param compiled The compiled expression.
     * @return the unique set of compile errors from the given compiled expression.
     */
    public static String getCompileErrors(ICompiledExpression compiled) {
    	if (compiled.hasErrors()) {
    		return getSetOfCompileErrors(compiled).toString();
    	} else
    		return null;
    }
    
    /**
     * Gets a unique set of compile errors from the given compiled expression.
     * @param compiled The compiled expression.
     * @return the unique set of compile errors from the given compiled expression.
     */
    private static Set<String> getSetOfCompileErrors(ICompiledExpression compiled) {
    	if (compiled.hasErrors()) {
    		Set<String> errors = new HashSet<String>();  // Remove duplicate compile errors.
    		for (String error : compiled.getErrorMessages())
    			errors.add(error);
    		return errors;
    	} else
    		return null;
    }

    /**
     * Checks the given text for compile errors, including that it
     * has the correct type.
     * @param newText A string of the expression to compile.
     * @param typeName A string of the name of the desired type of the expression.
     * @param stackFrame The stack frame.
     * @param evaluationEngine The evaluation engine.
     * @return Any compile errors from the expression, including an error
     *  if it does not have the proper type.
     */
	public static String getCompileErrors(String newText, String typeName, IJavaStackFrame stackFrame, IAstEvaluationEngine evaluationEngine) {
		Set<String> errors = getSetOfCompileErrors(newText, typeName, stackFrame, evaluationEngine);
		if (errors == null)
			return null;
		else
			return errors.toString();
	}

    /**
     * Checks the given text for compile errors, including that it
     * has the correct type.
     * @param newText A string of the expression to compile.
     * @param typeName A string of the name of the desired type of the expression.
     * @param stackFrame The stack frame.
     * @param evaluationEngine The evaluation engine.
     * @return Any compile errors from the expression, including an error
     *  if it does not have the proper type.
     */
	public static Set<String> getSetOfCompileErrors(String newText, String typeName, IJavaStackFrame stackFrame, IAstEvaluationEngine evaluationEngine) {
		try {
			String exprStr = typeName == null ? newText : typeName + " _$_$" + " = " + newText + ";";
			ICompiledExpression compiled = evaluationEngine.getCompiledExpression(exprStr, stackFrame);
        	if (compiled.hasErrors())
        		return getSetOfCompileErrors(compiled);
        	return null;
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}
    
    private static IType getThisType(IJavaProject project, IJavaStackFrame stackFrame) throws DebugException, JavaModelException {
    	String thisTypeName = stackFrame.getDeclaringTypeName();
		IType thisType = project.findType(thisTypeName);
		if (thisType != null && !thisTypeName.contains("$"))  // Do not use anonymous classes (e.g., Foo$1).
			return thisType;
		// The above will fail for anonymous classes (e.g., Foo$1), so we just get the outer part before the $.
		thisTypeName = sanitizeTypename(thisTypeName);
		if (thisTypeName.contains(".")) {
			String outerTypeName = thisTypeName.substring(0, thisTypeName.indexOf('.'));
			return project.findType(outerTypeName);
		}
		return null;
    }
    
    public static LambdaProperty getLambdaProperty(String varName, Shell shell, IJavaType varStaticType, String initialValue, String extraMessage, IJavaStackFrame stackFrame) {
    	try {
    		IJavaProject project = getProject(stackFrame);
    		String varStaticTypeName = varStaticType == null ? null : sanitizeTypename(varStaticType.getName());
    		IType varType = varStaticType == null ? null : project.findType(varStaticTypeName);
    		IType thisType = getThisType(project, stackFrame);
    		
	        String title= "Demonstrate property"; 
	        String message= "Demonstrate a property (in the form of a boolean lambda expression) that should hold for " + varName + " after this statement is executed.";
	        if (initialValue == null)
	        	initialValue = getDefaultLambdaArgName(stackFrame) + getDefaultTypeName(varStaticType, project, varType, thisType, varStaticTypeName) + " => ";
	        LambdaPropertyValidator validator= new LambdaPropertyValidator(stackFrame, project, varType, thisType, varName);
	        String stringValue = getDialogResult(title, message, extraMessage, initialValue, validator, "lambda");
	    	if (stringValue != null)
	    		return LambdaProperty.fromPropertyString(stringValue);
	    	else
	    		return null;
 		} catch (DebugException e) {
 			throw new RuntimeException(e);
 		} catch (JavaModelException e) {
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
    
    private static String getDefaultTypeName(IJavaType varStaticType, IJavaProject project, IType varType, IType thisType, String varStaticTypeName) throws DebugException {
    	if (varStaticType == null || !isObject(varStaticType.getSignature()))
    		return "";
		
		String unqualifiedTypename = getUnqualifiedName(varStaticTypeName);
		if (getValidTypeError(project, varType, thisType, unqualifiedTypename) == null)
			return ": " + unqualifiedTypename;
		else
			return ": " + varStaticTypeName;
    }
    
    private static String getExpression(String varName, String varTypeName, Shell shell, String initialValue, String extraMessage) {
        String title= "Demonstrate an expression"; 
        String message= "Demonstrate an expression for " + varName + ".  We will find expressions that evaluate to the same value.";
        ExpressionValidator validator= new ExpressionValidator(getStackFrame(), EclipseUtils.sanitizeTypename(varTypeName));
        if (initialValue == null)
        	initialValue = "";
        String stringValue = getDialogResult(title, message, extraMessage, initialValue, validator, "value");
    	return stringValue;
    }
    
    public static PrimitiveValueProperty getPrimitiveValueProperty(String varName, String varTypeName, Shell shell, String initialValue, String extraMessage) throws DebugException {
    	String stringValue = getExpression(varName, varTypeName, shell, initialValue, extraMessage);
    	if (stringValue != null) {
    		try {
		    	IJavaValue demonstrationValue = evaluate(stringValue);
		    	return PrimitiveValueProperty.fromPrimitive(EclipseUtils.javaStringOfValue(demonstrationValue), demonstrationValue);
    		}  catch (EvaluationError e) {
		    	Synthesizer.setLastCrashedInfo(varName, PrimitiveValueProperty.fromPrimitive(stringValue, null), null);
				throw e;
			}
    	} else
    		return null;
    }
    
    public static ObjectValueProperty getObjectValueProperty(String varName, String varTypeName, Shell shell, String initialValue, String extraMessage) throws DebugException {
    	String stringValue = getExpression(varName, varTypeName, shell, initialValue, extraMessage);
    	if (stringValue != null) {
    		try {
    			IJavaValue demonstrationValue = evaluate(stringValue);
    			return ObjectValueProperty.fromObject(stringValue, demonstrationValue);
    		} catch (EvaluationError e) {
		    	Synthesizer.setLastCrashedInfo(varName, ObjectValueProperty.fromObject(stringValue, null), null);
				throw e;
			}
    	} else
    		return null;
    }
    
    public static TypeProperty getTypeProperty(String varName, Shell shell, String varTypeName, String initialValue, String extraMessage, IJavaStackFrame stackFrame) {
    	try {
    		varTypeName = sanitizeTypename(varTypeName);
			IJavaProject project = getProject(stackFrame);
			IType varType = project.findType(varTypeName);
    		IType thisType = getThisType(project, stackFrame);
			
			// Default to the unqualified typename if I can.
    		if (initialValue.contains("$"))
    			initialValue = sanitizeTypename(initialValue);
			if (thisType != null && thisType.resolveType(getUnqualifiedName(initialValue)) != null)
				initialValue = getUnqualifiedName(initialValue);
	    	
	    	String title= "Demonstrate a type"; 
	        String message= "Demonstrate a type for " + varName + ".  We will find expressions return that type when evaluated.";
	        TypeValidator validator= new TypeValidator(project, varType, thisType);
	        String typeName = getDialogResult(title, message, extraMessage, initialValue, validator, "type");
	    	if (typeName != null)
	    		return TypeProperty.fromType(typeName);
    		else
    			return null;
    	} catch (JavaModelException e) {
 			throw new RuntimeException(e);
 		} catch (DebugException e) {
 			throw new RuntimeException(e);
 		}
    }
    
    public static StateProperty getStateProperty(String varName, Shell shell, String initialValue, String extraMessage) {
		String title= "Demonstrate state property"; 
        String message= "Demonstrate a state property that should hold for " + varName + " after this statement is executed.  You may refer to the values of variables after this statement is executed using the prime syntax, e.g., " + varName + "\'";
        StatePropertyValidator validator= new StatePropertyValidator(getStackFrame());
        String stringValue = getDialogResult(title, message, extraMessage, initialValue, validator, "state");
    	if (stringValue != null)
    		return StateProperty.fromPropertyString(varName, stringValue);
    	else
    		return null;
    }
    
    public static ExpressionSkeleton getExpressionSkeleton(String varTypeName, String initialValue) {
    	String title = "Give an expression skeleton"; 
        String message = "Given an expression skeleton that describes the form of the desired expression, using " + ExpressionSkeleton.HOLE_SYNTAX + "s for unknown expressions and names.";
        ExpressionSkeletonValidator validator = new ExpressionSkeletonValidator(getStackFrame(), EclipseUtils.sanitizeTypename(varTypeName));
        if (initialValue == null)
        	initialValue = ExpressionSkeleton.HOLE_SYNTAX;
        String stringValue = getDialogResult(title, message, null, initialValue, validator, "skeleton");
    	if (stringValue != null)
    		return ExpressionSkeleton.fromString(stringValue);
    	else
    		return null;
    }
    
    private static String getDialogResult(String title, String message, String extraMessage, String initialValue, IInputValidator validator, final String helpContext) {
        if (extraMessage != null)
        	message += System.getProperty("line.separator") + extraMessage;
    	InputDialog dialog= new ModelessInputDialog(null, title, message, initialValue, validator){
        	@Override
			protected Control createDialogArea(Composite parent) {
        		IWorkbench workbench = PlatformUI.getWorkbench();
        		workbench.getHelpSystem().setHelp(parent, Activator.PLUGIN_ID + "." + helpContext);
        		return super.createDialogArea(parent);
        	}
        };
        if (dialog.open() == Window.OK) {
            String stringValue = dialog.getValue();
        	return stringValue;
    	}
        return null;
    }
    
    // Adapted from http://www.eclipse.org/forums/index.php/mv/tree/4336/.
    // TODO: Using this allows the user to do some weird things like continue execution, which can potentially do weird things.
    private static class ModelessInputDialog extends InputDialog
    {
    	public ModelessInputDialog(Shell parentShell, String dialogTitle, String dialogMessage, String initialValue, IInputValidator validator) {
    		super(parentShell, dialogTitle, dialogMessage, initialValue, validator);
    		setBlockOnOpen(false);
    	}

    	@Override
		protected void setShellStyle(int newShellStyle)
    	{
    		int newstyle = newShellStyle & ~SWT.APPLICATION_MODAL; // turn off APPLICATION_MODAL
    		newstyle |= SWT.MODELESS; // turn on MODELESS
    		super.setShellStyle(newstyle);
    	}

    	@Override
		public int open()
    	{
    		int retVal = super.open();
    		pumpMessages(); // this will let the caller wait till OK, Cancel is pressed, but will let the other GUI responsive
    		return retVal; // TODO: Since open() returns immediately, we don't get the real return value.  Specifically, if the user clicks Cancel, we think they clicked OK.
    	}

    	protected void pumpMessages()
    	{
    		Shell shell = getShell();
    		Display display = shell.getDisplay();
    		while (!shell.isDisposed())
    			if (!display.readAndDispatch())
    				display.sleep();
    		display.update();
    	}
    }

    private static class LambdaPropertyValidator implements IInputValidator {
    	
    	private final IJavaStackFrame stackFrame;
    	private final IJavaProject project;
    	private final IType varType;
    	private final IType thisType;
    	private final IAstEvaluationEngine evaluationEngine;
    	private final String varName;
    	
    	public LambdaPropertyValidator(IJavaStackFrame stackFrame, IJavaProject project, IType varType, IType thisType, String varName) {
    		this.stackFrame = stackFrame;
    		this.project = project;
    		this.varType = varType;
    		this.thisType = thisType;
    		this.evaluationEngine = getASTEvaluationEngine(stackFrame);
    		this.varName = varName;
    	}
        
        @Override
		public String isValid(String newText) {
        	return LambdaProperty.isLegalProperty(newText, stackFrame, project, varType, thisType, evaluationEngine, varName);
        }
    }

    private static class ExpressionValidator implements IInputValidator {
    	
        private final static ASTParser parser = ASTParser.newParser(AST.JLS4);
        private final IJavaStackFrame stackFrame;
        private final IAstEvaluationEngine evaluationEngine;
        private final String varTypeName;
        
        public ExpressionValidator(IJavaStackFrame stackFrame, String varTypeName) {
        	this.stackFrame = stackFrame;
        	this.evaluationEngine = getASTEvaluationEngine(stackFrame);
        	this.varTypeName = varTypeName;
        }
        
        @Override
		public String isValid(String newText) {
        	ASTNode node = EclipseUtils.parseExpr(parser, newText);
        	if (node instanceof CompilationUnit)
        		return "Enter a valid expression: " + ((CompilationUnit)node).getProblems()[0].getMessage();
			String compileErrors = getCompileErrors(newText, varTypeName, stackFrame, evaluationEngine);
			if (compileErrors != null)
				return compileErrors;
        	return null;
        }
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
        	return getValidTypeError(project, varType, thisType, newText);
        }
    }
    
    public static String getValidTypeError(IJavaProject project, IType varType, IType thisType, String newTypeName) {
    	try {
    		if (varType == null)
    			return "You cannot enter a type for a primitive.";
    		if (thisType == null)
    			return null;
    		String[][] allTypes = thisType.resolveType(newTypeName);
    		if (allTypes == null)
        		return "Please enter a valid type.";
    		if (allTypes.length > 1)
    			return "Please enter an unambiguous type by qualifying it.";
    		String[] typeParts = allTypes[0];
			String fullTypeName = "";
			for (int i = 0; i < typeParts.length; i++) {
				if (i > 0)
					fullTypeName += ".";
				fullTypeName += typeParts[i];
			}
        	IType curType = project.findType(fullTypeName);
			if (curType.newSupertypeHierarchy(null).contains(varType))
				return null;
			return "Please enter a subtype of " + varType.getFullyQualifiedName('.') + ".";
    	} catch (JavaModelException e) {
			throw new RuntimeException(e);
		}
    }
    
    /**
     * Gets the type with the given unique name
     * @param typeName The name of the type to get, which does not need
     * to be fully-qualified but must be unique.
     * @param stackFrame The current stack frame.
     * @param target The debug target.
     * @return The IJavaType representing the given name.
     */
    public static IJavaType getType(String typeName, IJavaStackFrame stackFrame, IJavaDebugTarget target) {
		try {
			IJavaProject project = getProject(stackFrame);
			return getType(project, getThisType(project, stackFrame), typeName, target);
		} catch (JavaModelException e) {
			throw new RuntimeException(e);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
    }
    
    private static IJavaType getType(IJavaProject project, IType thisType, String typeName, IJavaDebugTarget target) {
    	try {
    		if (thisType == null || project == null || target == null)
    			return null;
    		// If this is an array type, first get the base type and then generate the array at the end. 
    		int firstArray = typeName.indexOf("[]");
    		String componentTypeName = firstArray == -1 ? typeName : typeName.substring(0, firstArray);
    		String arrayTypes = firstArray == -1 ? "" : typeName.substring(firstArray);
    		// Find the full type name.
    		String[][] allTypes = thisType.resolveType(componentTypeName);
    		assert allTypes != null && allTypes.length == 1;
    		String[] typeParts = allTypes[0];
			String fullTypeName = "";
			typeParts[typeParts.length - 1] = typeParts[typeParts.length - 1].replace('.', '$');  // For some reason the IJavaDebugTarget.getJavaTypes method needs to have inner typesnames use '$' and not '.'.
			for (int i = 0; i < typeParts.length; i++) {
				if (fullTypeName.length() > 0)
					fullTypeName += ".";
				fullTypeName += typeParts[i];
			}
			return getFullyQualifiedType(fullTypeName + arrayTypes, target);
    	} catch (JavaModelException e) {
			throw new RuntimeException(e);
		}
    }
    
    /**
     * Gets the type with the given fully-qualified name.
     * @param fullTypeName The fully-qualified name of a type.
     * @param target The debug target.
     * @return The type with the given name.
     */
    public static IJavaType getFullyQualifiedType(String fullTypeName, IJavaDebugTarget target) {
		IJavaType type = getFullyQualifiedTypeIfExists(fullTypeName, target);
		assert type != null : fullTypeName;
		return type;
    }

    /**
     * Gets the type with the given fully-qualified name if one exists.
     * @param fullTypeName The fully-qualified name of a type.
     * @param target The debug target.
     * @return The type with the given name, or null.
     */
    public static IJavaType getFullyQualifiedTypeIfExists(String fullTypeName, IJavaDebugTarget target) {
    	try {
			IJavaType[] curTypes = target.getJavaTypes(fullTypeName);
			if (curTypes == null || curTypes.length != 1)
				return null;
			else
				return curTypes[0];
    	} catch (DebugException e) {
			throw new RuntimeException(e);
		}
    }

    private static class StatePropertyValidator implements IInputValidator {
    	
        private final IJavaStackFrame stackFrame;
        private final IAstEvaluationEngine evaluationEngine;
        
        public StatePropertyValidator(IJavaStackFrame stackFrame) {
        	this.stackFrame = stackFrame;
        	this.evaluationEngine = getASTEvaluationEngine(stackFrame);
        }
        
        @Override
		public String isValid(String newText) {
        	return StateProperty.isLegalProperty(newText, stackFrame, evaluationEngine);
        }
    }

    private static class ExpressionSkeletonValidator implements IInputValidator {
    	
        private final IJavaStackFrame stackFrame;
        private final IAstEvaluationEngine evaluationEngine;
        private final String varTypeName;
        
        public ExpressionSkeletonValidator(IJavaStackFrame stackFrame, String varTypeName) {
        	this.stackFrame = stackFrame;
        	this.evaluationEngine = getASTEvaluationEngine(stackFrame);
        	this.varTypeName = varTypeName;
        }
        
        @Override
		public String isValid(String newText) {
        	return ExpressionSkeleton.isLegalSkeleton(newText, varTypeName, stackFrame, evaluationEngine);
        }
    }
    
   	public static void insertIndentedLineAtCurrentDebugPoint(String text) throws BadLocationException, DebugException {
   		int line = getStackFrame().getLineNumber() - 1;
   		IDocument document = getDocument();
   		
   		int offset = document.getLineOffset(line);
   		int firstchar = offset;
   		while( document.getChar(firstchar) == ' ' ||
   				document.getChar(firstchar) == '\t') {
   			firstchar++;
   		}
   		String indent = document.get(offset, firstchar-offset);
   		//Bug fix: need to keep the debug cursor before the inserted line so we can
   		// execute it.  Inserting at beginning of line shifts it down one.
   		document.replace(firstchar, 0, text + "\n" + indent); //$NON-NLS-1$
   		
   		//Log the change for debugging
   		//System.out.println("Inserting text: \"\"\"" + text + "\"\"\" at " + path + " line " + line + " position " + start + " to " + end);

   	}
   	
   	public static void replaceLineAtCurrentDebugPoint(String newText) throws DebugException, BadLocationException {
   		int line = getStackFrame().getLineNumber() - 1;
   		replaceLine(newText, line);
   	}
   	
   	public static void replaceLine(String newText, int line) throws BadLocationException {
   		IDocument document = getDocument();
   		
   		int offset = document.getLineOffset(line);
   		int firstChar = offset;
   		while (document.getChar(firstChar) == ' ' || document.getChar(firstChar) == '\t')
   			firstChar++;
   		int lastChar = firstChar;
   		while (document.getChar(lastChar) != '\n')
   			lastChar++;
   		//Bug fix: need to keep the debug cursor before the inserted line so we can
   		// execute it.  Inserting at beginning of line shifts it down one.
   		document.replace(firstChar, lastChar - firstChar, newText);
   	}
   	
   	public static IDocument getDocument() {
   		ITextEditor editor = EclipseUtils.getActiveTextEditor();
   		return editor.getDocumentProvider().getDocument(editor.getEditorInput());
   	}

   	// This only seems to work when called from the UI thread (e.g., when called with Display.syncExec).
	public static Shell getShell() {
		return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
	}
   	
   	public static void showError(final String title, String text, Throwable exception) {
   		showErrorOrWarning(title, text, exception, IStatus.ERROR);
   	}
   	
   	public static void showWarning(final String title, String text, Throwable exception) {
   		showErrorOrWarning(title, text, exception, IStatus.WARNING);
   	}
   	
   	private static void showErrorOrWarning(final String title, String text, Throwable exception, int severity) {
   		final IStatus status = new Status(severity, Activator.PLUGIN_ID, IStatus.OK, text, exception);
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				ErrorDialog.openError(getShell(), title, null, status);
			}
		});
   	}
   	
   	public static void log(String msg) {
   		Activator.getDefault().getLog().log(new Status(IStatus.INFO, Activator.PLUGIN_ID, msg));
   	}

    /**
     * Evaluates the given snippet. Reports any errors to the user.
     * @param stringValue the snippet to evaluate
     * @return the value that was computed or <code>null</code> if any errors occurred.
     * @throws DebugException 
     */
   	// TODO: Integrate with codehint.expreval code?
    public static IJavaValue evaluate(String stringValue) throws DebugException {
        final IJavaStackFrame frame = getStackFrame();
        IAstEvaluationEngine engine = getASTEvaluationEngine(frame);
        final IEvaluationResult[] results = new IEvaluationResult[1];
        IEvaluationListener listener = new IEvaluationListener() {
            @Override
			public void evaluationComplete(IEvaluationResult result) {
                synchronized (frame) {
                    results[0] = result;
                    frame.notifyAll();
                }
            }
        };
		synchronized(frame) {
            engine.evaluate(stringValue, frame, listener, DebugEvent.EVALUATION_IMPLICIT, false);
			try {
				frame.wait();
			} catch (InterruptedException e) {
				if (results[0] == null)
					throw new RuntimeException(e);
			}
		}
		IEvaluationResult result = results[0];
		if (result == null)
		    return null;
		if (result.hasErrors()) {
			String msg = "The following errors were encountered during evaluation.\n\n" + getErrors(result);
			showError("Evaluation error", msg, null);
			throw new EvaluationManager.EvaluationError(msg);
		}
		return result.getValue();
    }
    
    public static String getUnqualifiedName(String name) {
    	int lastDot = name.lastIndexOf('.');
    	if (lastDot != -1)
    		name = name.substring(lastDot + 1);
    	return name;
    }
    
    public static IJavaType getTypeAndLoadIfNeeded(String typeName, IJavaDebugTarget target) {
		IJavaType type = getFullyQualifiedTypeIfExists(typeName, target);
		if (type == null) {
			loadClass(typeName);
			type = getFullyQualifiedTypeIfExists(typeName, target);
			/*if (type == null)
				System.out.println("Failed to load " + typeName);
			else
				System.out.println("Loaded " + typeName);*/
		}
		return type;
    }
    
    // TODO: There must be a better way to do this.
    private static void loadClass(String typeName) {
    	try {
    		evaluate(sanitizeTypename(typeName) + ".class");
    	} catch (DebugException e) {
    		throw new RuntimeException(e);
    	}
    }
    
    /**
     * Checks whether the given classname represents an anonymous class
     * (and therefore cannot be instantiated as-is).  Such names end
     * with a dollar sign followed by a sequence number.
     * @param name The name of the class.
     * @return Whether the given name represents an anonymous class.
     */
    /*public static boolean isAnonymousClass(String name) {
    	int lastDollar = name.lastIndexOf('$');
    	if (lastDollar == -1)
    		return false;
    	for (int i = lastDollar + 1; i < name.length(); i++) {
    		char ch = name.charAt(i);
    		if (ch < '0' || ch > '9')
    			return false;
    	}
    	return true;
    }*/
    
    /**
     * Extracts the errors messages out of a failing IEvaluationResult.
     * Copied from JavaObjectValueEditor.evaluate.
     * @param result A failing evaluation result.
     * @return A string representing the failure.
     */
    private static String getErrors(IEvaluationResult result) {
	    StringBuilder sb = new StringBuilder();
	    //buffer.append("Error on evaluation of: ").append(result.getSnippet()).append("\n");
	    if (result.getException() == null) {
		    String[] messages = result.getErrorMessages();
		    for (int i = 0; i < messages.length; i++)
		    	sb.append(messages[i]).append("\n ");
	    } else
	    	sb.append(getExceptionMessage(result.getException()));
    	return sb.toString();
    }
	
    /*
     * Inspired by org.eclipse.jdt.internal.debug.ui.actions.EvaluateAction.getExceptionMessage.
     */
	private static String getExceptionMessage(Throwable exception) {
		if (exception instanceof CoreException) {
			CoreException ce = (CoreException)exception;
			Throwable throwable= ce.getStatus().getException();
			if (throwable instanceof InvocationException) {
				ObjectReference ref = ((InvocationException)exception).exception();
				return "An exception occurred: " + ref.referenceType().name();
			} else if (throwable instanceof CoreException)
				return getExceptionMessage(throwable);
			return ce.getStatus().getMessage();
		}
		String message = "An exception occurred: " + exception.getClass(); 
		if (exception.getMessage() != null)
			message += " - " + exception.getMessage();
		return message;
	}

}
