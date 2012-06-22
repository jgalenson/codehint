package codehint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaPrimitiveValue;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.jdt.debug.core.JDIDebugModel;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.ListSelectionDialog;
import org.eclipse.ui.progress.UIJob;
import org.eclipse.ui.texteditor.ITextEditor;

import codehint.dialogs.LambdaPropertyDialog;
import codehint.dialogs.ObjectValuePropertyDialog;
import codehint.dialogs.PrimitiveValuePropertyDialog;
import codehint.dialogs.StatePropertyDialog;
import codehint.dialogs.SynthesisDialog;
import codehint.dialogs.TypePropertyDialog;
import codehint.expreval.EvaluatedExpression;
import codehint.expreval.EvaluationManager;
import codehint.expreval.EvaluationManager.EvaluationError;
import codehint.exprgen.ExpressionGenerator;
import codehint.exprgen.ExpressionSkeleton;
import codehint.exprgen.ExpressionSkeleton.TypeError;
import codehint.exprgen.TypedExpression;
import codehint.handler.DemonstrateStatePropertyHandler;
import codehint.handler.DemonstrateTypeHandler;
import codehint.handler.DemonstrateValueHandler;
import codehint.property.LambdaProperty;
import codehint.property.ObjectValueProperty;
import codehint.property.PrimitiveValueProperty;
import codehint.property.Property;
import codehint.property.StateProperty;
import codehint.property.TypeProperty;
import codehint.property.ValueProperty;
import codehint.utils.EclipseUtils;

public class Synthesizer {
	
	private static Map<String, Property> initialDemonstrations;
	// We store the last property we have seen demonstrated demonstrated while we are processing it.  If everything works, we clear this, but if there is an error, we keep this and use it as the initial value for the user's next demonstrated property.
	private static String lastCrashedVariable;
	private static ExpressionSkeleton lastCrashedSkeleton;
	private static Property lastCrashedProperty;
	
	public static void synthesizeAndInsertExpressions(final IVariable variable, final String fullVarName, final SynthesisDialog synthesisDialog, final IJavaStackFrame stack, Shell shell, final boolean replaceCurLine) {
		try {
			synthesisDialog.open();
			final Property property = synthesisDialog.getProperty();
			final ExpressionSkeleton skeleton = synthesisDialog.getSkeleton();
			if (property == null || skeleton == null)
				return;
			
			EclipseUtils.log("Beginning synthesis for " + variable.toString() + " with property " + property.toString() + " and skeleton " + skeleton.toString() + ".");
	
			final IJavaType varStaticType = ((IJavaVariable)variable).getJavaType();
	
			// Compute a list of expressions that generate that value as possible choices for expressions.
			Job job = new Job("Expression generation") {
				@Override
				protected IStatus run(IProgressMonitor monitor) {
					try {
						IJavaDebugTarget target = (IJavaDebugTarget)variable.getDebugTarget();

						final List<EvaluatedExpression> validExpressions = skeleton.synthesize(target, stack, property, varStaticType, monitor);
	
				    	if (validExpressions.isEmpty())
							return new Status(IStatus.ERROR, Activator.PLUGIN_ID, "No valid expressions were found.");
	
				        UIJob job = new UIJob("Inserting synthesized code"){
	                        @Override
	                        public IStatus runInUIThread(IProgressMonitor monitor) {
								try {
									CandidateSelector candidateSelector = new CandidateSelector(validExpressions.toArray(new EvaluatedExpression[validExpressions.size()]), stack);
									List<EvaluatedExpression> finalExpressions = candidateSelector.getUserFilteredCandidates();
	
							        if (finalExpressions == null) {
								    	setLastCrashedInfo(null, null, null);
										EclipseUtils.log("Cancelling synthesis for " + variable.toString() + " with property " + property.toString() + " and skeleton " + skeleton.toString() + ".");
										return Status.CANCEL_STATUS;
							        }
							        else if (finalExpressions.isEmpty())
										return new Status(IStatus.ERROR, Activator.PLUGIN_ID, "No valid expressions were found.");
									
									List<String> validExpressionStrings = EvaluatedExpression.snippetsOfEvaluatedExpressions(finalExpressions);
									
									//Construct the textual line to insert.  Working in text seems easier than
									// using the AST manipulators for now, but this may need revisited later.  
									String statement = generateChooseOrChosenStmt(fullVarName, validExpressionStrings);
				
									//Insert the new text
									try {
										if (replaceCurLine)
											EclipseUtils.replaceLineAtCurrentDebugPoint(statement, stack.getLineNumber() - 1);
										else
											EclipseUtils.insertIndentedLineAtCurrentDebugPoint(statement, stack.getLineNumber() - 1);
									} catch (BadLocationException e) {
										throw new RuntimeException(e);
									}
				
									//Insert ourselves a breakpoint so that we stop here again
									// Note: Really we'd like to break only on a trace without an override.  Can we do this?
				
									//Don't want to actually change the value.  The invocation of choose (that we just inserted) will do that.
				
									//Insert breakpoint so we can find this line again
									//get the current frame from the current active editor to get the cursor line
									// Note: This is not how the variable visit does this, 
									// hopefully, we get the same value
				
									int line = stack.getLineNumber();  // Comment from Eclipse sources: Document line numbers are 0-based. Debug line numbers are 1-based.
									assert line >= 0;
				
									ITextEditor editor = EclipseUtils.getActiveTextEditor();
									assert editor != null;
				
									String typename = stack.getDeclaringTypeName();
									IResource resource = (IResource)editor.getEditorInput().getAdapter(IResource.class);
				
									try {
										JDIDebugModel.createLineBreakpoint(resource, typename, line, -1, -1, 0, true, null);
									} catch (CoreException e) {
										throw new RuntimeException(e);
									}
				
									// TODO: Using the text of the statement as a key is not a very good idea.
									if (finalExpressions.size() > 1)
										initialDemonstrations.put(statement, property);
	
									IJavaValue value = property instanceof ValueProperty ? ((ValueProperty)property).getValue() : finalExpressions.get(0).getResult();
									if (value != null)
										variable.setValue(value);
	
							    	setLastCrashedInfo(null, null, null);
									
									//NOTE: Our current implementation does not save.  Without a manual save, our added 
									// code gets ignored for this invocation.  Should we force a save and restart?
				
							    	EclipseUtils.log("Finishing synthesis for " + variable.toString() + " with property " + property.toString() + " and skeleton " + skeleton.toString() + ".  Found " + finalExpressions.size() + " user-approved expressions and inserted " + statement);
									return Status.OK_STATUS;
								} catch (DebugException e) {
									e.printStackTrace();
									EclipseUtils.showError("Error", "An error occurred processing your demonstration.", e);
									throw new RuntimeException(e.getMessage());
								}
							}
			        	};
			            job.setUser(true);
			            job.schedule();
						return Status.OK_STATUS;
					} catch (EvaluationError e) {
				    	setLastCrashedInfo(variable.toString(), property, skeleton);
						return new Status(IStatus.ERROR, Activator.PLUGIN_ID, e.getMessage());
					} catch (OperationCanceledException e) {
						EclipseUtils.log("Cancelling synthesis for " + variable.toString() + " with property " + property.toString() + " and skeleton " + skeleton.toString() + ".");
						return Status.CANCEL_STATUS;
					} catch (TypeError e) {
						EclipseUtils.showError("Error", e.getMessage(), null);
						return Status.CANCEL_STATUS;
					}
				}
			};
			job.setPriority(Job.LONG);
			job.setUser(true);
			job.schedule();
		} catch (DebugException e) {
			throw new RuntimeException(e);
		} 
	}
	
	// TODO: Make the view prettier when it shows the values.  Perhaps http://stackoverflow.com/questions/8862589/how-to-add-multiple-columns-in-listselectiondialog-in-eclipse.
	private static class CandidateSelector {
		
		private final ListSelectionDialog dialog;
		
		public CandidateSelector(EvaluatedExpression[] validExprs, IJavaStackFrame stack) {
			dialog = new MyDialog(EclipseUtils.getShell(), "", new MyContentProvider(validExprs), new MyLabelProvider(validExprs, stack), "Select the candidates to keep.  The expressions are on the left and their values are on the right.  Objects have their toStrings shown.");
			dialog.setInitialSelections(validExprs);
			dialog.setTitle("Select candidates");
		}
		
		private static class MyDialog extends ListSelectionDialog {
			
			public MyDialog(Shell parentShell, Object input, IStructuredContentProvider contentProvider, ILabelProvider labelProvider, String message) {
				super(parentShell, input, contentProvider, labelProvider, message);
			}

			// TODO: Find a nicer way to give a two-column dialog.  This hackily changes the font to be monospaced.
			@Override
			protected Control createDialogArea(Composite parent) {
				Control c = super.createDialogArea(parent);
				// Pick some monospaced font.
				Font font = (new LocalResourceManager(JFaceResources.getResources(), c)).createFont(FontDescriptor.createFrom("DejaVu Sans Mono", 10, SWT.NORMAL));
				// Apply it to the elements in the dialog but not the buttons and text at the top.
				// Note that this relies heavily on the implementation of ListSelectionDialog.
				((Composite)c).getChildren()[1].setFont(font);
				return c;
			}
			
		}

		private static class MyContentProvider implements IStructuredContentProvider {
			
			private final EvaluatedExpression[] validExprs;
			
			public MyContentProvider(EvaluatedExpression[] validExprs) {
				this.validExprs = validExprs;
			}

			@Override
			public void dispose() {}

			@Override
			public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {}

			@Override
			public Object[] getElements(Object inputElement) {
				return validExprs;
			}
			
		}

		private static class MyLabelProvider implements ILabelProvider {

			private final int maxSnippetLength;
			private final List<ILabelProviderListener> listeners = new ArrayList<ILabelProviderListener>();
			private final IJavaStackFrame stack;
			
			public MyLabelProvider(EvaluatedExpression[] validExprs, IJavaStackFrame stack) {
				int maxSnippetLen = -1;
				for (EvaluatedExpression e: validExprs)
					if (e.getSnippet().length() > maxSnippetLen)
						maxSnippetLen = e.getSnippet().length();
				maxSnippetLength = maxSnippetLen;
				this.stack = stack;
			}

			@Override
			public void addListener(ILabelProviderListener listener) {
				listeners.add(listener);
			}

			@Override
			public void dispose() {
			}

			@Override
			public boolean isLabelProperty(Object element, String property) {
				return false;
			}

			@Override
			public void removeListener(ILabelProviderListener listener) {
				listeners.remove(listener);
			}

			@Override
			public Image getImage(Object element) {
				return null;
			}

			@Override
			public String getText(Object element) {
				EvaluatedExpression cur = (EvaluatedExpression)element;
				String str = cur.getSnippet();
				for (int i = str.length(); i < maxSnippetLength; i++)
					str += " ";
				str += "  " + getValue(cur, stack);
				return str;
			}
			
		};
		
		public List<EvaluatedExpression> getUserFilteredCandidates() {
	        List<EvaluatedExpression> finalExpressions = null;
	        if (dialog.open() == Window.OK) {
	        	Object[] selected = dialog.getResult();
	        	finalExpressions = new ArrayList<EvaluatedExpression>(selected.length);
	        	for (Object e: selected)
	        		finalExpressions.add((EvaluatedExpression)e);
	        }
	        return finalExpressions;
		}
	}

	private static class ChoiceBreakpointListener implements IDebugEventSetListener {
	    
	    private final static ASTParser parser = ASTParser.newParser(AST.JLS4);
	    private final static Pattern choosePattern = Pattern.compile("\\s*(?:(\\w+)\\s+)?([\\w\\[\\].]+)\\s*=\\s*(CodeHint.choose.*);\\s*\\r?\\n\\s*");
	    
	    @Override
		public void handleDebugEvents(DebugEvent[] events) {
       		IDocument document = getDocument();
	    	for (DebugEvent event : events) {
	            Object source= event.getSource();
	            if (source instanceof IThread && event.getKind() == DebugEvent.SUSPEND &&
	                    event.getDetail() == DebugEvent.BREAKPOINT) {
	                IThread thread = (IThread) source;
	                try {
		                IJavaStackFrame frame = (IJavaStackFrame)thread.getTopStackFrame();
	                   	if (frame == null)
	                   		continue;
	                   	int line = frame.getLineNumber() - 1 ;
	
	                	//TODO: Expression could be spread across multiple lines
	               		int offset = document.getLineOffset(line);
	               		int length = document.getLineLength(line);
	               		String fullCurLine = document.get(offset, length);
	               		
	               		Matcher matcher = choosePattern.matcher(fullCurLine);
	               		if(matcher.matches()) {
	               			refineExpressions(frame, matcher, thread, line);
	               			return;  // We often get duplicate events that would trigger this, but they must all be equivalent, so only handle the first. 
	               		}
	               		
	               		checkLineForSpecs(fullCurLine, frame);
					} catch (DebugException e) {
						e.printStackTrace();
			        	EclipseUtils.showError("Error", "An error occurred during refinement.", e);
						assert false;
					} catch (BadLocationException e) {
						throw new RuntimeException(e);
					}
	            }
	        }
	        
	    }

		private static void refineExpressions(IJavaStackFrame frame, Matcher matcher, IThread thread, int line) throws DebugException {
	    	String curLine = matcher.group(0).trim();
   			
	    	EclipseUtils.log("Beginning refinement for " + curLine + ".");
	    	
   			final String varname = matcher.group(2);
   			// TODO: Ensure lhsVar is always non-null by using JDIPlaceholderVariable if necessary?
	    	final IJavaVariable lhsVar = getLHSVariable(EclipseUtils.parseExpr(parser, varname), frame);
   			// This is the declared type while vartype is the type of the array.  The difference is that if the static type is a primitive, the array type is the wrapper class.
	    	IJavaType varStaticType = lhsVar != null ? lhsVar.getJavaType() : null;
   			String varStaticTypeName = lhsVar != null ? EclipseUtils.sanitizeTypename(lhsVar.getReferenceTypeName()) : matcher.group(1);
        	
   			// Parse the expression.
   			ASTNode node = EclipseUtils.parseExpr(parser, matcher.group(3));
   			// Get the possible expressions in a generic list.
   			Iterator<?> it = ((MethodInvocation)node).arguments().iterator();
   			ArrayList<TypedExpression> initialExprs = new ArrayList<TypedExpression>();
   			while (it.hasNext())
   				initialExprs.add(new TypedExpression((Expression)it.next(), varStaticType, null));
        	assert initialExprs.size() > 0;  // We must have at least one expression.
        	// TODO: Run the following off the UI thread like above when we do the first synthesis.
   			ArrayList<EvaluatedExpression> exprs = EvaluationManager.evaluateExpressions(initialExprs, frame, null, new NullProgressMonitor());
   			if (exprs.isEmpty()) {
   				EclipseUtils.showError("No valid expressions", "No valid expressions were found.", null);
   				throw new RuntimeException("No valid expressions");
   			}

   			Property initialProperty = initialDemonstrations.containsKey(curLine) ? initialDemonstrations.get(curLine) : null;
   			
   			IJavaValue value = null;
   			String newLine = null;
   			boolean automatic;
   			List<EvaluatedExpression> finalExprs = exprs;
   			            			
   			// If all expressions evaluate to the same value, use that and move on.
   			if (allHaveSameResult(exprs)) {
   				if (exprs.size() < initialExprs.size())  // Some expressions crashed, so remove them from the code.
           			newLine = rewriteLine(matcher, varname, curLine, initialProperty, exprs, line);
   				value = exprs.get(0).getResult();
   				automatic = true;
   			} else {
       			// TODO: Default the box to something useful (like most common answer) when disagreement occurs
    	    	// TODO: Do we want to ensure that the user enters a value we expect?
   	   			// Forcibly redraw the screen to ensure we see the newest information.  I don't know why, but putting this in an async seems to work better than in a sync.
   				Display.getDefault().asyncExec(new Runnable() {
   	   				@Override
					public void run() {
   	   					Display.getDefault().update();
   	   				}
   	   			});
       			// Get the new concrete value from the user.
   				Property property = getPropertyFromUser(varname, varStaticType, varStaticTypeName, "\nPotential values are: " + getLegalValues(exprs, frame), frame, initialProperty);
       			if (property == null) {
       				//The user cancelled, just drop back into the debugger and let the 
       				//use do what they want.  Attempting to execute the line will result
       				//in a crash anyway, so they can't screw anything up
       				EclipseUtils.log("Ending refinement for " + curLine + " because the user told us to cancel.");
       				return;
       			}
       			
       			// Filter out the expressions that do not give the desired value in the current context.
       			ArrayList<EvaluatedExpression> validExprs = null;
       			try {
       				validExprs = EvaluationManager.filterExpressions(exprs, frame, property);
       			} catch (EvaluationError e) {
       		    	setLastCrashedInfo(varname, property, null);
       		    	EclipseUtils.showError("Error", e.getMessage(), e);
					throw e;
				}
       	    	setLastCrashedInfo(null, null, null);
       			if (validExprs.isEmpty()) {
       				EclipseUtils.showError("Error", "No legal expressions remain after refinement.", null);
       				throw new RuntimeException("No legal expressions remain after refinement");
       			}
   				value = validExprs.get(0).getResult();  // The returned values could be different, so we arbitrarily use the first one.  This might not be the best thing to do.
       			
       			newLine = rewriteLine(matcher, varname, curLine, property, validExprs, line);

       			if (validExprs.size() == 1) {
           			// If there's only a single possibility remaining, remove the breakpoint.
           			assert thread.getBreakpoints().length > 0;
           			// If there are multiple breakpoints at this line, only remove one (presumably the user added the others).
           			IBreakpoint curBreakpoint = thread.getBreakpoints()[0];
           			try {
						DebugPlugin.getDefault().getBreakpointManager().removeBreakpoint(curBreakpoint, true);
					} catch (CoreException e) {
						e.printStackTrace();
						throw new RuntimeException("Cannot delete breakpoint.");
					}
       			}

   				automatic = false;
   				finalExprs = validExprs;
   			}
   			
   			// TODO: Handle case when this is null (maybe do it above when we workaround it being null).  Creating a JDILocalVariable requires knowing about where the current scope ends, though.
   			if (lhsVar != null)
   				lhsVar.setValue(value);

   			EclipseUtils.log("Ending refinement for " + curLine + (automatic ? " automatically" : "") + ".  " + (newLine == null ? "Statement unchanged." : "Went from " + initialExprs.size() + " expressions to " + finalExprs.size() + ".  New statement: " + newLine));
        	
   			// Immediately continue the execution.
   			thread.resume();
	    }
	    
	    private static void checkLineForSpecs(String fullCurLine, final IJavaStackFrame stack) {
	    	final Matcher statePropertyMatcher = DemonstrateStatePropertyHandler.PATTERN.matcher(fullCurLine);
       		if(statePropertyMatcher.matches()) {
            	Display.getDefault().asyncExec(new Runnable(){
    				@Override
    				public void run() {
                    	DemonstrateStatePropertyHandler.handleFromText(statePropertyMatcher, stack);
    				}
            	});
            	return;
       		}

	    	final Matcher valuePropertyMatcher = DemonstrateValueHandler.PATTERN.matcher(fullCurLine);
       		if(valuePropertyMatcher.matches()) {
            	Display.getDefault().asyncExec(new Runnable(){
    				@Override
    				public void run() {
                    	DemonstrateValueHandler.handleFromText(valuePropertyMatcher, stack);
    				}
            	});
            	return;
       		}

	    	final Matcher typePropertyMatcher = DemonstrateTypeHandler.PATTERN.matcher(fullCurLine);
       		if(typePropertyMatcher.matches()) {
            	Display.getDefault().asyncExec(new Runnable(){
    				@Override
    				public void run() {
                    	DemonstrateTypeHandler.handleFromText(typePropertyMatcher, stack);
    				}
            	});
            	return;
       		}
		}

		private static String rewriteLine(Matcher matcher, final String varname, String curLine, Property property, ArrayList<EvaluatedExpression> validExprs, final int line) {
			List<String> newExprsStrs = EvaluatedExpression.snippetsOfEvaluatedExpressions(validExprs);
			String varDeclaration = matcher.group(1) != null ? matcher.group(1) + " " : "";
			final String newLine = varDeclaration + generateChooseOrChosenStmt(varname, newExprsStrs);
			// If we don't execute this on the UI thread we get an exception, although it still works.
			Display.getDefault().syncExec(new Runnable() {
				@Override
				public void run() {
					try {
						EclipseUtils.replaceLine(newLine, line);
					} catch (BadLocationException e) {
						throw new RuntimeException(e);
					}
				}
			});
   			initialDemonstrations.remove(curLine);
			initialDemonstrations.put(newLine, property);
			return newLine;
		}
	    
	    /**
	     * Checks whether all the evaluated expressions have the same results.
	     * @param exprs The list of evaluated expressions.
	     * @return Whether or not all the given evaluated expressions
	     * have the same results.
	     */
	    private static boolean allHaveSameResult(ArrayList<EvaluatedExpression> exprs) { 
	    	IJavaValue first = exprs.get(0).getResult();  // For efficiency, let's only do one cast.
	    	for (int i = 1; i < exprs.size(); i++)
	    		if (!first.equals(exprs.get(i).getResult()))
	    			return false;
	    	return true;
	    }
	    
	    private static IDocument getDocument() {
	    	final IDocument[] result = new IDocument[] { null };
        	Display.getDefault().syncExec(new Runnable(){
				@Override
				public void run() {
                	result[0] = EclipseUtils.getDocument();
				}
        	});
        	return result[0];
	    }
	    
	    private static Property getPropertyFromUser(final String varName, final IJavaType varStaticType, final String varStaticTypeName, final String extraMessage, final IJavaStackFrame stackFrame, final Property oldProperty) {
	    	final Property[] result = new Property[] { null };
	    	Display.getDefault().syncExec(new Runnable() {
	    		@Override
	    		public void run() {
	    			try {
	    				Shell shell = EclipseUtils.getShell();
	    				SynthesisDialog dialog = null;
		    			if (oldProperty == null || oldProperty instanceof StateProperty)
		    				dialog = new StatePropertyDialog(varName, varStaticTypeName, stackFrame, shell, oldProperty == null ? "" : oldProperty.toString(), extraMessage, false);
		    			else if (oldProperty instanceof PrimitiveValueProperty)
		    				dialog = new PrimitiveValuePropertyDialog(varName, varStaticTypeName, stackFrame, shell, "", extraMessage, false);
		    			else if (oldProperty instanceof ObjectValueProperty)
		    				dialog = new ObjectValuePropertyDialog(varName, varStaticTypeName, stackFrame, shell, "", extraMessage, false);
		    			else if (oldProperty instanceof TypeProperty)
		    				dialog = new TypePropertyDialog(varName, varStaticTypeName, stackFrame, shell, ((TypeProperty)oldProperty).getTypeName(), extraMessage, false);
		    			else if (oldProperty instanceof LambdaProperty)
		    				dialog = new LambdaPropertyDialog(varName, varStaticType.getName(), varStaticType, stackFrame, shell, oldProperty.toString(), extraMessage, false);
		    			dialog.open();
		    			result[0] = dialog.getProperty();
	    			} catch (DebugException e) {
	    				throw new RuntimeException(e);
	    			}
	    		}
	    	});
	    	return result[0];
	    }
	    
	    /**
	     * Gets the variable representing the given LHS
	     * from the given stack frame.  This can be either
	     * a simple variable, a field access, or an array access.
	     * @param curNode The AST node.
	     * @param frame The stack frame.
	     * @return The variable representing the given node
	     * in the given stack frame.
	     * @throws DebugException
	     */
	    private static IJavaVariable getLHSVariable(ASTNode curNode, IJavaStackFrame frame) throws DebugException {
	    	if (curNode instanceof SimpleName)
	    		return frame.findVariable(((SimpleName)curNode).getIdentifier());
	    	else if (curNode instanceof ArrayAccess) {
	    		ArrayAccess array = (ArrayAccess)curNode;
	    		if (array.getIndex() instanceof NumberLiteral) {
	    			int index = Integer.parseInt(((NumberLiteral)array.getIndex()).getToken());
	    			IJavaVariable parentVar = getLHSVariable(array.getArray(), frame);
	    			return (IJavaVariable)((IJavaArray)parentVar.getValue()).getVariable(index);
	    		} else  // Non-constant index, so we can't do anything.
	    			return null;
	    	} else if (curNode instanceof QualifiedName) {  // (simple) field access
	    		QualifiedName name = (QualifiedName)curNode;
	    		IJavaVariable parentVar = getLHSVariable(name.getQualifier(), frame);
	    		return ((IJavaObject)parentVar.getValue()).getField(name.getName().getIdentifier(), false);
	    	} else if (curNode instanceof FieldAccess) {  // (complex) field access
	    		FieldAccess access = (FieldAccess)curNode;
	    		IJavaVariable parentVar = getLHSVariable(access.getExpression(), frame);
	    		return ((IJavaObject)parentVar.getValue()).getField(access.getName().getIdentifier(), false);
	    	}
	    	return null;
	    }
	    
	    private static String getLegalValues(List<EvaluatedExpression> exprs, IJavaStackFrame stack) {
	    	StringBuilder sb = new StringBuilder();
	    	for (EvaluatedExpression e: exprs) {
	    		if (sb.length() > 0)
	    			sb.append(", ");
	    		sb.append(getValue(e, stack));
	    	}
	    	return sb.toString();
	    }
	    
	}
	
	private static String getValue(EvaluatedExpression cur, IJavaStackFrame stack) {
		try {
			// TODO-optimization: I can compute this in EvaluationManager (only if the spec is true) and store it in the EvaluatedExpression to reduce overheads.
			if (cur.getResult() instanceof IJavaPrimitiveValue)
				return EclipseUtils.javaStringOfValue(cur.getResult());
			else if (cur.getResult().isNull())
				return "null";
			else if (cur.getResult() instanceof IJavaArray)
				return EclipseUtils.evaluate("java.util.Arrays.toString(" + cur.getSnippet() + ")", stack).getValueString();
			else if ("Ljava/lang/String;".equals(cur.getResult().getSignature()))
				return EclipseUtils.javaStringOfValue(EclipseUtils.evaluate("(" + cur.getSnippet() + ").toString()", stack));
			else
				return EclipseUtils.evaluate("(" + cur.getSnippet() + ").toString()", stack).getValueString();
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}
	
    private static String generateChooseOrChosenStmt(String varname, List<String> expressions) {
    	String functionName = "CodeHint." + (expressions.size() == 1 ? "chosen" : "choose");
		String statement = varname + " = " + functionName + "(";
		String newExprsString = expressions.toString();
		newExprsString = newExprsString.substring(1, newExprsString.length() - 1);
		statement += newExprsString;
		statement += ");";
		return statement;
    }

    private static ChoiceBreakpointListener listener;
	
    //Called externally (from the plugin startup) to register our breakpoint listener
    public static void start() {
    	if (listener == null)
    		listener = new ChoiceBreakpointListener();
    	initialDemonstrations = new HashMap<String, Property>();
    	setLastCrashedInfo(null, null, null);
    	DebugPlugin.getDefault().addDebugEventListener(listener);
    	ExpressionGenerator.initBlacklist();
    }

    //Called externally (from the plugin shutdown) to unregister our breakpoint listener
    public static void stop() {
    	DebugPlugin.getDefault().removeDebugEventListener(listener);
    	listener = null;
    	initialDemonstrations = null;
    	setLastCrashedInfo(null, null, null);
    	ExpressionGenerator.clearBlacklist();
    }
    
    public static Property getLastCrashedProperty(String varName) {
    	if (lastCrashedVariable != null && lastCrashedVariable.equals(varName))
    		return lastCrashedProperty;
    	else
    		return null;
    }
    
    public static ExpressionSkeleton getLastCrashedSkeleton(String varName) {
    	if (lastCrashedVariable != null && lastCrashedVariable.equals(varName))
    		return lastCrashedSkeleton;
    	else
    		return null;
    }
    
    public static void setLastCrashedInfo(String varName, Property property, ExpressionSkeleton skeleton) {
    	lastCrashedVariable = varName;
    	lastCrashedProperty = property;
    	lastCrashedSkeleton = skeleton;
    }

}
