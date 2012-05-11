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
import org.eclipse.jdt.core.Signature;
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
import org.eclipse.jdt.internal.debug.core.model.JDIDebugTarget;
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

import codehint.expreval.EvaluatedExpression;
import codehint.expreval.EvaluationManager;
import codehint.expreval.EvaluationManager.EvaluationError;
import codehint.exprgen.ExpressionGenerator;

public class Synthesizer {
    
    /* TODO LIST:
     * - Remove dead/redundant code
     * - Extract helper files
     * - On second pass need to figure out which choice tags are used
     */
	
	private static Map<String, Property> initialDemonstrations;
	// We store the last property we have seen demonstrated demonstrated while we are processing it.  If everything works, we clear this, but if there is an error, we keep this and use it as the initial value for the user's next demonstrated property.
	private static Property lastDemonstratedProperty;
	
	public static void synthesizeAndInsertExpressions(final IVariable variable, final String fullVarName, final Property property, final IJavaValue demonstration, Shell shell, final boolean replaceCurLine) {
		System.out.println("Beginning synthesis for " + variable.toString() + " with property " + property.toString() + ".");

		lastDemonstratedProperty = property;

		final IJavaStackFrame frame = EclipseUtils.getStackFrame();

		// Compute a list of expressions that generate that value as possible choices for expressions.
		Job job = new Job("Expression generation") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					//PAR TODO - Is this the point to configure the save behavior?
					JDIDebugTarget target = (JDIDebugTarget)variable.getDebugTarget();
					IJavaType varStaticType = ((IJavaVariable)variable).getJavaType();

					final List<EvaluatedExpression> validExpressions = ExpressionGenerator.generateExpression(target, frame, property, demonstration, varStaticType, monitor);

					if (validExpressions.isEmpty()) {
						lastDemonstratedProperty = null;
						return new Status(IStatus.ERROR, Activator.PLUGIN_ID, "No valid expressions were found.");
					}
					//System.out.println("Valid expressions: " + validExpressionStrings.toString());

			        UIJob job = new UIJob("Inserting synthesized code"){
                        @Override
                        public IStatus runInUIThread(IProgressMonitor monitor) {
							try {
								CandidateSelector candidateSelector = new CandidateSelector(validExpressions.toArray(new EvaluatedExpression[0]));
								List<EvaluatedExpression> finalExpressions = candidateSelector.getUserFilteredCandidates();

						        if (finalExpressions == null) {
									lastDemonstratedProperty = null;
									return Status.CANCEL_STATUS;
						        } else if (finalExpressions.isEmpty()) {
									lastDemonstratedProperty = null;
									return new Status(IStatus.ERROR, Activator.PLUGIN_ID, "No valid expressions were found.");
						        }
								
								List<String> validExpressionStrings = EvaluatedExpression.snippetsOfEvaluatedExpressions(finalExpressions);
								
								//Construct the textual line to insert.  Working in text seems easier than
								// using the AST manipulators for now, but this may need revisited later.  
								//TODO: For non primitive values, the static type would be wrong.  Need to get the _dynamic_ type
								// of the variable
								String statement = generateChooseOrChosenStmt(fullVarName, validExpressionStrings);
			
								//Insert the new text
								try {
									if (replaceCurLine)
										EclipseUtils.replaceLineAtCurrentDebugPoint(statement);
									else
										EclipseUtils.insertIndentedLineAtCurrentDebugPoint(statement);
								} catch (BadLocationException e1) {
									// TODO Auto-generated catch block
									e1.printStackTrace();
									assert false;
								}
			
								//Insert ourselves a breakpoint so that we stop here again
								// Note: Really we'd like to break only on a trace without an override.  Can we do this?
			
								//Don't want to actually change the value.  The invocation of choose (that we just inserted) will do that.
			
								//Insert breakpoint so we can find this line again
								//get the current frame from the current active editor to get the cursor line
								// Note: This is not how the variable visit does this, 
								// hopefully, we get the same value
			
								int line = frame.getLineNumber();  // Comment from Eclipse sources: Document line numbers are 0-based. Debug line numbers are 1-based.
								assert line >= 0;
			
								ITextEditor editor = EclipseUtils.getActiveTextEditor();
								assert editor != null;
			
								String typename = frame.getDeclaringTypeName();
								IResource resource = (IResource)editor.getEditorInput().getAdapter(IResource.class);
			
								try {
									JDIDebugModel.createLineBreakpoint(resource, typename, line, -1, -1, 0, true, null);
								} catch (CoreException e) {
									e.printStackTrace();
									throw new RuntimeException(e.getMessage());
								}
			
								// TODO: Using the text of the statement as a key is not a very good idea.
								initialDemonstrations.put(statement, demonstration != null ? null : property);
								
								IJavaValue value = demonstration != null ? demonstration : validExpressions.get(0).getResult();
								if (value != null)
									variable.setValue(value);  // PAR TODO: Philip, Joel added this line.  Is there a reason we don't want it?  We certainly do want to change the value in the current iteration somehow.  (Of course, if we do want this we should probably combine it with the one in the else block below.)
			
								//NOTE: Our current implementation does not save.  Without a manual save, our added 
								// code gets ignored for this invocation.  Should we force a save and restart?
			
								lastDemonstratedProperty = null;
			
								System.out.println("Finishing synthesis for " + variable.toString() + " with property " + property.toString() + ".  Found " + validExpressions.size() + " expressions and inserted " + statement);
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
				} catch (DebugException e) {
					e.printStackTrace();
					EclipseUtils.showError("Error", "An error occurred processing your demonstration.", e);
					throw new RuntimeException(e.getMessage());
				} catch (EvaluationError e) {
					return new Status(IStatus.ERROR, Activator.PLUGIN_ID, e.getMessage());
				} catch (OperationCanceledException e) {
					System.out.println("Cancelling synthesis for " + variable.toString() + " with property " + property.toString() + ".");
					return Status.CANCEL_STATUS;
				}
			}
		};
		job.setPriority(Job.LONG);
		job.setUser(true);
		job.schedule();
	}
	
	// TODO: Make the view prettier when it shows the values.  Perhaps http://stackoverflow.com/questions/8862589/how-to-add-multiple-columns-in-listselectiondialog-in-eclipse.
	private static class CandidateSelector {
		
		private final ListSelectionDialog dialog;
		
		public CandidateSelector(EvaluatedExpression[] validExprs) {
			dialog = new MyDialog(EclipseUtils.getShell(), "", new MyContentProvider(validExprs), new MyLabelProvider(validExprs), "Select the candidates to keep.  The expressions are on the left and their values are on the right.  Objects have their toStrings shown.");
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
			
			public MyLabelProvider(EvaluatedExpression[] validExprs) {
				int maxSnippetLen = -1;
				for (EvaluatedExpression e: validExprs)
					if (e.getSnippet().length() > maxSnippetLen)
						maxSnippetLen = e.getSnippet().length();
				maxSnippetLength = maxSnippetLen;
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
				str += "  " + getValue(cur);
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
	    
	    /* (non-Javadoc)
	     * @see org.eclipse.debug.core.IDebugEventSetListener#handleDebugEvents(org.eclipse.debug.core.DebugEvent[])
	     */
	    @Override
		public void handleDebugEvents(DebugEvent[] events) {
       		IDocument document = getDocument();
	    	for (DebugEvent event : events) {
	            Object source= event.getSource();
	            if (source instanceof IThread && event.getKind() == DebugEvent.SUSPEND &&
	                    event.getDetail() == DebugEvent.BREAKPOINT) {
	                IThread thread = (IThread) source;
	                //PAR TODO: Is this an event we care about?
	                try {
	                   	IJavaStackFrame frame = (IJavaStackFrame)thread.getTopStackFrame();
	                   	if (frame == null)
	                   		continue;
	                   	int line = frame.getLineNumber() - 1 ;
	                   	assert line >= 0;
	                   	//System.out.println( "Considering breakpoint at line " + line );
	                   	
	                   	
	                   	//Needed only for sanity checking and debugging
	                	int start = frame.getCharEnd();
	                	int end = frame.getCharStart();
	                	assert (start == -1 && end == -1);
	
	                	//TODO: Expression could be spread across multiple lines
	               		int offset = document.getLineOffset(line);
	               		int length = document.getLineLength(line);
	               		String fullCurLine = document.get(offset, length);
	               		
	               		Matcher matcher = choosePattern.matcher(fullCurLine);
	               		if(matcher.matches()) {
	               			refineExpressions(frame, matcher, thread, line);
	               			return;  // We often get duplicate events that would trigger this, but they must all be equivalent, so only handle the first. 
	               		}
					} catch (DebugException e) {
						e.printStackTrace();
			        	EclipseUtils.showError("Error", "An error occurred during refinement.", e);
						assert false;
					} catch (BadLocationException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						assert false;
					}
	            }
	        }
	        
	    }
	    
	    private static void refineExpressions(IJavaStackFrame frame, Matcher matcher, IThread thread, int line) throws DebugException {
	    	String curLine = matcher.group(0).trim();
   			
   			System.out.println("Beginning refinement for " + curLine + ".");
	    	
   			IJavaDebugTarget target = (IJavaDebugTarget)frame.getDebugTarget();
   			final String varname = matcher.group(2);
	    	final IJavaVariable lhsVar = getLHSVariable(EclipseUtils.parseExpr(parser, varname), frame);
   			// This is the declared type while vartype is the type of the array.  The difference is that if the static type is a primitive, the array type is the wrapper class.
   			String varStaticTypeName = lhsVar != null ? EclipseUtils.sanitizeTypename(lhsVar.getReferenceTypeName()) : matcher.group(1);
        	
   			// Parse the expression.
   			ASTNode node = EclipseUtils.parseExpr(parser, matcher.group(3));
   			// Get the possible expressions in a generic list.
   			Iterator<?> it = ((MethodInvocation)node).arguments().iterator();
   			ArrayList<Expression> initialExprs = new ArrayList<Expression>();
   			while (it.hasNext())
   				initialExprs.add((Expression)it.next());
        	assert initialExprs.size() > 0;  // We must have at least one expression.
        	// TODO: Run the following off the UI thread like above when we do the first synthesis.
   			ArrayList<EvaluatedExpression> exprs = EvaluationManager.evaluateExpressions(initialExprs, target, frame, varStaticTypeName, null, new NullProgressMonitor());
   			if (exprs.isEmpty()) {
   				EclipseUtils.showError("No valid expressions", "No valid expressions were found.", null);
   				throw new RuntimeException("No valid expressions");
   			}

   			Property initialProperty = initialDemonstrations.containsKey(curLine) ? initialDemonstrations.get(curLine) : null;
   			boolean demonstratedProperty = initialProperty != null || !initialDemonstrations.containsKey(curLine);  // If we cannot identify the statement, we must be general and ask for a property.
   			
   			IJavaValue value = null;
   			String newLine = null;
   			boolean automatic;
   			List<EvaluatedExpression> finalExprs = exprs;
   			            			
   			// If all expressions evaluate to the same value, use that and move on.
   			if (allHaveSameResult(exprs)) {
   				if (exprs.size() < initialExprs.size())  // Some expressions crashed, so remove them from the code.
           			newLine = rewriteLine(matcher, varname, curLine, initialProperty, demonstratedProperty, exprs, line);
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
       			String varSignature = lhsVar != null ? lhsVar.getSignature() : Signature.createTypeSignature(matcher.group(1), false);
       			Property property = null;
       			if (!demonstratedProperty) {
       				String resultStr = getExpressionFromUser(varname, "", "\nChoose one of: " + getLegalValues(exprs));
       				if (resultStr != null) {
	       				if (EclipseUtils.isPrimitive(varSignature))
		       				property = LambdaProperty.fromPrimitive(resultStr);
	       				else
	       					property = LambdaProperty.fromObject(resultStr);
       				}
       			} else {
       				IJavaType varStaticType = lhsVar == null ? null : lhsVar.getJavaType();
       				String initValue = initialProperty != null ? initialProperty.toString() : null;
       				property = getPropertyFromUser(varname, varStaticType, initValue, "\nPotential values are: " + getLegalValues(exprs), frame, initialProperty);
       			}
       			if (property == null) {
       				//The user cancelled, just drop back into the debugger and let the 
       				//use do what they want.  Attempting to execute the line will result
       				//in a crash anyway, so they can't screw anything up
       	   			System.out.println("Ending refinement for " + curLine + " because the user told us to cancel.");
       				return;
       			}
            	lastDemonstratedProperty = property;
       			
       			// Filter out the expressions that do not give the desired value in the current context.
       			ArrayList<EvaluatedExpression> validExprs = EvaluationManager.filterExpressions(exprs, target, frame, varStaticTypeName, property);
       			if (validExprs.isEmpty()) {
       				EclipseUtils.showError("Error", "No legal expressions remain after refinement.", null);
       				throw new RuntimeException("No legal expressions remain after refinement");
       			}
   				value = validExprs.get(0).getResult();  // The returned values could be different, so we arbitrarily use the first one.  This might not be the best thing to do.
       			
       			newLine = rewriteLine(matcher, varname, curLine, property, demonstratedProperty, validExprs, line);

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

        	lastDemonstratedProperty = null;

    		System.out.println("Ending refinement for " + curLine + (automatic ? " automatically" : "") + ".  " + (newLine == null ? "Statement unchanged." : "Went from " + initialExprs.size() + " expressions to " + finalExprs.size() + ".  New statement: " + newLine));
        	
   			// Immediately continue the execution.
   			thread.resume();
	    }

		private static String rewriteLine(Matcher matcher, final String varname, String curLine, Property property, boolean demonstratedProperty, ArrayList<EvaluatedExpression> validExprs, final int line) {
			List<String> newExprsStrs = EvaluatedExpression.snippetsOfEvaluatedExpressions(validExprs);
			String varDeclaration = matcher.group(1) != null ? matcher.group(1) + " " : "";
			final String newLine = varDeclaration + generateChooseOrChosenStmt(varname, newExprsStrs);
			//System.out.println("New stmt: " + newLine);
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
			initialDemonstrations.put(newLine, demonstratedProperty ? property : null);
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
	    
	    private static String getExpressionFromUser(final String varName, final String initValue, final String extraMessage) {
			final String[] result = new String[] { null };
			Display.getDefault().syncExec(new Runnable() {
				@Override
				public void run() {
					result[0] = EclipseUtils.getExpression(varName, EclipseUtils.getShell(), initValue, extraMessage);
				}
			});
			return result[0];
	    }
	    private static Property getPropertyFromUser(final String varName, final IJavaType varStaticType, final String initValue, final String extraMessage, final IJavaStackFrame stackFrame, final Property oldProperty) {
	    	final Property[] result = new Property[] { null };
	    	Display.getDefault().syncExec(new Runnable() {
	    		@Override
	    		public void run() {
	    			if (oldProperty == null || oldProperty instanceof StateProperty)
	    				result[0] = EclipseUtils.getStateProperty(varName, EclipseUtils.getShell(), initValue, extraMessage);
	    			else if (oldProperty instanceof LambdaProperty)
	    				result[0] = EclipseUtils.getLambdaProperty(varName, EclipseUtils.getShell(), varStaticType, initValue, extraMessage, stackFrame);
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
	    
	    private static String getLegalValues(List<EvaluatedExpression> exprs) {
	    	StringBuilder sb = new StringBuilder();
	    	for (EvaluatedExpression e: exprs) {
	    		if (sb.length() > 0)
	    			sb.append(", ");
	    		sb.append(getValue(e));
	    	}
	    	return sb.toString();
	    }
	    
	}
	
	private static String getValue(EvaluatedExpression cur) {
		try {
			// TODO: This could infinite loop.
			// TODO-optimization: I can compute this in EvaluationManager (only if the spec is true) and store it in the EvaluatedExpression to reduce overheads.
			if (cur.getResult() instanceof IJavaPrimitiveValue)
				return cur.getResult().getValueString();
			else if (cur.getResult().isNull())
				return "null";
			else if (cur.getResult() instanceof IJavaArray)
				return EclipseUtils.evaluate("java.util.Arrays.toString(" + cur.getSnippet() + ")").getValueString();
			else
				return EclipseUtils.evaluate("(" + cur.getSnippet() + ").toString()").getValueString();
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
    	lastDemonstratedProperty = null;
    	DebugPlugin.getDefault().addDebugEventListener(listener);
    	ExpressionGenerator.initBlacklist();
    }

    //Called externally (from the plugin shutdown) to unregister our breakpoint listener
    public static void stop() {
    	DebugPlugin.getDefault().removeDebugEventListener(listener);
    	listener = null;
    	initialDemonstrations = null;
    	lastDemonstratedProperty = null;
    }
    
    public static Property getLastDemonstratedProperty() {
    	return lastDemonstratedProperty;
    }

}
