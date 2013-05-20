package codehint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.eclipse.core.runtime.preferences.InstanceScope;
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
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.jdt.debug.core.JDIDebugModel;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.eclipse.ui.texteditor.ITextEditor;

import codehint.dialogs.InitialSynthesisDialog;
import codehint.dialogs.ObjectValuePropertyDialog;
import codehint.dialogs.PrimitiveValuePropertyDialog;
import codehint.dialogs.PropertyDialog;
import codehint.dialogs.RefinementSynthesisDialog;
import codehint.dialogs.StatePropertyDialog;
import codehint.dialogs.SynthesisDialog;
import codehint.dialogs.TypePropertyDialog;
import codehint.dialogs.SynthesisDialog.SynthesisState;
import codehint.effects.SideEffectHandler;
import codehint.expreval.EvaluationManager;
import codehint.expreval.EvaluationManager.StopSynthesis;
import codehint.expreval.NativeHandler;
import codehint.expreval.TimeoutChecker;
import codehint.expreval.EvaluationManager.EvaluationError;
import codehint.expreval.FullyEvaluatedExpression;
import codehint.exprgen.ExpressionGenerator;
import codehint.exprgen.ExpressionMaker;
import codehint.exprgen.ExpressionSkeleton;
import codehint.exprgen.ExpressionSkeleton.SkeletonError;
import codehint.exprgen.SubtypeChecker;
import codehint.exprgen.TypeCache;
import codehint.exprgen.TypedExpression;
import codehint.exprgen.ValueCache;
import codehint.handler.DemonstrateStatePropertyHandler;
import codehint.handler.DemonstrateTypeHandler;
import codehint.handler.DemonstrateValueHandler;
import codehint.handler.SynthesisStarter;
import codehint.property.ObjectValueProperty;
import codehint.property.PrimitiveValueProperty;
import codehint.property.Property;
import codehint.property.StateProperty;
import codehint.property.TypeProperty;
import codehint.property.ValueProperty;
import codehint.utils.EclipseUtils;
import codehint.utils.Utils;

public class Synthesizer {

	private static final String PHANTOM_BREAKPOINT_QUALIFIER = "org.eclipse.jdt.debug.ui";
	private static final String PHANTOM_BREAKPOINT_PREFNAME = "org.eclipse.jdt.debug.ui.javaDebug.SuspendOnUncaughtExceptions";
	
	private static Map<String, Property> initialDemonstrations;
	private static List<String> addedChosenStmts;
	private static Map<String, String> initialFiles;
	private static ExpressionMaker.Metadata metadata;
	private static Map<String, List<FullyEvaluatedExpression>> initialExprs;
	private static Set<String> handledEffects;
	private static Set<String> blockedNatives;
	
	/**
	 * Synthesizes expressions and inserts them into the code.
	 * It first opens the synthesis dialog, which lets the user
	 * give a pdspec and skeleton and synthesizes and shows the
	 * candidate expressions.
	 * Once the user selects some, it inserts them into the code.
	 * @param variable The variable (really, LHS) that should be 
	 * assigned the synthesized expressions.
	 * @param fullVarName The full name of the variable, which
	 * could be an array access or field dereference.
	 * @param synthesisDialog The dialog that handles the synthesis.
	 * @param stack The current stack frame.
	 * @param replaceCurLine Whether or the current line should
	 * be replaced by the inserted expressions or if they should
	 * be added after it.
	 */
	public static void synthesizeAndInsertExpressions(final IVariable variable, final String fullVarName, final InitialSynthesisDialog synthesisDialog, final IJavaStackFrame stack, final boolean replaceCurLine) {
		try {
			String varString = variable == null ? "" : variable.toString();
			synthesisDialog.open();
			Property property = synthesisDialog.getProperty();
			ExpressionSkeleton skeleton = synthesisDialog.getSkeleton();
			List<FullyEvaluatedExpression> finalExpressions = synthesisDialog.getExpressions();
			ExpressionMaker expressionMaker = synthesisDialog.getExpressionMaker();
			boolean blockedNativeCalls = synthesisDialog.blockedNativeCalls();
			boolean handledSideEffects = synthesisDialog.handledSideEffects();
			synthesisDialog.cleanup();
	        if (finalExpressions == null) {
		    	if (property != null && skeleton != null) {
		    		EclipseUtils.log("Cancelling synthesis for " + varString + " with property " + property.toString() + " and skeleton " + skeleton.toString() + ".");
		    		DataCollector.log("cancel", "spec=" + property.toString(), "skel=" + skeleton.toString());
		    	}
				return;
	        } else if (finalExpressions.isEmpty())
				return;
			
			List<String> validExpressionStrings = FullyEvaluatedExpression.snippetsOfEvaluatedExpressions(finalExpressions);
			
			//Construct the textual line to insert.  Working in text seems easier than
			// using the AST manipulators for now, but this may need revisited later.  
			String statement = generateChooseOrChosenStmt(fullVarName, validExpressionStrings);

			//Insert the new text
			try {
				if (replaceCurLine)
					EclipseUtils.replaceLine(statement, stack.getLineNumber() - 1);
				else
					EclipseUtils.insertIndentedLine(statement, stack.getLineNumber() - 1);
			} catch (BadLocationException e) {
				throw new RuntimeException(e);
			}

			//Insert ourselves a breakpoint so that we stop here again
			// Note: Really we'd like to break only on a trace without an override.  Can we do this?

			//Insert breakpoint so we can find this line again
			//get the current frame from the current active editor to get the cursor line

			int line = stack.getLineNumber();  // Comment from Eclipse sources: Document line numbers are 0-based. Debug line numbers are 1-based.
			assert line >= 0;

			ITextEditor editor = EclipseUtils.getActiveTextEditor();
			assert editor != null;

			String typename = stack.getDeclaringTypeName();
			IResource resource = getResource(editor);

			addBreakpoint(resource, typename, line);

			// TODO: Using the text of the statement as a key is not a very good idea.
			if (finalExpressions.size() > 1) {
				initialDemonstrations.put(statement, property);
				metadata.addMetadataFor(finalExpressions, expressionMaker);
				initialExprs.put(statement, finalExpressions);
				if (blockedNativeCalls)
					blockedNatives.add(statement);
				if (handledSideEffects)
					handledEffects.add(statement);
			}

			IJavaValue value = property instanceof ValueProperty ? ((ValueProperty)property).getValue() : finalExpressions.get(0).getValue();
			if (variable != null && value != null)
				variable.setValue(value);
			
			//NOTE: Our current implementation does not save.  Without a manual save, our added 
			// code gets ignored for this invocation.  Should we force a save and restart?

	    	EclipseUtils.log("Finishing synthesis for " + varString + " with property " + property.toString() + " and skeleton " + skeleton.toString() + ".  Found " + finalExpressions.size() + " user-approved expressions and inserted " + statement);
	    	DataCollector.log("finish", "spec=" + property.toString(), "skel=" + skeleton.toString(), "found=" + finalExpressions.size());
	    	return;
		} catch (DebugException e) {
			e.printStackTrace();
			EclipseUtils.showError("Error", "An error occurred processing your demonstration.", e);
			throw new RuntimeException(e.getMessage());
		}
	}
	
	/**
	 * Class that does the actual synthesis on a separate thread.
	 */
	public static class SynthesisWorker {
		
		private final String varName;
		private final IJavaType varStaticType;
		
		public SynthesisWorker(String varName, IJavaType varStaticType) {
			this.varName = varName;
			this.varStaticType = varStaticType;
		}
		
		/**
		 * Synthesizes the expressions that meet the user's specifications.
		 * It does this on a separate thread and then reports the results
		 * back to the synthesis dialog on the UI thread.
		 * @param synthesisDialog The dialog controlling the synthesis.
		 * @param evalManager The evaluation manager.
		 * @param extraDepth Extra depth to search.
		 * @param timeoutChecker The job that times out long evaluations.
		 * @param blockNatives Whether we are block native calls.
		 * @param sideEffectHandler Class that records and undoes side effects.
		 */
		public void synthesize(final SynthesisDialog synthesisDialog, final EvaluationManager evalManager, final int extraDepth, final TimeoutChecker timeoutChecker, final boolean blockNatives, final SideEffectHandler sideEffectHandler) {
			final Property property = synthesisDialog.getProperty();
			final ExpressionSkeleton skeleton = synthesisDialog.getSkeleton();
			final boolean searchConstructors = synthesisDialog.searchConstructors();
			final boolean searchOperators = synthesisDialog.searchOperators();
			Job job = new Job("Expression generation") {
				@Override
				protected IStatus run(IProgressMonitor monitor) {
					EclipseUtils.log("Beginning synthesis for " + varName + " with property " + property.toString() + " and skeleton " + skeleton.toString() + " with extra depth " + extraDepth + ".");
					DataCollector.log("start", "spec=" + property.toString(), "skel=" + skeleton.toString(), "exdep=" + extraDepth, "cons=" + searchConstructors, "ops=" + searchOperators, "block-natives=" + blockNatives, "handle-effects=" + sideEffectHandler.isEnabled());
					boolean unfinished = false;
					BreakpointDisabler breakpointDisabler = new BreakpointDisabler();
					breakpointDisabler.disableBreakpoints();
					// Ensure that our evaluations do not hit "phantom" breakpoints when they crash.
					IPreferenceStore prefStore = new ScopedPreferenceStore(InstanceScope.INSTANCE, PHANTOM_BREAKPOINT_QUALIFIER);
					boolean oldPrefValue = prefStore.getBoolean(PHANTOM_BREAKPOINT_PREFNAME);
					if (oldPrefValue)
						prefStore.setValue(PHANTOM_BREAKPOINT_PREFNAME, false);
					try {
						evalManager.init();
						sideEffectHandler.start(synthesisDialog.getProgressMonitor());
						skeleton.synthesize(property, varName, varStaticType, extraDepth, searchConstructors, searchOperators, synthesisDialog, synthesisDialog.getProgressMonitor());
			        	return Status.OK_STATUS;
					} catch (EvaluationError e) {
						unfinished = true;
						e.printStackTrace();
						return new Status(IStatus.ERROR, Activator.PLUGIN_ID, e.getMessage());
					} catch (OperationCanceledException e) {
						EclipseUtils.log("Cancelling synthesis for " + varName + " with property " + property.toString() + " and skeleton " + skeleton.toString() + ".");
						DataCollector.log("cancel", "spec=" + property.toString(), "skel=" + skeleton.toString(), "exdep=" + extraDepth, "cons=" + searchConstructors, "ops=" + searchOperators, "block-natives=" + blockNatives, "handle-effects=" + sideEffectHandler.isEnabled());
						unfinished = true;
						return Status.CANCEL_STATUS;
					} catch (SkeletonError e) {
						EclipseUtils.showError("Error", e.getMessage(), null);
						unfinished = true;
						return Status.CANCEL_STATUS;
					} catch (StopSynthesis e) {
						unfinished = true;
						return Status.CANCEL_STATUS;  // We want to quit silently here, not throw an exception to the user.
					} catch (RuntimeException e) {
						unfinished = true;
						throw e;
					} catch (Exception e) {
						unfinished = true;
						throw new RuntimeException(e);
					} finally {
						breakpointDisabler.reenableBreakpoints();
						timeoutChecker.stop();
						sideEffectHandler.stop(synthesisDialog.getProgressMonitor());
						final InitialSynthesisDialog.SynthesisState state = unfinished ? InitialSynthesisDialog.SynthesisState.UNFINISHED : InitialSynthesisDialog.SynthesisState.END;
						if (oldPrefValue)
							prefStore.setValue(PHANTOM_BREAKPOINT_PREFNAME, oldPrefValue);
	                	synthesisDialog.endSynthesis(state);
						tryToResetEvalManager(synthesisDialog, evalManager);
					}
				}
			};
			job.setPriority(Job.LONG);
			//job.setUser(true);
			job.schedule();
			timeoutChecker.start();
		}
		
	}
	
	private static class BreakpointDisabler {
		
		private IBreakpoint[] breakpoints;
		private boolean[] breakpointsEnabled;
		
		public void disableBreakpoints() {
			breakpoints = DebugPlugin.getDefault().getBreakpointManager().getBreakpoints();
			breakpointsEnabled = new boolean[breakpoints.length];
			for (int i = 0; i < breakpoints.length; i++) {
				try {
					boolean isEnabled = breakpoints[i].isEnabled();
					breakpointsEnabled[i] = isEnabled;
					if (isEnabled)
						breakpoints[i].setEnabled(false);
				} catch (CoreException e) {
					e.printStackTrace();
				}
			}
		}
		
		public void reenableBreakpoints() {
			for (int i = 0; i < breakpoints.length; i++) {
				try {
					if (breakpointsEnabled[i])
						breakpoints[i].setEnabled(true);
				} catch (CoreException e) {
					e.printStackTrace();
				}
			}
		}
		
	}
	
	/**
	 * Class that refines the given expressions to keep only those
	 * that meet the user's specifications.
	 */
	public static class RefinementWorker {
		
		private final ArrayList<TypedExpression> typedExprs;
		private final IJavaType varType;
		private final TypeCache typeCache;
		private ArrayList<FullyEvaluatedExpression> exprs;
		
		/**
		 * Creates a new RefinementWorker that will filter
		 * the given expressions.
		 * @param typedExprs The candidate expressions to filter.
		 * @param varType The type of the variable being
		 * assigned.
		 * @param typeCache The type cache.
		 */
		public RefinementWorker(ArrayList<TypedExpression> typedExprs, IJavaType varType, TypeCache typeCache) {
			this.typeCache = typeCache;
			this.typedExprs = typedExprs;
			this.varType = varType;
		}
		
		public void evaluateLine(final boolean blockedNatives, final boolean handledEffects, final RefinementSynthesisDialog synthesisDialog, final IJavaStackFrame frame, final IJavaThread thread) {
			Job job = new Job("Expression generation") {
				@Override
				protected IStatus run(IProgressMonitor monitor) {
					IJavaDebugTarget target = (IJavaDebugTarget)frame.getDebugTarget();
					ValueCache valueCache = new ValueCache(target);
					NativeHandler nativeHandler = blockedNatives ? new NativeHandler(thread, frame, target, typeCache) : null;
					BreakpointDisabler breakpointDisabler = new BreakpointDisabler();
					SideEffectHandler effectHandler = handledEffects ? new SideEffectHandler(frame, EclipseUtils.getProject(frame)) : null;
					// TODO: Run the following off the UI thread like above when we do the first synthesis.
					TimeoutChecker timeoutChecker = new TimeoutChecker(thread, frame, target, typeCache);
					EvaluationManager evalManager = new EvaluationManager(false, nativeHandler == null, frame, new ExpressionMaker(frame, valueCache, typeCache, timeoutChecker, null, null, metadata), new SubtypeChecker(frame, target, typeCache), typeCache, valueCache, timeoutChecker);
					evalManager.init();
					try {
						timeoutChecker.start();
						if (nativeHandler != null) {
							breakpointDisabler.disableBreakpoints();
							nativeHandler.enable(true);
							nativeHandler.blockNativeCalls();
						}
						if (effectHandler != null) {
							effectHandler.enable(true);
							effectHandler.start(synthesisDialog.getProgressMonitor());
							effectHandler.startHandlingSideEffects();
						}
						exprs = evalManager.evaluateExpressions(typedExprs, null, null, synthesisDialog, synthesisDialog.getProgressMonitor(), "Evaluating previous results");
						return Status.OK_STATUS;
					} catch (RuntimeException e) {
						throw e;
					} finally {
						if (nativeHandler != null) {
							breakpointDisabler.reenableBreakpoints();
							nativeHandler.allowNativeCalls();
						}
						if (effectHandler != null) {
							effectHandler.stop(synthesisDialog.getProgressMonitor());
							effectHandler.stopHandlingSideEffects();
						}
						timeoutChecker.stop();
						synthesisDialog.setInitialRefinementExpressions(exprs);
						tryToResetEvalManager(synthesisDialog, evalManager);
					}
				}
			};
			job.setPriority(Job.LONG);
			job.schedule();
		}

		/**
		 * Refines the current expressions and keeps only those
		 * that meet the user's specifications.
		 * @param synthesisDialog The dialog controlling the synthesis.
		 * @param evalManager The evaluation manager.
		 */
		public void refine(RefinementSynthesisDialog synthesisDialog, EvaluationManager evalManager) {
			Property property = synthesisDialog.getProperty();
   			try {
   				evalManager.evaluateExpressions(exprs, property, varType, synthesisDialog, synthesisDialog.getProgressMonitor(), "");
   				synthesisDialog.endSynthesis(SynthesisState.END);
   			} catch (EvaluationError e) {
   		    	EclipseUtils.showError("Error", e.getMessage(), e);
				throw e;
			}
		}
		
	}

	private static void tryToResetEvalManager(final SynthesisDialog synthesisDialog, final EvaluationManager evalManager) {
		if (!evalManager.resetFields())
			Display.getDefault().asyncExec(new Runnable(){
				@Override
				public void run() {
					synthesisDialog.close();
				}
			});
	}

	/**
	 * A class that handles refinement and pdspecs in code.
	 */
	private static class ChoiceBreakpointListener implements IDebugEventSetListener {
	    
	    private final static ASTParser parser = ASTParser.newParser(AST.JLS4);
	    private final static Pattern choosePattern = Pattern.compile("\\s*(?:(\\w+)\\s+)?([\\w\\]\\[.]+)\\s*=\\s*(CodeHint.choose.*);\\s*\\r?\\n\\s*");
	    
	    @Override
		public void handleDebugEvents(DebugEvent[] events) {
	    	for (DebugEvent event : events) {
	            Object source= event.getSource();
	            if (source instanceof IThread && event.getKind() == DebugEvent.SUSPEND &&
	                    event.getDetail() == DebugEvent.BREAKPOINT) {
	                IThread thread = (IThread) source;
	                try {
		                IJavaStackFrame frame = (IJavaStackFrame)thread.getTopStackFrame();
	                   	if (frame == null || frame.getLineNumber() == -1)
	                   		continue;
	                   	int line = frame.getLineNumber() - 1 ;

	               		IDocument document = getDocument();
	               		String fullCurLine = EclipseUtils.getTextAtLine(document, line);
	               		
	               		Matcher matcher = choosePattern.matcher(fullCurLine);
	               		if(matcher.matches()) {
	               			IBreakpoint[] breakpoints = thread.getBreakpoints();  // Cache this, since some things I do before I use it might reset it.
	                    	TypeCache typeCache = new TypeCache();
                    		refineExpressions(frame, matcher, thread, line, breakpoints, typeCache);
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
	            } else if (source instanceof IJavaDebugTarget && event.getKind() == DebugEvent.TERMINATE) {
	            	// Remove the breakpoints we added automatically, if any.
					try {
						for (IBreakpoint bp: addedBreakpoints)
							bp.delete();
		            	addedBreakpoints.clear();
					} catch (CoreException e) {
						throw new RuntimeException(e);
					}
					// Remove chosen statements, any text we added, and save if possible.
					Display.getDefault().asyncExec(new Runnable(){
						@Override
						public void run() {
							try {
								final ITextEditor editor = EclipseUtils.getActiveTextEditor();
								String resourceName = getResourceName(editor);
								IDocument document = EclipseUtils.getDocument();
								String text = document.get();
								String cleanText = text;
								int numChosenStmts = addedChosenStmts.size();
								for (int i = addedChosenStmts.size() - 1; i >= 0; i--) {
									String chosenStmt = addedChosenStmts.get(i);
									int index = text.indexOf(chosenStmt);
									if (index != -1) {  // Replace "CodeHint.chosen(...)" with "...".
										document.replace(index, chosenStmt.length(), chosenStmt.substring("CodeHint.chosen(".length(), chosenStmt.length() - 1));
										int startIndex = index - 1;
										while (cleanText.charAt(startIndex) != '\n')
											startIndex--;
										int endIndex = index + chosenStmt.length();
										while (cleanText.charAt(endIndex) != '\n')
											endIndex++;
										cleanText = cleanText.substring(0, startIndex) + cleanText.substring(endIndex);  // Remove the chosen call completely to see if anything else has changed.
									}
									addedChosenStmts.remove(i);
								}
								String initialFile = initialFiles.get(resourceName);
								if (initialFile != null) {
									if (numChosenStmts > 0 && initialFile.equals(cleanText))
										Display.getDefault().asyncExec(new Runnable(){  // Save asynchronously, since it takes a noticeable amount of time and delays e.g., updating the document's text.
											@Override
											public void run() {
												editor.doSave(null);
											}
										});
									initialFiles.remove(resourceName);
								}
							} catch (BadLocationException e) {
								throw new RuntimeException(e);
							}
						}
					});
					SynthesisStarter.cleanup();
	            } else if (source instanceof IJavaDebugTarget && event.getKind() == DebugEvent.CREATE) {
					String resourceName = getResourceName(getEditor());
	    	        if (!initialFiles.containsKey(resourceName))
	    	        	initialFiles.put(resourceName, getDocument().get());
	            }
	        }
	        
	    }

	    /**
	     * Refines the choose expression on the given line.
	     * @param frame The stack frame.
	     * @param matcher A Matcher that has parsed the current line.
	     * @param thread The current thread.
	     * @param lineNumber The source code line that is being refined.
	     * @param breakpoints The breakpoints that caused this thread
	     * to suspend.
	     * @throws DebugException
	     */
		private static void refineExpressions(IJavaStackFrame frame, Matcher matcher, IThread thread, int lineNumber, IBreakpoint[] breakpoints, TypeCache typeCache) throws DebugException {
	    	String curLine = matcher.group(0).trim();
   			
	    	EclipseUtils.log("Beginning refinement for " + curLine + ".");
	    	DataCollector.log("refine-start");
	    	
   			final String varname = matcher.group(2);
   			// TODO: Ensure lhsVar is always non-null by using JDIPlaceholderVariable if necessary?
	    	final IJavaVariable lhsVar = getLHSVariable(EclipseUtils.parseExpr(parser, varname), frame);
   			// This is the declared type while vartype is the type of the array.  The difference is that if the static type is a primitive, the array type is the wrapper class.
	    	IJavaType varStaticType = lhsVar != null ? lhsVar.getJavaType() : null;
   			String varStaticTypeName = lhsVar != null ? EclipseUtils.sanitizeTypename(lhsVar.getReferenceTypeName()) : matcher.group(1);

   			ArrayList<TypedExpression> typedExprs = new ArrayList<TypedExpression>();
   			List<FullyEvaluatedExpression> oldExprs = initialExprs.get(curLine);
   			if (oldExprs != null) {
   				for (TypedExpression e: oldExprs)  // Use the old expressions since we might have metadata about them (e.g., Method, Field).
   					typedExprs.add(new TypedExpression(e.getExpression(), e.getType()));
   			} else {
	   			// Parse the expression.
	   			ASTNode node = EclipseUtils.parseExpr(parser, matcher.group(3));
	   			// Get the possible expressions in a generic list.
	   			Iterator<?> it = ((MethodInvocation)node).arguments().iterator();
	   			while (it.hasNext())
	   				typedExprs.add(new TypedExpression((Expression)it.next(), varStaticType));
   			}
        	assert typedExprs.size() > 0;  // We must have at least one expression.

   			Property initialProperty = initialDemonstrations.containsKey(curLine) ? initialDemonstrations.get(curLine) : null;
   			          
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
   			final SynthesisDialog synthesisDialog = getRefinementDialog(curLine, typedExprs, varname, varStaticType, varStaticTypeName, frame, typeCache, initialProperty);
   			Display.getDefault().syncExec(new Runnable() {
   				@Override
   				public void run() {
   					synthesisDialog.open();
   				}
   			});
   			ArrayList<FullyEvaluatedExpression> validExprs = synthesisDialog.getExpressions();
   			if (validExprs == null) {
   				//The user cancelled, just drop back into the debugger and let the 
   				//use do what they want.  Attempting to execute the line will result
   				//in a crash anyway, so they can't screw anything up
   				EclipseUtils.log("Ending refinement for " + curLine + " because the user told us to cancel.");
   				DataCollector.log("refine-cancel");
   				return;
   			}

   			if (validExprs.isEmpty()) {
   				EclipseUtils.showError("Error", "No legal expressions remain after refinement.", null);
   				throw new RuntimeException("No legal expressions remain after refinement");
   			}
   			IJavaValue value = validExprs.get(0).getValue();  // The returned values could be different, so we arbitrarily use the first one.  This might not be the best thing to do.

   			Property newProperty = synthesisDialog.getProperty() == null ? initialProperty : synthesisDialog.getProperty();  // The user might not have entered a pdspec (e.g., they refined or just selected some things), in which case we use the old one.
   			String newLine = rewriteLine(matcher, varname, curLine, newProperty, validExprs, lineNumber);

   			if (validExprs.size() == 1) {
   				// If there's only a single possibility remaining, remove the breakpoint.
   				assert breakpoints.length > 0 : breakpoints.length;
   				// If there are multiple breakpoints at this line, only remove one (presumably the user added the others).
   				IBreakpoint curBreakpoint = breakpoints[0];
   				try {
   					DebugPlugin.getDefault().getBreakpointManager().removeBreakpoint(curBreakpoint, true);
   				} catch (CoreException e) {
   					e.printStackTrace();
   					throw new RuntimeException("Cannot delete breakpoint.");
   				}
   			} else
   				metadata.addMetadataFor(validExprs, synthesisDialog.getExpressionMaker());
   			
   			// TODO: Handle case when this is null (maybe do it above when we workaround it being null).  Creating a JDILocalVariable requires knowing about where the current scope ends, though.
   			if (lhsVar != null)
   				lhsVar.setValue(value);

   			
   			EclipseUtils.log("Ending refinement for " + curLine + ".  " + (newLine == null ? "Statement unchanged." : "Went from " + typedExprs.size() + " expressions to " + validExprs.size() + ".  New statement: " + newLine));
   			DataCollector.log("refine-finish", "pre=" + typedExprs.size(), "post=" + validExprs.size());
   			
   			// Immediately continue the execution.
   			thread.resume();
	    }
	    
		/**
		 * Checks to see if there is a pdspec at the current line, and synthesizes
		 * it if there is.
		 * Note that I have to use Display.syncExec instead of asyncExec or else
		 * the newly-opened dialog box will not have focus for some reason....
		 * @param fullCurLine The current line of code.
		 * @param stack The current stack frame.
		 */
	    private static void checkLineForSpecs(String fullCurLine, final IJavaStackFrame stack) {
	    	final Matcher statePropertyMatcher = DemonstrateStatePropertyHandler.PATTERN.matcher(fullCurLine);
       		if(statePropertyMatcher.matches()) {
            	Display.getDefault().syncExec(new Runnable(){
    				@Override
    				public void run() {
                    	DemonstrateStatePropertyHandler.handleFromText(statePropertyMatcher, stack);
    				}
            	});
            	return;
       		}

	    	final Matcher valuePropertyMatcher = DemonstrateValueHandler.PATTERN.matcher(fullCurLine);
       		if(valuePropertyMatcher.matches()) {
            	Display.getDefault().syncExec(new Runnable(){
    				@Override
    				public void run() {
                    	DemonstrateValueHandler.handleFromText(valuePropertyMatcher, stack);
    				}
            	});
            	return;
       		}

	    	final Matcher typePropertyMatcher = DemonstrateTypeHandler.PATTERN.matcher(fullCurLine);
       		if(typePropertyMatcher.matches()) {
            	Display.getDefault().syncExec(new Runnable(){
    				@Override
    				public void run() {
                    	DemonstrateTypeHandler.handleFromText(typePropertyMatcher, stack);
    				}
            	});
            	return;
       		}
		}

	    /**
	     * Rewrites the current choose statement to show the
	     * results of the refinement.
	     * @param matcher A Matcher that has parsed the current line.
	     * @param varname The name of the variable being assigned
	     * the expressions.
	     * @param curLine The old line of code.
	     * @param property The pdspec given by the user.
	     * @param validExprs The new expressions.
	     * @param lineNumber
	     * @return
	     */
		private static String rewriteLine(Matcher matcher, String varname, String curLine, Property property, ArrayList<FullyEvaluatedExpression> validExprs, final int lineNumber) {
			List<String> newExprsStrs = FullyEvaluatedExpression.snippetsOfEvaluatedExpressions(validExprs);
			String varDeclaration = matcher.group(1) != null ? matcher.group(1) + " " : "";
			final String newLine = varDeclaration + generateChooseOrChosenStmt(varname, newExprsStrs);
			// If we don't execute this on the UI thread we get an exception, although it still works.
			Display.getDefault().syncExec(new Runnable() {
				@Override
				public void run() {
					try {
						EclipseUtils.replaceLine(newLine, lineNumber);
					} catch (BadLocationException e) {
						throw new RuntimeException(e);
					}
				}
			});
   			initialDemonstrations.remove(curLine);
			initialDemonstrations.put(newLine, property);
			initialExprs.remove(curLine);
			initialExprs.put(newLine, validExprs);
			if (blockedNatives.remove(curLine))
				blockedNatives.add(newLine);
			if (handledEffects.remove(curLine))
				handledEffects.add(newLine);
			return newLine;
		}
	    
	    /**
	     * Gets the current Document on the UI thread.
	     * @return The current document.
	     */
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
	    
	    /**
	     * Gets the current editor on the UI thread.
	     * @return The current editor.
	     */
	    private static ITextEditor getEditor() {
	    	final ITextEditor[] result = new ITextEditor[] { null };
        	Display.getDefault().syncExec(new Runnable(){
				@Override
				public void run() {
                	result[0] = EclipseUtils.getActiveTextEditor();
				}
        	});
        	return result[0];
	    }

	    /**
	     * Gets the current Shell on the UI thread.
	     * @return The current shell.
	     */
	    private static Shell getShell() {
	    	final Shell[] result = new Shell[] { null };
        	Display.getDefault().syncExec(new Runnable(){
				@Override
				public void run() {
                	result[0] = EclipseUtils.getShell();
				}
        	});
        	return result[0];
	    }
	    
	    /**
	     * Gets the proper dialog to use for the refinement.  This
	     * dialog will default to asking for the type of pdspec the
	     * user gave the last time at this line.
	     * @param curLine The current line.
	     * @param exprs The initial set of expressions.
	     * @param varName The name of the variable being assigned
	     * the expressions.
	     * @param varStaticType The static type of the variable.
	     * @param varStaticTypeName The name of the type of the variable.
	     * @param stackFrame The stack frame.
	     * @param typeCache The type cache.
	     * @param oldProperty The property the user gave the last time
	     * at this line.
	     * @return A dialog that defaults to asking for the type of
	     * pdspec the user gave the last time at this line.
	     * @throws DebugException
	     */
	    private static RefinementSynthesisDialog getRefinementDialog(String curLine, ArrayList<TypedExpression> exprs, String varName, IJavaType varStaticType, String varStaticTypeName, IJavaStackFrame stackFrame, TypeCache typeCache, Property oldProperty) throws DebugException {
			Shell shell = getShell();
			PropertyDialog propertyDialog = null;
			if (oldProperty == null || oldProperty instanceof StateProperty)
				propertyDialog = new StatePropertyDialog(varName, stackFrame, oldProperty == null ? "" : oldProperty.toString(), null);
			else if (oldProperty instanceof PrimitiveValueProperty)
				propertyDialog = new PrimitiveValuePropertyDialog(varName, varStaticTypeName, stackFrame, "", null);
			else if (oldProperty instanceof ObjectValueProperty)
				propertyDialog = new ObjectValuePropertyDialog(varName, varStaticTypeName, stackFrame, "", null);
			else if (oldProperty instanceof TypeProperty)
				propertyDialog = new TypePropertyDialog(varName, varStaticTypeName, stackFrame, ((TypeProperty)oldProperty).getTypeName(), null);
			/*else if (oldProperty instanceof LambdaProperty)
				propertyDialog = new LambdaPropertyDialog(varName, varStaticType.getName(), varStaticType, stackFrame, oldProperty.toString(), extraMessage);*/
			else
				throw new IllegalArgumentException(oldProperty.toString());
			return new RefinementSynthesisDialog(shell, varStaticTypeName, varStaticType, stackFrame, propertyDialog, new SynthesisWorker(varName, varStaticType), new RefinementWorker(exprs, varStaticType, typeCache), blockedNatives.contains(curLine), handledEffects.contains(curLine));
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
	    
	    /**
	     * Gets a nice string representing the legal values
	     * with duplicates removed.
	     * @param exprs The legal strings.
	     * @return A nice string representation of the legal values.
	     */
	    private static String getLegalValues(List<FullyEvaluatedExpression> exprs) {
	    	// Remove duplicates.
	    	Set<String> values = new HashSet<String>();
	    	for (FullyEvaluatedExpression e: exprs)
	    		values.add(e.getResultString());
	    	// Build a string.
	    	StringBuilder sb = new StringBuilder();
	    	for (String value: values) {
	    		if (sb.length() > 0)
	    			sb.append(", ");
	    		sb.append(Utils.truncate(value, 50));
	    	}
	    	return sb.toString();
	    }
	    
	}
	
	/**
	 * Generates the text of a choose or chosen call
	 * that should be inserted into the document.
	 * @param varname The name of the variable being assigned.
	 * @param expressions The candidate expressions.
	 * @return The text of the choose or chosen call
	 * to insert.
	 */
    private static String generateChooseOrChosenStmt(String varname, List<String> expressions) {
    	String functionName = "CodeHint." + (expressions.size() == 1 ? "chosen" : "choose");
		String statementRHSExpr = functionName + "(";
		String newExprsString = expressions.toString();
		newExprsString = newExprsString.substring(1, newExprsString.length() - 1);
		statementRHSExpr += newExprsString;
		statementRHSExpr += ")";
		if (expressions.size() == 1)
			addedChosenStmts.add(statementRHSExpr);
		return (varname == null ? "" : varname + " = ") + statementRHSExpr + ";";
    }

	private static IResource getResource(ITextEditor editor) {
		IResource resource = (IResource)editor.getEditorInput().getAdapter(IResource.class);
		return resource;
	}

	private static String getResourceName(ITextEditor editor) {
		String resourceName = getResource(editor).getLocation().toString();
		return resourceName;
	}

    private static ChoiceBreakpointListener listener;
	
    /**
     * Registers our breakpoint listener and initializes some
     * static fields.
     * This is called externally from plugin startup.
     */
    public static void start() {
    	if (listener == null)
    		listener = new ChoiceBreakpointListener();
    	initialDemonstrations = new HashMap<String, Property>();
    	addedChosenStmts = new ArrayList<String>();
    	initialFiles = new HashMap<String, String>();
    	metadata = ExpressionMaker.Metadata.emptyMetadata();
    	initialExprs = new HashMap<String, List<FullyEvaluatedExpression>>();
    	handledEffects = new HashSet<String>();
    	blockedNatives = new HashSet<String>();
    	Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
            	ITextEditor editor = EclipseUtils.getActiveTextEditor();
            	if (editor != null && !editor.isDirty())  // Initialize the original file if it is not dirty.
            		initialFiles.put(getResourceName(editor), EclipseUtils.getDocument(editor).get());
			}
    	});
    	DebugPlugin.getDefault().addDebugEventListener(listener);
    	ExpressionGenerator.init();
    }

    /**
     * Unregisters our breakpoint listener and clears some
     * static fields.
     * This is called externally from plugin shutdown.
     */
    public static void stop() {
    	DebugPlugin.getDefault().removeDebugEventListener(listener);
    	listener = null;
    	initialDemonstrations = null;
    	addedChosenStmts = null;
    	initialFiles = null;
    	metadata = null;
    	initialExprs = null;
    	handledEffects = null;
    	blockedNatives = null;
    	ExpressionGenerator.clear();
    }
    
    private static List<IBreakpoint> addedBreakpoints = new ArrayList<IBreakpoint>();
    
    public static void addBreakpoint(IResource resource, String typename, int line) {
    	try {
			IBreakpoint bp = JDIDebugModel.createLineBreakpoint(resource, typename, line, -1, -1, 0, true, null);
			addedBreakpoints.add(bp);
		} catch (CoreException e) {
			throw new RuntimeException(e);
		}
    }
    
    public static ExpressionMaker.Metadata getMetadata() {
    	return metadata;
    }

}
