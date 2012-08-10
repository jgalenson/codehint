package codehint.exprgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.debug.core.IJavaArrayType;
import org.eclipse.jdt.debug.core.IJavaClassObject;
import org.eclipse.jdt.debug.core.IJavaClassType;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaReferenceType;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;

import com.sun.jdi.Field;
import com.sun.jdi.Method;

import codehint.dialogs.InitialSynthesisDialog;
import codehint.expreval.EvaluatedExpression;
import codehint.expreval.EvaluationManager;
import codehint.expreval.FullyEvaluatedExpression;
import codehint.exprgen.typeconstraint.DesiredType;
import codehint.exprgen.typeconstraint.FieldConstraint;
import codehint.exprgen.typeconstraint.FieldNameConstraint;
import codehint.exprgen.typeconstraint.MethodConstraint;
import codehint.exprgen.typeconstraint.MethodNameConstraint;
import codehint.exprgen.typeconstraint.SameHierarchy;
import codehint.exprgen.typeconstraint.SingleTypeConstraint;
import codehint.exprgen.typeconstraint.SubtypeBound;
import codehint.exprgen.typeconstraint.SubtypeSet;
import codehint.exprgen.typeconstraint.SupertypeBound;
import codehint.exprgen.typeconstraint.SupertypeSet;
import codehint.exprgen.typeconstraint.TypeConstraint;
import codehint.exprgen.typeconstraint.UnknownConstraint;
import codehint.property.LambdaProperty;
import codehint.property.Property;
import codehint.property.TypeProperty;
import codehint.property.ValueProperty;
import codehint.utils.EclipseUtils;
import codehint.utils.Utils;

public final class ExpressionSkeleton {
	
    private final static ASTParser parser = ASTParser.newParser(AST.JLS4);
    
    public final static String HOLE_SYNTAX = "??";
    private final static String LIST_HOLE_SYNTAX = "**";
    
    private final String sugaredString;
    private final Expression expression;
    private final Map<String, HoleInfo> holeInfos;
    private final IJavaDebugTarget target;
    private final IJavaStackFrame stack;
    private final SubtypeChecker subtypeChecker;
    private final TypeCache typeCache;
    private final EvaluationManager evalManager;
    private final ExpressionGenerator expressionGenerator;
	
    /**
     * Creates a new ExpressionSkeleton.
     * @param sugaredString The sugared form of the skeleton string.
     * @param node The desugared expression representing the skeleton.
     * @param holeInfos The information about the holes in the skeleton.
     * @param target The debug target.
     * @param stack The stack frame.
     * @param subtypeChecker The subtype checker.
     * @param typeCache The type cache.
     * @param evalManager The evaluation manager.
     * @param expressionGenerator The expression generator.
     */
	private ExpressionSkeleton(String sugaredString, Expression node, Map<String, HoleInfo> holeInfos, IJavaDebugTarget target, IJavaStackFrame stack, SubtypeChecker subtypeChecker, TypeCache typeCache, EvaluationManager evalManager, ExpressionGenerator expressionGenerator) {
		this.sugaredString = sugaredString;
		this.expression = node;
		this.holeInfos = holeInfos;
		this.target = target;
		this.stack = stack;
		this.subtypeChecker = subtypeChecker;
		this.typeCache = typeCache;
		this.evalManager = evalManager;
		this.expressionGenerator = expressionGenerator;
	}

	/**
	 * Creates an ExpressionSkeleton from the given sugared string.
	 * @param skeletonStr The sugared string from which to create the skeleton.
	 * @param target The debug target.
	 * @param stack The stack frame.
	 * @param evaluationEngine The AST evaluation engine.
     * @param subtypeChecker The subtype checker.
     * @param typeCache The type cache.
     * @param evalManager The evaluation manager.
     * @param expressionGenerator The expression generator.
	 * @return The ExpressionSkeleton representing the given sugared string.
	 */
	public static ExpressionSkeleton fromString(String skeletonStr, IJavaDebugTarget target, IJavaStackFrame stack, IAstEvaluationEngine evaluationEngine, SubtypeChecker subtypeChecker, TypeCache typeCache, EvaluationManager evalManager, ExpressionGenerator expressionGenerator) {
		return SkeletonParser.rewriteHoleSyntax(skeletonStr, target, stack, evaluationEngine, subtypeChecker, typeCache, evalManager, expressionGenerator);
	}
	
	/**
	 * Checks whether the given string represents a legal sugared skeleton.
	 * @param str The string to check.
	 * @param varTypeName The name of the type of the variable being assigned.
	 * @param stackFrame The stack frame.
	 * @param evaluationEngine The AST evaluation engine.
	 * @return Whether the given string represents a legal sugared skeleton.
	 */
	public static String isLegalSkeleton(String str, String varTypeName, IJavaStackFrame stackFrame, IAstEvaluationEngine evaluationEngine) {
		return SkeletonParser.isLegalSkeleton(str, varTypeName, stackFrame, evaluationEngine);
	}
	
	/**
	 * Gets the sugared string representing this skeleton.
	 * @return The sugared string form of this skeleton.
	 */
	public String getSugaredString() {
		return sugaredString;
	}
	
	@Override
	public String toString() {
		return sugaredString;
	}
	
	/**
	 * A helper class for parsing sugared skeleton strings.
	 */
	private static class SkeletonParser {

	    private final static String DESUGARED_HOLE_NAME = "_$hole";
	
		private static class ParserError extends RuntimeException {
			
			private static final long serialVersionUID = 1L;
	
			public ParserError(String msg) {
				super(msg);
			}
			
		}

		/**
		 * Checks whether the given string represents a legal sugared skeleton.
		 * @param str The string to check.
		 * @param varTypeName The name of the type of the variable being assigned.
		 * @param stackFrame The stack frame.
		 * @param evaluationEngine The AST evaluation engine.
		 * @return Whether the given string represents a legal sugared skeleton.
		 */
		private static String isLegalSkeleton(String str, String varTypeName, IJavaStackFrame stackFrame, IAstEvaluationEngine evaluationEngine) {
			try {
				Expression skeletonExpr = rewriteHoleSyntax(str, new HashMap<String, HoleInfo>(), stackFrame, evaluationEngine);
				Set<String> compileErrors = EclipseUtils.getSetOfCompileErrors(skeletonExpr.toString(), varTypeName, stackFrame, evaluationEngine);
				if (compileErrors != null)
					for (String error: compileErrors)
						if (!error.contains(DESUGARED_HOLE_NAME))
							return error;
				return null;
			} catch (ParserError e) {
				return e.getMessage();
			}
		}

		/**
		 * Creates an ExpressionSkeleton from the given sugared string or throws
		 * a ParserError if the string is not valid.
		 * @param sugaredString The sugared string from which to create the skeleton.
		 * @param holeInfos Map in which to store information about the holes in the skeleton.
		 * @return The ExpressionSkeleton representing the given sugared string.
		 */
		private static Expression rewriteHoleSyntax(String sugaredString, Map<String, HoleInfo> holeInfos, IJavaStackFrame stack, IAstEvaluationEngine evaluationEngine) {
			String str = sugaredString;
			for (int holeNum = 0; true; holeNum++) {
				int holeStart = str.indexOf(HOLE_SYNTAX);
				if (holeStart != -1)
					str = rewriteSingleHole(str, holeStart, holeNum, holeInfos, stack, evaluationEngine);
				else {
					holeStart = str.indexOf(LIST_HOLE_SYNTAX);
					if (holeStart != -1)
						str = rewriteSingleListHole(str, holeStart, holeNum, holeInfos);
					else
						break;
				}
			}
			if (holeInfos.isEmpty())
				throw new ParserError("The skeleton must have at least one " + HOLE_SYNTAX + " or " + LIST_HOLE_SYNTAX + " hole.");
			ASTNode node = EclipseUtils.parseExpr(parser, str);
			if (node instanceof CompilationUnit)
				throw new ParserError("Enter a valid skeleton: " + ((CompilationUnit)node).getProblems()[0].getMessage());
			return (Expression)ExpressionMaker.resetAST(node);
		}

		/**
		 * Creates an ExpressionSkeleton from the given sugared string or throws
		 * a ParserError if the string is not valid.
		 * @param sugaredString The sugared string from which to create the skeleton.
		 * @param target The debug target.
		 * @param stack The stack frame.
		 * @param evaluationEngine The AST evaluation engine.
	     * @param subtypeChecker The subtype checker.
	     * @param typeCache The type cache.
	     * @param evalManager The evaluation manager.
	     * @param expressionGenerator The expression generator.
		 * @return The ExpressionSkeleton representing the given sugared string.
		 */
		private static ExpressionSkeleton rewriteHoleSyntax(String sugaredString, IJavaDebugTarget target, IJavaStackFrame stack, IAstEvaluationEngine evaluationEngine, SubtypeChecker subtypeChecker, TypeCache typeCache, EvaluationManager evalManager, ExpressionGenerator expressionGenerator) {
			Map<String, HoleInfo> holeInfos = new HashMap<String, HoleInfo>();
			Expression expr = rewriteHoleSyntax(sugaredString, holeInfos, stack, evaluationEngine);
			return new ExpressionSkeleton(sugaredString, (Expression)ExpressionMaker.resetAST(expr), holeInfos, target, stack, subtypeChecker, typeCache, evalManager, expressionGenerator);
		}
	
		/**
		 * Rewrites a single ?? hole in a skeleton string to its
		 * desugared form.
		 * @param str The string.
		 * @param holeIndex The starting index of the hole.
		 * @param holeNum A unique number identifying the hole.
		 * @param holeInfos Map that stores information about holes.
		 * @param stack The stack frame.
		 * @param evaluationEngine The AST evaluation engine.
		 * @return The original string with this hole desugared.
		 */
		private static String rewriteSingleHole(String str, int holeIndex, int holeNum, Map<String, HoleInfo> holeInfos, IJavaStackFrame stack, IAstEvaluationEngine evaluationEngine) {
			if (holeIndex >= 1 && Character.isJavaIdentifierPart(str.charAt(holeIndex - 1)))
				throw new ParserError("You cannot put a " + HOLE_SYNTAX + " hole immediately after an identifier.");
			String args = null;
			boolean isNegative = false;
			int i = holeIndex + HOLE_SYNTAX.length();
			if (i + 1 < str.length() && str.charAt(i) == '-' && str.charAt(i + 1) == '{') {
				isNegative = true;
				i++;
			}
			if (i < str.length() && str.charAt(i) == '{') {
				while (i < str.length() && str.charAt(i) != '}')
					i++;
				args = str.substring(holeIndex + HOLE_SYNTAX.length() + (isNegative ? 2 : 1), i);
				if (i < str.length())
					i++;
				else
					throw new ParserError("Enter a close brace } to finish the set of possibilities.");
			}
			if (i < str.length() && Character.isJavaIdentifierPart(str.charAt(i)))
				throw new ParserError("You cannot put a " + HOLE_SYNTAX + " hole immediately before an identifier.");
			String holeName = DESUGARED_HOLE_NAME + holeNum;
			str = str.substring(0, holeIndex) + holeName + str.substring(i);
			holeInfos.put(holeName, makeHoleInfo(args, isNegative, stack, evaluationEngine));
			return str;
		}

		/**
		 * Rewrites a single ** hole in a skeleton string to its
		 * desugared form.
		 * @param str The string.
		 * @param holeIndex The starting index of the hole.
		 * @param holeNum A unique number identifying the hole.
		 * @param holeInfos Map that stores information about holes.
		 * @return The original string with this hole desugared.
		 */
		private static String rewriteSingleListHole(String str, int holeIndex, int holeNum, Map<String, HoleInfo> holeInfos) {
			if (holeIndex < 2 || holeIndex + LIST_HOLE_SYNTAX.length() >= str.length() || str.charAt(holeIndex - 1) != '(' || !Character.isJavaIdentifierPart(str.charAt(holeIndex - 2)) || str.charAt(holeIndex + LIST_HOLE_SYNTAX.length()) != ')')
				throw new ParserError("You can only use a " + LIST_HOLE_SYNTAX + " hole as the only argument to a method.");
			String holeName = DESUGARED_HOLE_NAME + holeNum;
			str = str.substring(0, holeIndex) + holeName + str.substring(holeIndex + LIST_HOLE_SYNTAX.length());
			holeInfos.put(holeName, new ListHoleInfo(null, false));
			return str;
		}
		
		/**
		 * Makes a class that stores information about holes,
		 * such as the list of possible expressions.
		 * @param argsStr The string representing the declared
		 * arguments, e.g., "{x, y, z}".
		 * @param isNegative Whether the hole is negative, e.g.,
		 * for "??-{x,y,z}".
		 * @param stack The stack frame.
		 * @param evaluationEngine The AST evaluation engine.
		 * @return The HoleInfo representing information
		 * about this hole.
		 */
		private static HoleInfo makeHoleInfo(String argsStr, boolean isNegative, IJavaStackFrame stack, IAstEvaluationEngine evaluationEngine) {
			ArrayList<String> args = null;
			if (argsStr != null) {
				// Ensure the argument string parses
				ASTNode node = EclipseUtils.parseExpr(parser, DESUGARED_HOLE_NAME + "(" + argsStr + ")");
				if (node instanceof MethodInvocation) {
					// Ensure the individual arguments type check.
					// We do this by pretending to use the arguments list to initialize an array of Objects.
					// Since anything can be coerced into an Object, this works.
					// This allows us to make only one call to the compiler instead of one per arg.
					String error = EclipseUtils.getCompileErrors("new Object[] {" + argsStr + "}", null, stack, evaluationEngine);
					if (error != null)
						throw new ParserError(error);
					// Add the arguments.
					List<?> untypedArgs = ((MethodInvocation)node).arguments();
					args = new ArrayList<String>(untypedArgs.size());
					for (int i = 0; i < untypedArgs.size(); i++)
						args.add(((Expression)untypedArgs.get(i)).toString());
				} else
					throw new ParserError(((CompilationUnit)node).getProblems()[0].getMessage());
			}
			return new HoleInfo(args, isNegative);
		}
	
	}
	
	/**
	 * A class that stores information about ?? holes
	 * such as their list of possible expressions, if any.
	 */
	private static class HoleInfo {
		
		private final ArrayList<String> args;
		private final boolean isNegative;
		
		public HoleInfo(ArrayList<String> args, boolean isNegative) {
			this.args = args;
			this.isNegative = isNegative;
		}
		
		public ArrayList<String> getArgs() {
			return args;
		}
		
		public boolean isNegative() {
			return isNegative;
		}
		
		@Override
		public String toString() {
			return HOLE_SYNTAX + (isNegative ? "-" : "") + (args == null ? "" : args.toString());
		}
		
	}

	/**
	 * A class that stores information about ** holes.
	 */
	private static class ListHoleInfo extends HoleInfo {

		public ListHoleInfo(ArrayList<String> args, boolean isNegative) {
			super(args, isNegative);
		}
		
	}
	
	/**
	 * Synthesizes expressions that satisfy this skeleton and the user's pdspec.
	 * @param property The pdspec entered by the user.
	 * @param varStaticType The static type of the variable being assigned.
	 * @param synthesisDialog The synthesis dialog to pass valid expressions.
	 * @param monitor The progress monitor.
	 * @return Expressions that satisfy this skeletonn and the pdspec.
	 */
	public ArrayList<FullyEvaluatedExpression> synthesize(Property property, IJavaType varStaticType, InitialSynthesisDialog synthesisDialog, IProgressMonitor monitor) {
		try {
			long startTime = System.currentTimeMillis();
			// TODO: Improve progress monitor so it shows you which evaluation it is.
			TypeConstraint typeConstraint = getInitialTypeConstraint(varStaticType, property);
			ArrayList<FullyEvaluatedExpression> results;
			if (HOLE_SYNTAX.equals(sugaredString))  // Optimization: Optimize special case of "??" skeleton by simply calling old ExprGen code directly.
				results = expressionGenerator.generateExpression(property, typeConstraint, synthesisDialog, monitor, 1);
			else {
				monitor.beginTask("Skeleton generation", holeInfos.size() + 2);
				ArrayList<TypedExpression> exprs = SkeletonFiller.fillSkeleton(expression, typeConstraint, holeInfos, stack, target, evalManager, expressionGenerator, subtypeChecker, typeCache, monitor);
				SubMonitor evalMonitor = SubMonitor.convert(monitor, "Expression evaluation", exprs.size());
				results = evalManager.evaluateExpressions(exprs, property, synthesisDialog, evalMonitor);
				EclipseUtils.log("Synthesis found " + exprs.size() + " expressions of which " + results.size() + " were valid and took " + (System.currentTimeMillis() - startTime) + " milliseconds.");
		    	monitor.done();
			}
			return results;
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Gets the initial type constraint for the whole skeleton based on the
	 * static type of the varible being assigned and the pdspec.
	 * @param varStaticType The static type of the variable being assigned.
	 * @param property The pdspec.
	 * @return The initial type constraint for the whole skeleton.
	 * @throws DebugException
	 */
	private TypeConstraint getInitialTypeConstraint(IJavaType varStaticType, Property property) throws DebugException {
		if (property instanceof ValueProperty)  // We want exactly the type of the value.
			return new DesiredType(((ValueProperty)property).getValue().getJavaType());
		else if (property instanceof TypeProperty)  // We want the type specified by the type property.
			return new SupertypeBound(((TypeProperty)property).getType());
		else if (property instanceof LambdaProperty) {
			String typeName = ((LambdaProperty)property).getTypeName();
			if (typeName != null)  // We want the type specified in the lambda expression.
				return new SupertypeBound(EclipseUtils.getType(typeName, stack, target, typeCache));
		}
		return new SupertypeBound(varStaticType);  // Just use the variable's static type.
	}
	
	/**
	 * A class that computes the parents of holes.
	 */
	private static class HoleParentSetter extends ASTVisitor {

		private final Map<String, HoleInfo> holeInfos;
		private final Set<ASTNode> parentsOfHoles;
		
		private HoleParentSetter(Map<String, HoleInfo> holeInfos) {
			this.holeInfos = holeInfos;
			parentsOfHoles = new HashSet<ASTNode>();
		}
		
		/**
		 * Finds all the nodes that are parents of a hole.
		 * @param holeInfos The information about the holes.
		 * @param skeleton The skeleton expression.
		 * @return All the nodes that are parents of a hole.
		 */
		public static Set<ASTNode> getParentsOfHoles(Map<String, HoleInfo> holeInfos, Expression skeleton) {
			HoleParentSetter holeParentSetter = new HoleParentSetter(holeInfos);
			skeleton.accept(holeParentSetter);
			return holeParentSetter.parentsOfHoles;
		}

    	@Override
    	public boolean visit(SimpleName node) {
    		if (holeInfos.containsKey(node.getIdentifier()))
    			for (ASTNode cur = node; cur != null && cur instanceof Expression; cur = cur.getParent())
    				parentsOfHoles.add(cur);
    		return true;
    	}
		
	}
	
	/**
	 * A class that represents a type error in the skeleton.
	 */
	public static class TypeError extends RuntimeException {
		
		private static final long serialVersionUID = 1L;

		public TypeError(String msg) {
			super(msg);
		}
		
	}
	
	/**
	 * The class responsible for filling the skeleton with
	 * candidate expressions, i.e., doing the actual synthesis.
	 */
	private static class SkeletonFiller {

		private final Map<String, Map<String, ArrayList<Field>>> holeFields;
		private final Map<String, Map<String, ArrayList<Method>>> holeMethods;
		private final Map<String, HoleInfo> holeInfos;
		private final EvaluationManager evalManager;
		private final ExpressionGenerator expressionGenerator;
		private final SubtypeChecker subtypeChecker;
		private final TypeCache typeCache;
		private final IJavaStackFrame stack;
		private final IJavaDebugTarget target;
		private final IProgressMonitor monitor;
		
		private final IJavaType intType;
		private final IJavaType booleanType;
		
		/**
		 * Creates a new SkeletonFiller.  Many common values are
		 * stored as ivars for convenience.
		 * @param holeInfos The information about the holes in the skeleton.
		 * @param stack The current stack frame.
		 * @param target The debug target.
		 * @param evalManager The evaluation manager.
		 * @param expressionGenerator The expression generator.
		 * @param subtypeChecker The subtype checker.
		 * @param typeCache The type cache.
		 * @param monitor The progress monitor.
		 */
		private SkeletonFiller(Map<String, HoleInfo> holeInfos, IJavaStackFrame stack, IJavaDebugTarget target, EvaluationManager evalManager, ExpressionGenerator expressionGenerator, SubtypeChecker subtypeChecker, TypeCache typeCache, IProgressMonitor monitor) {
			this.holeFields = new HashMap<String, Map<String, ArrayList<Field>>>();
			this.holeMethods = new HashMap<String, Map<String, ArrayList<Method>>>();
			this.holeInfos = holeInfos;
			this.evalManager = evalManager;
			this.expressionGenerator = expressionGenerator;
			this.subtypeChecker = subtypeChecker;
			this.typeCache = typeCache;
			this.stack = stack;
			this.target = target;
			this.monitor = monitor;
			intType = EclipseUtils.getFullyQualifiedType("int", stack, target, typeCache);
			booleanType = EclipseUtils.getFullyQualifiedType("boolean", stack, target, typeCache);
		}
		
		/**
		 * Fill the skeleton by synthesizing expressions that meet it.
		 * That is, it finds expressions that can complete the holes
		 * in type-correct ways and returns the entire expressions.
		 * @param skeleton The actual skeleton.
		 * @param initialTypeConstraint The type constraint for the
		 * entire skeleton.  This constrains what type it must return.
		 * @param holeInfos The information about the holes in the skeleton.
		 * @param stack The current stack frame.
		 * @param target The debug target.
		 * @param evalManager The evaluation manager.
		 * @param expressionGenerator The expression generator.
		 * @param subtypeChecker The subtype checker.
		 * @param typeCache The type cache.
		 * @param monitor The progress monitor.
		 * @return Expressions that meet the skeleton (with the
		 * holes filled in).
		 */
		public static ArrayList<TypedExpression> fillSkeleton(Expression skeleton, TypeConstraint initialTypeConstraint, Map<String, HoleInfo> holeInfos, IJavaStackFrame stack, IJavaDebugTarget target, EvaluationManager evalManager, ExpressionGenerator expressionGenerator, SubtypeChecker subtypeChecker, TypeCache typeCache, IProgressMonitor monitor) {
			SkeletonFiller filler = new SkeletonFiller(holeInfos, stack, target, evalManager, expressionGenerator, subtypeChecker, typeCache, monitor);
			ExpressionsAndTypeConstraints result = filler.fillSkeleton(skeleton, initialTypeConstraint, HoleParentSetter.getParentsOfHoles(holeInfos, skeleton));
			ArrayList<TypedExpression> exprs = new ArrayList<TypedExpression>();
			for (ArrayList<TypedExpression> curExprs: result.getExprs().values())
				exprs.addAll(curExprs);
			return exprs;
		}
		
		/**
		 * Recursively fills the given skeleton pieces.  We split on
		 * the type of the current AST node, handling each differently.
		 * 
		 * TODO: Instead of simply setting the node's constraint to the new thing,
		 * should we also keep what we have from the old one? 
		 * E.g., in get{Fields,Methods}AndSetConstraint, the old constraint might be
		 * "thing with a field" and the new might be "thing of type Foo".
		 * We want to combine those two.
		 * @param node The current piece of the skeleton.
		 * @param curConstraint The constraint for the type of the
		 * expressions generated from this piece of the skeleton.
		 * @param parentsOfHoles All the nodes that are parents of some hole.
		 * @return The synthesized expressions corresponding to this
		 * skeleton piece and the type constraint representing their types.
		 */
		private ExpressionsAndTypeConstraints fillSkeleton(Expression node, TypeConstraint curConstraint, Set<ASTNode> parentsOfHoles) {
			if (node instanceof ArrayAccess) {
				return fillArrayAccess((ArrayAccess)node, curConstraint, parentsOfHoles);
			} else if (node instanceof BooleanLiteral) {
	    		return new ExpressionsAndTypeConstraints(new EvaluatedExpression(node, booleanType, new Value(target.newValue(((BooleanLiteral)node).booleanValue()))), new SupertypeBound(booleanType));
			} else if (node instanceof CastExpression) {
				return fillCast((CastExpression)node, curConstraint, parentsOfHoles);
			} else if (node instanceof CharacterLiteral) {
				IJavaType type = EclipseUtils.getFullyQualifiedType("char", stack, target, typeCache);
	    		return new ExpressionsAndTypeConstraints(new EvaluatedExpression(node, type, new Value(target.newValue(((CharacterLiteral)node).charValue()))), new SupertypeBound(type));
			} else if (node instanceof ClassInstanceCreation) {
				return fillClassInstanceCreation(node, parentsOfHoles);
			} else if (node instanceof ConditionalExpression) {
				return fillConditionalExpression(node, curConstraint, parentsOfHoles);
			} else if (node instanceof FieldAccess) {
				FieldAccess fieldAccess = (FieldAccess)node;
				return fillNormalField(fieldAccess.getExpression(), fieldAccess.getName(), parentsOfHoles, curConstraint);
			} else if (node instanceof InfixExpression) {
				return fillInfixExpression(node, curConstraint, parentsOfHoles);
			} else if (node instanceof InstanceofExpression) {
				return fillInstanceof((InstanceofExpression)node, parentsOfHoles);
			} else if (node instanceof MethodInvocation) {
				MethodInvocation call = (MethodInvocation)node;
				ArrayList<TypeConstraint> argTypes = getArgTypes(call.arguments(), parentsOfHoles);
				ExpressionsAndTypeConstraints receiverResult = null;
				if (call.getExpression() != null) {
					TypeConstraint expressionConstraint = new MethodConstraint(parentsOfHoles.contains(call.getName()) ? null : call.getName().getIdentifier(), curConstraint, argTypes);
					receiverResult = fillSkeleton(call.getExpression(), expressionConstraint, parentsOfHoles);
				} else
					receiverResult = new ExpressionsAndTypeConstraints(new SupertypeBound(getThisType()));
				return fillMethod(call, call.getName(), call.arguments(), parentsOfHoles, curConstraint, argTypes, receiverResult);
			} else if (node instanceof NullLiteral) {
	    		return new ExpressionsAndTypeConstraints(new EvaluatedExpression(node, null, new Value(target.nullValue())), new SupertypeBound(EclipseUtils.getFullyQualifiedType("java.lang.Object", stack, target, typeCache)));
			} else if (node instanceof NumberLiteral) {
	    		return fillNumberLiteral(node);
			} else if (node instanceof ParenthesizedExpression) {
				return fillParenthesized((ParenthesizedExpression)node, curConstraint, parentsOfHoles);
			} else if (node instanceof PostfixExpression) {
				return fillPostfix((PostfixExpression)node, curConstraint, parentsOfHoles);
			} else if (node instanceof PrefixExpression) {
				return fillPrefix((PrefixExpression)node, curConstraint, parentsOfHoles);
			} else if (node instanceof QualifiedName) {
				IJavaType type = EclipseUtils.getTypeAndLoadIfNeededAndExists(node.toString(), stack, target, typeCache);
				if (type != null)
					return new ExpressionsAndTypeConstraints(new TypedExpression(node, type), new SupertypeBound(type));
				else {
					QualifiedName name = (QualifiedName)node;
					return fillNormalField(name.getQualifier(), name.getName(), parentsOfHoles, curConstraint);
				}
			} else if (node instanceof SimpleName) {
				return fillSimpleName(node, curConstraint);
			} else if (node instanceof StringLiteral) {
				IJavaType type = EclipseUtils.getFullyQualifiedType("java.lang.String", stack, target, typeCache);
				return new ExpressionsAndTypeConstraints(new EvaluatedExpression(node, type, new Value(target.newValue(((StringLiteral)node).getEscapedValue()))), new SupertypeBound(type));
			} else if (node instanceof SuperFieldAccess) {
				SuperFieldAccess superAccess = (SuperFieldAccess)node;
				IJavaType superType = getSuperType(superAccess.getQualifier());
				return fillField(superAccess.getName(), superAccess.getQualifier(), parentsOfHoles, curConstraint, new ExpressionsAndTypeConstraints(new TypedExpression(null, superType), new SupertypeBound(superType)));
			} else if (node instanceof SuperMethodInvocation) {
				SuperMethodInvocation superAccess = (SuperMethodInvocation)node;
				IJavaType superType = getSuperType(superAccess.getQualifier());
				ArrayList<TypeConstraint> argTypes = getArgTypes(superAccess.arguments(), parentsOfHoles);
				return fillMethod(superAccess, superAccess.getName(), superAccess.arguments(), parentsOfHoles, curConstraint, argTypes, new ExpressionsAndTypeConstraints(new TypedExpression(null, superType), new SupertypeBound(superType)));
			} else if (node instanceof ThisExpression) {
				IJavaType type = getThisType();
				return new ExpressionsAndTypeConstraints(new EvaluatedExpression(node, type, new Value(getThis())), new SupertypeBound(type)); 
			} else if (node instanceof TypeLiteral) {
				return fillTypeLiteral(node);
			} else
				throw new RuntimeException("Unexpected expression type " + node.getClass().toString());
		}

		/**
		 * Fills array access skeleton pieces.
		 * @param arrayAccess The array access part of the skeleton.
		 * @param curConstraint The constraint for the type of the
		 * expressions generated from this piece of the skeleton.
		 * @param parentsOfHoles All nodes that are parents of some hole.
		 * @return The synthesized expressions corresponding to this
		 * skeleton piece and the type constraint representing their types.
		 */
		private ExpressionsAndTypeConstraints fillArrayAccess(ArrayAccess arrayAccess, TypeConstraint curConstraint, Set<ASTNode> parentsOfHoles) {
			try {
				TypeConstraint arrayConstraint = null;
				if (parentsOfHoles.contains(arrayAccess.getArray())) {
					IJavaType[] componentConstraints = curConstraint.getTypes(stack, target, typeCache);
					List<IJavaType> componentTypes = new ArrayList<IJavaType>(componentConstraints.length);
					for (IJavaType type: componentConstraints)
						componentTypes.add(EclipseUtils.getFullyQualifiedType(type.getName() + "[]", stack, target, typeCache));
					arrayConstraint = getSupertypeConstraintForTypes(componentTypes);
				}
				ExpressionsAndTypeConstraints arrayResult = fillSkeleton(arrayAccess.getArray(), arrayConstraint, parentsOfHoles);
				arrayConstraint = arrayResult.getTypeConstraint();
				ExpressionsAndTypeConstraints indexResult = fillSkeleton(arrayAccess.getIndex(), new SupertypeBound(intType), parentsOfHoles);
				IJavaType[] arrayConstraints = arrayConstraint.getTypes(stack, target, typeCache);
				List<IJavaType> resultTypes = new ArrayList<IJavaType>(arrayConstraints.length);
				for (IJavaType type: arrayConstraints)
					resultTypes.add(((IJavaArrayType)type).getComponentType());
				Map<String, ArrayList<TypedExpression>> resultExprs = new HashMap<String, ArrayList<TypedExpression>>(arrayResult.getExprs().size());
				for (Map.Entry<String, ArrayList<TypedExpression>> a: arrayResult.getExprs().entrySet())
					for (TypedExpression arrExpr: a.getValue())
						if (arrExpr.getType() != null)  // TODO: This should really be part of my constraint when I search for this in the first place above.
							for (TypedExpression indexExpr: Utils.singleton(indexResult.getExprs().values()))
								Utils.addToMap(resultExprs, a.getKey(), ExpressionMaker.makeArrayAccess(arrExpr, indexExpr, null));
				return new ExpressionsAndTypeConstraints(resultExprs, getSupertypeConstraintForTypes(resultTypes));
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
		}

		/**
		 * Fills cast skeleton pieces.
		 * @param cast The cast part of the skeleton.
		 * @param curConstraint The constraint for the type of the
		 * expressions generated from this piece of the skeleton.
		 * @param parentsOfHoles All nodes that are parents of some hole.
		 * @return The synthesized expressions corresponding to this
		 * skeleton piece and the type constraint representing their types.
		 */
		private ExpressionsAndTypeConstraints fillCast(CastExpression cast, TypeConstraint curConstraint, Set<ASTNode> parentsOfHoles) {
			// TODO: Allow any type or ensure comparable with the given type with SameHierarchy?
			ExpressionsAndTypeConstraints exprResult = fillSkeleton(cast.getExpression(), curConstraint, parentsOfHoles);
			IJavaType castType = EclipseUtils.getType(cast.getType().toString(), stack, target, typeCache);
			Map<String, ArrayList<TypedExpression>> resultExprs = new HashMap<String, ArrayList<TypedExpression>>(exprResult.getExprs().size());
			for (Map.Entry<String, ArrayList<TypedExpression>> res: exprResult.getExprs().entrySet())
				for (TypedExpression expr: res.getValue())
					Utils.addToMap(resultExprs, res.getKey(), ExpressionMaker.makeCast(expr, castType, null));
			return new ExpressionsAndTypeConstraints(resultExprs, new SupertypeBound(castType));
		}

		/**
		 * Fills class instance creation skeleton pieces.
		 * @param node The class instance creation part of the skeleton.
		 * @param parentsOfHoles All nodes that are parents of some hole.
		 * @return The synthesized expressions corresponding to this
		 * skeleton piece and the type constraint representing their types.
		 */
		private ExpressionsAndTypeConstraints fillClassInstanceCreation(Expression node, Set<ASTNode> parentsOfHoles) {
			ClassInstanceCreation cons = (ClassInstanceCreation)node;
			assert cons.getExpression() == null;  // TODO: Handle this case.
			IJavaType consType = EclipseUtils.getType(cons.getType().toString(), stack, target, typeCache);
			ArrayList<TypeConstraint> argTypes = getArgTypes(cons.arguments(), parentsOfHoles);
			Map<String, ArrayList<Method>> constructors = getConstructors(consType, argTypes);
			boolean isListHole = argTypes == null;
			ArrayList<ExpressionsAndTypeConstraints> argResults = getAndFillArgs(cons.arguments(), parentsOfHoles, constructors, isListHole);
			OverloadChecker overloadChecker = new OverloadChecker(consType);
			Map<String, ArrayList<TypedExpression>> resultExprs = new HashMap<String, ArrayList<TypedExpression>>();
			for (Method method: Utils.singleton(constructors.values()))
				buildCalls(method, new TypedExpression(null, consType), cons, argResults, isListHole, resultExprs, overloadChecker);
			return new ExpressionsAndTypeConstraints(resultExprs, new DesiredType(consType));
		}

		/**
		 * Fills conditional/ternary skeleton pieces.
		 * @param node The conditional/ternary part of the skeleton.
		 * @param curConstraint The constraint for the type of the
		 * expressions generated from this piece of the skeleton.
		 * @param parentsOfHoles All nodes that are parents of some hole.
		 * @return The synthesized expressions corresponding to this
		 * skeleton piece and the type constraint representing their types.
		 */
		private ExpressionsAndTypeConstraints fillConditionalExpression(Expression node, TypeConstraint curConstraint, Set<ASTNode> parentsOfHoles) {
			ConditionalExpression cond = (ConditionalExpression)node;
			// TODO: Do a better job of constraining left/right to be similar?  E.g., if we have right but not left.
			ExpressionsAndTypeConstraints condResult = fillSkeleton(cond.getExpression(), new SupertypeBound(booleanType), parentsOfHoles);
			ExpressionsAndTypeConstraints thenResult = fillSkeleton(cond.getThenExpression(), curConstraint, parentsOfHoles);
			ExpressionsAndTypeConstraints elseResult = fillSkeleton(cond.getElseExpression(), curConstraint, parentsOfHoles);
			Map<String, ArrayList<TypedExpression>> resultExprs = new HashMap<String, ArrayList<TypedExpression>>(thenResult.getExprs().size());
			for (TypedExpression condExpr: Utils.singleton(condResult.getExprs().values()))
				for (Map.Entry<String, ArrayList<TypedExpression>> thenExprs: thenResult.getExprs().entrySet()) {
					IJavaType thenType = "null".equals(thenExprs.getKey()) ? null : EclipseUtils.getFullyQualifiedType(thenExprs.getKey(), stack, target, typeCache);
					for (Map.Entry<String, ArrayList<TypedExpression>> elseExprs: elseResult.getExprs().entrySet()) {
						IJavaType elseType = "null".equals(elseExprs.getKey()) ? null : EclipseUtils.getFullyQualifiedType(elseExprs.getKey(), stack, target, typeCache);
						if ((thenType == null && EclipseUtils.isObject(elseType)) || (elseType == null && EclipseUtils.isObject(thenType)) || subtypeChecker.isSubtypeOf(thenType, elseType) || subtypeChecker.isSubtypeOf(elseType, thenType))
							for (TypedExpression thenExpr: thenExprs.getValue())
								for (TypedExpression elseExpr: elseExprs.getValue())
									Utils.addToMap(resultExprs, thenExprs.getKey(), ExpressionMaker.makeConditional(condExpr, thenExpr, elseExpr, thenExpr.getType()));
					}
				}
			return new ExpressionsAndTypeConstraints(resultExprs, thenResult.getTypeConstraint());
		}

		/**
		 * Fills infix skeleton pieces.
		 * @param node The infix part of the skeleton.
		 * @param curConstraint The constraint for the type of the
		 * expressions generated from this piece of the skeleton.
		 * @param parentsOfHoles All nodes that are parents of some hole.
		 * @return The synthesized expressions corresponding to this
		 * skeleton piece and the type constraint representing their types.
		 */
		private ExpressionsAndTypeConstraints fillInfixExpression(Expression node, TypeConstraint curConstraint, Set<ASTNode> parentsOfHoles) {
			try {
				InfixExpression infix = (InfixExpression)node;
				boolean isBooleanResult = infix.getOperator() == InfixExpression.Operator.CONDITIONAL_AND || infix.getOperator() == InfixExpression.Operator.CONDITIONAL_OR || infix.getOperator() == InfixExpression.Operator.EQUALS || infix.getOperator() == InfixExpression.Operator.NOT_EQUALS || infix.getOperator() == InfixExpression.Operator.LESS || infix.getOperator() == InfixExpression.Operator.LESS_EQUALS || infix.getOperator() == InfixExpression.Operator.GREATER || infix.getOperator() == InfixExpression.Operator.GREATER_EQUALS;
				if (isBooleanResult && !curConstraint.isFulfilledBy(booleanType, subtypeChecker, typeCache, stack, target))
					throw new TypeError("Incorrectly-typed operator " + infix.getOperator());
				TypeConstraint leftConstraint = curConstraint;
				TypeConstraint rightConstraint = curConstraint;
				if (infix.getOperator() == InfixExpression.Operator.CONDITIONAL_AND || infix.getOperator() == InfixExpression.Operator.CONDITIONAL_OR) {
					leftConstraint = new SupertypeBound(booleanType);
					rightConstraint = new SupertypeBound(booleanType);
				} else if (infix.getOperator() == InfixExpression.Operator.MINUS || infix.getOperator() == InfixExpression.Operator.TIMES || infix.getOperator() == InfixExpression.Operator.DIVIDE || infix.getOperator() == InfixExpression.Operator.LESS || infix.getOperator() == InfixExpression.Operator.LESS_EQUALS || infix.getOperator() == InfixExpression.Operator.GREATER || infix.getOperator() == InfixExpression.Operator.GREATER_EQUALS) {
					leftConstraint = makeSupertypeSet(intType);  // TODO: These should be all numeric types.
					rightConstraint = makeSupertypeSet(intType);
					if (!isBooleanResult && !curConstraint.isFulfilledBy(intType, subtypeChecker, typeCache, stack, target))
						throw new TypeError("Incorrectly-typed operator " + infix.getOperator());
				}
				ExpressionsAndTypeConstraints leftResult = fillSkeleton(infix.getLeftOperand(), leftConstraint, parentsOfHoles);
				ExpressionsAndTypeConstraints rightResult = fillSkeleton(infix.getRightOperand(), rightConstraint, parentsOfHoles);
				Map<String, ArrayList<TypedExpression>> resultExprs = new HashMap<String, ArrayList<TypedExpression>>(leftResult.getExprs().size());
				for (Map.Entry<String, ArrayList<TypedExpression>> leftExprs: leftResult.getExprs().entrySet()) {
					IJavaType leftType = "null".equals(leftExprs.getKey()) ? null : EclipseUtils.getFullyQualifiedType(leftExprs.getKey(), stack, target, typeCache);
					for (Map.Entry<String, ArrayList<TypedExpression>> rightExprs: rightResult.getExprs().entrySet()) {
						IJavaType rightType = "null".equals(rightExprs.getKey()) ? null : EclipseUtils.getFullyQualifiedType(rightExprs.getKey(), stack, target, typeCache);
						if ((leftType == null && EclipseUtils.isObject(rightType)) || (rightType == null && EclipseUtils.isObject(leftType)) || leftType.equals(rightType))
							if (!(EclipseUtils.isObject(leftType) && EclipseUtils.isObject(rightType) && (leftType != null && !"java.lang.String".equals(leftType.getName())) && (rightType != null && !"java.lang.String".equals(rightType.getName()))))
								for (TypedExpression leftExpr: leftExprs.getValue())
									for (TypedExpression rightExpr: rightExprs.getValue())
										if (leftExpr.getValue() == null || !leftExpr.getValue().isNull() || rightExpr.getValue() == null || !rightExpr.getValue().isNull()) {  // TODO: These two checks should be part of my constraint when I search for the child holes above.
											IJavaType resultType = isBooleanResult ? booleanType : leftExpr.getValue() == null || !leftExpr.getValue().isNull() ? leftExpr.getType() : rightExpr.getType();
											Utils.addToMap(resultExprs, leftExprs.getKey(), ExpressionMaker.makeInfix(target, leftExpr, infix.getOperator(), rightExpr, resultType));
										}
					}
				}
				TypeConstraint resultConstraint = isBooleanResult ? new SupertypeBound(booleanType) : leftConstraint;
				return new ExpressionsAndTypeConstraints(resultExprs, resultConstraint);
			} catch (DebugException ex) {
				throw new RuntimeException(ex);
			}
		}

		/**
		 * Fills instanceof skeleton pieces.
		 * @param instance The instanceof part of the skeleton.
		 * @param parentsOfHoles All nodes that are parents of some hole.
		 * @return The synthesized expressions corresponding to this
		 * skeleton piece and the type constraint representing their types.
		 */
		private ExpressionsAndTypeConstraints fillInstanceof(InstanceofExpression instance, Set<ASTNode> parentsOfHoles) {
			IJavaType castType = EclipseUtils.getType(instance.getRightOperand().toString(), stack, target, typeCache);
			ExpressionsAndTypeConstraints exprResult = fillSkeleton(instance.getLeftOperand(), new SameHierarchy(castType), parentsOfHoles);
			Map<String, ArrayList<TypedExpression>> resultExprs = new HashMap<String, ArrayList<TypedExpression>>(exprResult.getExprs().size());
			for (Map.Entry<String, ArrayList<TypedExpression>> res: exprResult.getExprs().entrySet())
				for (TypedExpression expr: res.getValue())
					Utils.addToMap(resultExprs, res.getKey(), ExpressionMaker.makeInstanceOf(expr, (Type)ExpressionMaker.resetAST(instance.getRightOperand()), booleanType, null));
			return new ExpressionsAndTypeConstraints(resultExprs, new SupertypeBound(booleanType));
		}

		/**
		 * Fills number literal skeleton pieces.
		 * @param node The number literal part of the skeleton.
		 * @return The synthesized expressions corresponding to this
		 * skeleton piece and the type constraint representing their types.
		 */
		private ExpressionsAndTypeConstraints fillNumberLiteral(Expression node) {
			String str = ((NumberLiteral)node).getToken();
			int lastChar = str.charAt(str.length() - 1);
			// Rules taken from: http://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html.
			IJavaType resultType = null;
			if (lastChar == 'l' || lastChar == 'L')
				resultType = EclipseUtils.getFullyQualifiedType("long", stack, target, typeCache);
			else if (lastChar == 'f' || lastChar == 'f')
				resultType = EclipseUtils.getFullyQualifiedType("float", stack, target, typeCache);
			else if (lastChar == 'd' || lastChar == 'd' || str.indexOf('.') != -1)
				resultType = EclipseUtils.getFullyQualifiedType("double", stack, target, typeCache);
			else
				resultType = intType;
			return new ExpressionsAndTypeConstraints(new TypedExpression(node, resultType), new SupertypeBound(resultType));
		}

		/**
		 * Fills parenthesized skeleton pieces.
		 * @param node The parenthesized part of the skeleton.
		 * @param curConstraint The constraint for the type of the
		 * expressions generated from this piece of the skeleton.
		 * @param parentsOfHoles All nodes that are parents of some hole.
		 * @return The synthesized expressions corresponding to this
		 * skeleton piece and the type constraint representing their types.
		 */
		private ExpressionsAndTypeConstraints fillParenthesized(ParenthesizedExpression paren, TypeConstraint curConstraint, Set<ASTNode> parentsOfHoles) {
			ExpressionsAndTypeConstraints exprResult = fillSkeleton(paren.getExpression(), curConstraint, parentsOfHoles);
			Map<String, ArrayList<TypedExpression>> resultExprs = new HashMap<String, ArrayList<TypedExpression>>(exprResult.getExprs().size());
			for (Map.Entry<String, ArrayList<TypedExpression>> res: exprResult.getExprs().entrySet())
				for (TypedExpression expr: res.getValue())
					Utils.addToMap(resultExprs, res.getKey(), ExpressionMaker.makeParenthesized(expr));
			return new ExpressionsAndTypeConstraints(resultExprs, exprResult.getTypeConstraint());
		}

		/**
		 * Fills postfix skeleton pieces.
		 *
		 * TODO: Support increment/decrement (and in prefix).
		 * I need to ensure that the expr is a non-final variable.
		 * @param node The postfix part of the skeleton.
		 * @param curConstraint The constraint for the type of the
		 * expressions generated from this piece of the skeleton.
		 * @param parentsOfHoles All nodes that are parents of some hole.
		 * @return The synthesized expressions corresponding to this
		 * skeleton piece and the type constraint representing their types.
		 */
		private ExpressionsAndTypeConstraints fillPostfix(PostfixExpression postfix, TypeConstraint curConstraint, Set<ASTNode> parentsOfHoles) {
			ExpressionsAndTypeConstraints exprResult = fillSkeleton(postfix.getOperand(), curConstraint, parentsOfHoles);
			Map<String, ArrayList<TypedExpression>> resultExprs = new HashMap<String, ArrayList<TypedExpression>>(exprResult.getExprs().size());
			for (Map.Entry<String, ArrayList<TypedExpression>> res: exprResult.getExprs().entrySet())
				for (TypedExpression expr: res.getValue())
					Utils.addToMap(resultExprs, res.getKey(), ExpressionMaker.makePostfix(target, expr, postfix.getOperator(), expr.getType()));
			return new ExpressionsAndTypeConstraints(resultExprs, curConstraint);
		}

		/**
		 * Fills prefix skeleton pieces.
		 * @param node The prefix part of the skeleton.
		 * @param curConstraint The constraint for the type of the
		 * expressions generated from this piece of the skeleton.
		 * @param parentsOfHoles All nodes that are parents of some hole.
		 * @return The synthesized expressions corresponding to this
		 * skeleton piece and the type constraint representing their types.
		 */
		private ExpressionsAndTypeConstraints fillPrefix(PrefixExpression prefix, TypeConstraint curConstraint, Set<ASTNode> parentsOfHoles) {
			ExpressionsAndTypeConstraints exprResult = fillSkeleton(prefix.getOperand(), curConstraint, parentsOfHoles);
			Map<String, ArrayList<TypedExpression>> resultExprs = new HashMap<String, ArrayList<TypedExpression>>(exprResult.getExprs().size());
			for (Map.Entry<String, ArrayList<TypedExpression>> res: exprResult.getExprs().entrySet())
				for (TypedExpression expr: res.getValue())
					Utils.addToMap(resultExprs, res.getKey(), ExpressionMaker.makePrefix(target, expr, prefix.getOperator(), expr.getType()));
			return new ExpressionsAndTypeConstraints(resultExprs, curConstraint);
		}

		/**
		 * Fills simple name (i.e., no qualifiers) skeleton pieces.
		 * @param node The simple name part of the skeleton.
		 * @param curConstraint The constraint for the type of the
		 * expressions generated from this piece of the skeleton.
		 * @return The synthesized expressions corresponding to this
		 * skeleton piece and the type constraint representing their types.
		 */
		private ExpressionsAndTypeConstraints fillSimpleName(Expression node, TypeConstraint curConstraint) {
			try {
				SimpleName name = (SimpleName)node;
				if (holeInfos.containsKey(name.getIdentifier())) {  // This is a hole.
					HoleInfo holeInfo = holeInfos.get(name.getIdentifier());
					SubMonitor childMonitor = SubMonitor.convert(monitor, "Hole generation and evaluation", 1);
					assert !holeInfo.isNegative();  // TODO: Use negative arg information.
					// TODO: Optimization: If this is the only hole, evaluate the skeleton directly?
					// TODO: Improve heuristics to reduce search space when too many holes.
					if (curConstraint instanceof FieldNameConstraint) {  // Field name hole
						FieldNameConstraint fieldNameConstraint = (FieldNameConstraint)curConstraint;
						if (holeInfo.getArgs() != null)
							fieldNameConstraint.setLegalNames(new HashSet<String>(holeInfo.getArgs()));
						return new ExpressionsAndTypeConstraints(getFieldsAndConstraint(name, fieldNameConstraint, true));
					} else if (curConstraint instanceof MethodNameConstraint) {  // Method name hole
						MethodNameConstraint methodNameConstraint = (MethodNameConstraint)curConstraint;
						if (holeInfo.getArgs() != null)
							methodNameConstraint.setLegalNames(new HashSet<String>(holeInfo.getArgs()));
						return new ExpressionsAndTypeConstraints(getMethodsAndConstraint(name, methodNameConstraint, true));
					} else {  // Expression hole
						ArrayList<FullyEvaluatedExpression> values;
						if (holeInfo.getArgs() != null) {  // If the user supplied potential expressions, use them.
							// Get the correct type of each expression.
							ArrayList<TypedExpression> fakeTypedHoleInfos = new ArrayList<TypedExpression>(holeInfo.getArgs().size());
							for (String s: holeInfo.getArgs()) {
								Expression e = (Expression)ExpressionMaker.resetAST(EclipseUtils.parseExpr(parser, s));
								// The current evaluation manager needs to know the type of the expression, so we figure it out.
								IJavaType type = curConstraint instanceof DesiredType ? ((DesiredType)curConstraint).getDesiredType() : ((SingleTypeConstraint)fillSkeleton(e, curConstraint, null).getTypeConstraint()).getTypeConstraint();
								fakeTypedHoleInfos.add(new TypedExpression(e, type));
							}
							// Evaluate all the expressions.
							values = evalManager.evaluateExpressions(fakeTypedHoleInfos, null, null, childMonitor);
						} else  // If the user did not provide potential expressions, synthesize some.
							values = expressionGenerator.generateExpression(null, curConstraint, null, childMonitor, holeInfos.size() == 1 ? 1 : 0);
						// Group the expressions by their type.
						Map<String, ArrayList<EvaluatedExpression>> valuesByType = new HashMap<String, ArrayList<EvaluatedExpression>>();
						List<IJavaType> resultTypes = new ArrayList<IJavaType>(values.size());
						for (EvaluatedExpression e: values) {
							resultTypes.add(e.getType());
							String typeName = e.getType() == null ? "null" : e.getType().getName();
							Utils.addToMap(valuesByType, typeName, e);
						}
						// Group the expressions by the constraints they satisfy.
						TypeConstraint resultConstraint = getSupertypeConstraintForTypes(resultTypes);
						IJavaType[] constraintTypes = curConstraint.getTypes(stack, target, typeCache);
						Map<String, ArrayList<TypedExpression>> typedValuesForType = new HashMap<String, ArrayList<TypedExpression>>(constraintTypes.length);
						for (Entry<String, ArrayList<EvaluatedExpression>> exprs: valuesByType.entrySet()) {
							// Ensure that the type of these expressions satisfies some constraint.
							IJavaType curType = exprs.getValue().get(0).getType();
							boolean satisfiesSomeConstraint = false;
							for (IJavaType constraintType: constraintTypes) {
								if (subtypeChecker.isSubtypeOf(curType, constraintType)) {
									satisfiesSomeConstraint = true;
									break;
								}
							}
							if (satisfiesSomeConstraint) {
								// Add the results.
								String typeName = exprs.getKey();
								for (EvaluatedExpression e: exprs.getValue())
									Utils.addToMap(typedValuesForType, typeName, e);
							}
						}
						return new ExpressionsAndTypeConstraints(typedValuesForType, resultConstraint);
					}
				} else if (curConstraint instanceof FieldNameConstraint) {
					return new ExpressionsAndTypeConstraints(getFieldsAndConstraint(name, (FieldNameConstraint)curConstraint, false));
				} else if (curConstraint instanceof MethodNameConstraint) {
					return new ExpressionsAndTypeConstraints(getMethodsAndConstraint(name, (MethodNameConstraint)curConstraint, false));
				} else {
					IJavaVariable var = stack.findVariable(name.getIdentifier());
					if (var != null)
						return new ExpressionsAndTypeConstraints(new EvaluatedExpression(name, var.getJavaType(), new Value((IJavaValue)var.getValue())), new SupertypeBound(var.getJavaType()));
					IJavaType type = EclipseUtils.getTypeAndLoadIfNeeded(name.getIdentifier(), stack, target, typeCache);
					assert type != null : name.getIdentifier();
					return new ExpressionsAndTypeConstraints(ExpressionMaker.makeStaticName(name.getIdentifier(), (IJavaReferenceType)type), new SupertypeBound(type));
				}
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
		}

		/**
		 * Fills type literal skeleton pieces.
		 * @param node The type literal part of the skeleton.
		 * @return The synthesized expressions corresponding to this
		 * skeleton piece and the type constraint representing their types.
		 */
		private ExpressionsAndTypeConstraints fillTypeLiteral(Expression node) {
			try {
				IJavaType type = EclipseUtils.getType(((TypeLiteral)node).getType().toString(), stack, target, typeCache);
				IJavaClassObject classObj = ((IJavaReferenceType)type).getClassObject();
				IJavaType classObjType = classObj.getJavaType();
				return new ExpressionsAndTypeConstraints(new EvaluatedExpression(node, classObjType, new Value(classObj)), new SupertypeBound(classObjType));
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
		}
		
		/* Helper functions. */

		/**
		 * Gets the type constraint for the given field name and gets the
		 * possible fields if it represents a hole.
		 * @param name The name of the field, which is either a real
		 * name or a fake name for a hole.
		 * @param fieldNameConstraint The constraint on the name of
		 * the field.
		 * @param isHole Whether or not this is a hole.
		 * @return The type constraint for the given field name.
		 */
		private TypeConstraint getFieldsAndConstraint(SimpleName name, FieldNameConstraint fieldNameConstraint, boolean isHole) {
			Map<String, ArrayList<Field>> fieldsByType = fieldNameConstraint.getFields(stack, target, subtypeChecker, typeCache);
			List<IJavaType> types = new ArrayList<IJavaType>();
			for (ArrayList<Field> fields: fieldsByType.values())
				for (Field field: fields)
					types.add(EclipseUtils.getTypeAndLoadIfNeeded(field.typeName(), stack, target, typeCache));
			if (isHole)
				holeFields.put(name.getIdentifier(), fieldsByType);
			return getSupertypeConstraintForTypes(types);
		}

		/**
		 * Gets the type constraint for the given method name and gets the
		 * possible methods if it represents a hole.
		 * @param name The name of the method, which is either a real
		 * name or a fake name for a hole.
		 * @param methodNameConstraint The constraint on the name of
		 * the method.
		 * @param isHole Whether or not this is a hole.
		 * @return The type constraint for the given method name.
		 */
		private TypeConstraint getMethodsAndConstraint(SimpleName name, MethodNameConstraint methodNameConstraint, boolean isHole) {
			Map<String, ArrayList<Method>> methodsByType = methodNameConstraint.getMethods(stack, target, subtypeChecker, typeCache);
			List<IJavaType> types = new ArrayList<IJavaType>();
			for (ArrayList<Method> methods: methodsByType.values())
				for (Method method: methods)
					types.add(EclipseUtils.getTypeAndLoadIfNeededAndExists(method.returnTypeName(), stack, target, typeCache));
			if (isHole)
				holeMethods.put(name.getIdentifier(), methodsByType);
			return getSupertypeConstraintForTypes(types);
		}

		/**
		 * Fills non-super field access skeleton pieces.
		 * @param qualifier The qualifier of this field access.
		 * @param name The name of this field access.
		 * @param parentsOfHoles All nodes that are parents of some hole.
		 * @param curConstraint The constraint for the type of the
		 * expressions generated from this piece of the skeleton.
		 * @return The synthesized expressions corresponding to this
		 * skeleton piece and the type constraint representing their types.
		 */
		private ExpressionsAndTypeConstraints fillNormalField(Expression qualifier, SimpleName name, Set<ASTNode> parentsOfHoles, TypeConstraint curConstraint) {
			TypeConstraint qualifierConstraint = new FieldConstraint(parentsOfHoles.contains(name) ? null : name.getIdentifier(), curConstraint);
			ExpressionsAndTypeConstraints receiverResult = fillSkeleton(qualifier, qualifierConstraint, parentsOfHoles);
			return fillField(name, null, parentsOfHoles, curConstraint, receiverResult);
		}

		/**
		 * Fills field access skeleton pieces.
		 * @param name The name of this field access.
		 * @param superQualifier The qualifier of the super field access
		 * (which can be null) or null if it is not a super field access.
		 * @param parentsOfHoles All nodes that are parents of some hole.
		 * @param curConstraint The constraint for the type of the
		 * expressions generated from this piece of the skeleton.
		 * @param receiverResult The synthesized expressions and the type
		 * constraint for the receiver of this field access. 
		 * @return The synthesized expressions corresponding to this
		 * skeleton piece and the type constraint representing their types.
		 */
		private ExpressionsAndTypeConstraints fillField(SimpleName name, Name superQualifier, Set<ASTNode> parentsOfHoles, TypeConstraint curConstraint, ExpressionsAndTypeConstraints receiverResult) {
			FieldNameConstraint fieldNameConstraint = new FieldNameConstraint(receiverResult.getTypeConstraint(), curConstraint);
			if (!parentsOfHoles.contains(name))
				fieldNameConstraint.setLegalNames(new HashSet<String>(Arrays.asList(new String[] { name.toString() })));
			ExpressionsAndTypeConstraints fieldResult = fillSkeleton(name, fieldNameConstraint, parentsOfHoles);
			Map<String, ArrayList<Field>> fields = null;
			if (holeFields.containsKey(name.getIdentifier()))
				fields = holeFields.get(name.getIdentifier());
			else
				fields = fieldNameConstraint.getFields(stack, target, subtypeChecker, typeCache);
			Map<String, ArrayList<TypedExpression>> resultExprs = new HashMap<String, ArrayList<TypedExpression>>(fieldResult.getTypeConstraint().getTypes(stack, target, typeCache).length);
			for (Map.Entry<String, ArrayList<TypedExpression>> receiverExprs: receiverResult.getExprs().entrySet())
				if (fields.containsKey(receiverExprs.getKey())) {
					for (TypedExpression receiverExpr: receiverExprs.getValue())
						if (receiverExpr.getValue() == null || !receiverExpr.getValue().isNull())
							for (Field field: fields.get(receiverExprs.getKey())) {
								if (!ExpressionMaker.isStatic(receiverExpr.getExpression()) || field.isStatic()) {
									String fieldTypeName = field.typeName();
									IJavaValue fieldValue = null;
									try {
										if (field.isStatic())
											fieldValue = (IJavaValue)((IJavaReferenceType)receiverExpr.getType()).getField(field.name()).getValue();
										else if (receiverExpr.getValue() != null)
											fieldValue = (IJavaValue)((IJavaObject)receiverExpr.getValue()).getField(field.name(), !field.declaringType().name().equals(receiverExpr.getType().getName())).getValue();
									} catch (DebugException e) {
										e.printStackTrace();
									}
									TypedExpression newExpr = null;
									if (receiverExpr.getExpression() == null)
										newExpr = ExpressionMaker.makeSuperFieldAccess(superQualifier, field.name(), EclipseUtils.getTypeAndLoadIfNeeded(fieldTypeName, stack, target, typeCache), fieldValue);
									else
										newExpr = ExpressionMaker.makeFieldAccess(receiverExpr, field.name(), EclipseUtils.getTypeAndLoadIfNeeded(fieldTypeName, stack, target, typeCache), fieldValue);
									Utils.addToMap(resultExprs, fieldTypeName, newExpr);
								}
							}
				}
			return new ExpressionsAndTypeConstraints(resultExprs, fieldResult.getTypeConstraint());
		}

		/**
		 * Fills method call skeleton pieces.
		 * @param node The method call part of the skeleton.
		 * @param name The name of this method call.
		 * @param arguments The expression arguments to this method call.
		 * @param parentsOfHoles All nodes that are parents of some hole.
		 * @param curConstraint The constraint for the type of the
		 * expressions generated from this piece of the skeleton.
		 * @param argTypes The type constraints on the arguments to this
		 * method call.
		 * @param receiverResult The synthesized expressions and the type
		 * constraint for the receiver of this field access. 
		 * @return The synthesized expressions corresponding to this
		 * skeleton piece and the type constraint representing their types.
		 */
		private ExpressionsAndTypeConstraints fillMethod(Expression node, SimpleName name, List<?> arguments, Set<ASTNode> parentsOfHoles, TypeConstraint curConstraint, ArrayList<TypeConstraint> argTypes, ExpressionsAndTypeConstraints receiverResult) {
			MethodNameConstraint methodNameConstraint = new MethodNameConstraint(receiverResult.getTypeConstraint(), curConstraint, argTypes);
			if (!parentsOfHoles.contains(name))
				methodNameConstraint.setLegalNames(new HashSet<String>(Arrays.asList(new String[] { name.toString() })));
			ExpressionsAndTypeConstraints methodResult = fillSkeleton(name, methodNameConstraint, parentsOfHoles);
			Map<String, ArrayList<Method>> methods = null;
			if (holeMethods.containsKey(name.getIdentifier()))
				methods = holeMethods.get(name.getIdentifier());
			else
				methods = methodNameConstraint.getMethods(stack, target, subtypeChecker, typeCache);
			boolean isListHole = argTypes == null;
			ArrayList<ExpressionsAndTypeConstraints> argResults = getAndFillArgs(arguments, parentsOfHoles, methods, isListHole);
			Map<String, ArrayList<TypedExpression>> resultExprs = new HashMap<String, ArrayList<TypedExpression>>(methodResult.getTypeConstraint().getTypes(stack, target, typeCache).length);
			if (receiverResult.getExprs() != null) {
				for (Map.Entry<String, ArrayList<TypedExpression>> receiverExprs: receiverResult.getExprs().entrySet())
					if (methods.containsKey(receiverExprs.getKey()))
						for (TypedExpression receiverExpr: receiverExprs.getValue())
							if (receiverExpr.getValue() == null || !receiverExpr.getValue().isNull()) {
								OverloadChecker overloadChecker = new OverloadChecker(receiverExpr.getType());
								for (Method method: methods.get(receiverExprs.getKey()))
									if (!ExpressionMaker.isStatic(receiverExpr.getExpression()) || method.isStatic())
										buildCalls(method, receiverExpr, node, argResults, isListHole, resultExprs, overloadChecker);
							}
			} else {  // No receiver (implicit this).
				IJavaType thisType = getThisType();
				OverloadChecker overloadChecker = new OverloadChecker(thisType);
				if (!methods.isEmpty())
					for (Method method: Utils.singleton(methods.values()))
						buildCalls(method, ExpressionMaker.makeThis(getThis(), thisType), node, argResults, isListHole, resultExprs, overloadChecker);
			}
			return new ExpressionsAndTypeConstraints(resultExprs, methodResult.getTypeConstraint());
		}
		
		/**
		 * Gets the type constraints for the given argument nodes.
		 * @param argNodes The expression nodes representing
		 * the arguments.
		 * @param parentsOfHoles All nodes that are parents of
		 * some hole.
		 * @return The type constraints for the given argument nods.
		 */
		private ArrayList<TypeConstraint> getArgTypes(List<?> argNodes, Set<ASTNode> parentsOfHoles) {
			if (argNodes.size() == 1) {  // Special case to check for a single list hole.
				Expression arg = (Expression)argNodes.get(0);
				if (arg instanceof SimpleName) {
					SimpleName argName = (SimpleName)arg;
					if (holeInfos.containsKey(argName.getIdentifier()) && holeInfos.get(argName.getIdentifier()) instanceof ListHoleInfo)
						return null;
				}
			}
			ArrayList<TypeConstraint> argTypes = new ArrayList<TypeConstraint>(argNodes.size());
			for (Object argObj: argNodes) {
				Expression arg = (Expression)argObj;
				// Invert supertype constraints to subtype constraints for contravariance.
				TypeConstraint typeConstraint = fillSkeleton(arg, UnknownConstraint.getUnknownConstraint(), parentsOfHoles).getTypeConstraint();
				if (typeConstraint instanceof DesiredType)
					typeConstraint = new SubtypeBound(((DesiredType)typeConstraint).getDesiredType());
				else if (typeConstraint instanceof SupertypeBound)
					typeConstraint = new SubtypeBound(((SupertypeBound)typeConstraint).getSupertypeBound());
				else if (typeConstraint instanceof SupertypeSet)
					typeConstraint = new SubtypeSet(((SupertypeSet)typeConstraint).getTypeSet());
				argTypes.add(parentsOfHoles.contains(arg) ? null : typeConstraint);
			}
			return argTypes;
		}

		/**
		 * Fills argument skeleton pieces.
		 * @param arguments The expression arguments.
		 * @param parentsOfHoles All nodes that are parents of some hole.
		 * @param methodsByType A map containing all potential methods
		 * indexed by the types of their receivers.
		 * @param isListHole Whether or not this argument is a list hole
		 * and can thus take in an arbitrary number of arguments.
		 * @return The synthesized expressions corresponding to the given
		 * arguments and the type constraint representing their types.
		 * If this is a list hole, the result only has one element, which
		 * contains all the potential expressions.
		 */
		private ArrayList<ExpressionsAndTypeConstraints> getAndFillArgs(List<?> arguments, Set<ASTNode> parentsOfHoles, Map<String, ArrayList<Method>> methodsByType, boolean isListHole) {
			int count = isListHole ? 1 : arguments.size();
			ArrayList<ExpressionsAndTypeConstraints> argResults = new ArrayList<ExpressionsAndTypeConstraints>(count);
			for (int i = 0; i < count; i++) {
				Expression arg = (Expression)arguments.get(0);
				List<IJavaType> argConstraints = new ArrayList<IJavaType>();
				for (ArrayList<Method> methods: methodsByType.values())
					for (Method method: methods) {
						if (isListHole)
							for (int j = 0; j < method.argumentTypeNames().size(); j++)
								argConstraints.add(EclipseUtils.getTypeAndLoadIfNeeded((String)method.argumentTypeNames().get(j), stack, target, typeCache));
						else
							argConstraints.add(EclipseUtils.getTypeAndLoadIfNeeded((String)method.argumentTypeNames().get(i), stack, target, typeCache));
					}
				TypeConstraint argConstraint = getSupertypeConstraintForTypes(argConstraints);
				argResults.add(fillSkeleton(arg, argConstraint, parentsOfHoles));
			}
			return argResults;
		}

		/**
		 * 
		 * @param method The method to call.
		 * @param receiverExpr The receiver object.
		 * @param callNode The node representing the call
		 * piece of the skeleton.
		 * @param argResults The potential expressions and type
		 * constraints for the arguments.  If this call uses a
		 * list hole, it contains only one element.
		 * @param isListHole Whether or not this call uses a
		 * list hole.
		 * @param resultExprs Map that stores the resulting
		 * expressions.
		 * @param overloadChecker The overload checker.
		 */
		private void buildCalls(Method method, TypedExpression receiverExpr, Expression callNode, ArrayList<ExpressionsAndTypeConstraints> argResults, boolean isListHole, Map<String, ArrayList<TypedExpression>> resultExprs, OverloadChecker overloadChecker) {
			try {
				String methodReturnTypeName = method.isConstructor() ? receiverExpr.getType().getName() : method.returnTypeName();  // The method class returns void for the return type of constructors....
				overloadChecker.setMethod(method);
				ArrayList<ArrayList<TypedExpression>> allPossibleActuals = new ArrayList<ArrayList<TypedExpression>>(method.argumentTypeNames().size());
				for (int i = 0; i < method.argumentTypeNames().size(); i++) {
					IJavaType argType = EclipseUtils.getTypeAndLoadIfNeeded((String)method.argumentTypeNames().get(i), stack, target, typeCache);
					ArrayList<TypedExpression> allArgs = new ArrayList<TypedExpression>();
					for (ArrayList<TypedExpression> curArgs: argResults.get(isListHole ? 0 : i).getExprs().values()) {
						IJavaType curType = curArgs.get(0).getType();
						if (subtypeChecker.isSubtypeOf(curType, argType)) {
							if (overloadChecker.needsCast(argType, curType, i))
								for (TypedExpression cur: curArgs)
									allArgs.add(ExpressionMaker.makeCast(cur, argType, cur.getValue()));
							else
								allArgs.addAll(curArgs);
						}
					}
					if (allArgs.size() == 0)
						return;
					allPossibleActuals.add(allArgs);
				}
				makeAllCalls(method, method.name(), methodReturnTypeName, receiverExpr, callNode, EclipseUtils.getTypeAndLoadIfNeeded(methodReturnTypeName, stack, target, typeCache), getThisType(), allPossibleActuals, new ArrayList<TypedExpression>(allPossibleActuals.size()), resultExprs);
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
		}

		/**
		 * Creates all possible calls using the given actuals.
		 * @param method The method being called.
		 * @param name The method name.
		 * @param constraintName The name of the return type of the method, which
		 * is used to store the resulting expressions.
		 * @param receiver The receiving object.
		 * @param callNode The node representing the call piece of the skeleton.
		 * @param returnType The return type of the function.
		 * @param thisType The type of the this object.
		 * @param possibleActuals A list of all the possible actuals for each argument.
		 * @param curActuals The current list of actuals, which is built
		 * up through recursion.
		 * @param resultExprs The map that stores the resulting expressions.
		 */
		private void makeAllCalls(Method method, String name, String constraintName, TypedExpression receiver, Expression callNode, IJavaType returnType, IJavaType thisType, ArrayList<ArrayList<TypedExpression>> possibleActuals, ArrayList<TypedExpression> curActuals, Map<String, ArrayList<TypedExpression>> resultExprs) {
			if (curActuals.size() == possibleActuals.size()) {
				TypedExpression callExpr = null;
				if (callNode instanceof SuperMethodInvocation)
					callExpr = ExpressionMaker.makeSuperCall(name, ((SuperMethodInvocation)callNode).getQualifier(), curActuals, returnType, null, method);
				else
					callExpr = ExpressionMaker.makeCall(name, receiver, curActuals, returnType, thisType, method, target);
				Utils.addToMap(resultExprs, constraintName, callExpr);
			}
			else {
				int argNum = curActuals.size();
				for (TypedExpression e : possibleActuals.get(argNum)) {
					curActuals.add(e);
					makeAllCalls(method, name, constraintName, receiver, callNode, returnType, thisType, possibleActuals, curActuals, resultExprs);
					curActuals.remove(argNum);
				}
			}
		}
		
		/**
		 * Gets a constraint saying that something must be a subtype
		 * of one of the given types.
		 * Note that the input types can contain duplicates, which
		 * this method filters out.
		 * @param types The list of types.
		 * @return A constraint that ensures that something is
		 * a subtype of one of the given types.
		 */
		private static TypeConstraint getSupertypeConstraintForTypes(Collection<IJavaType> types) {
			try {
				// Putting IJavaTypes into a Set doesn't remove duplicates, so I need to map them based on their names.
				Map<String, IJavaType> typesMap = new HashMap<String, IJavaType>();
				for (IJavaType type: types) {
					if (type != null) {  // Ignore nulls.
						String typeName = type.getName();
						if (!typesMap.containsKey(typeName))
							typesMap.put(typeName, type);
					}
				}
				Collection<IJavaType> uniqueTypes = typesMap.values();
				return uniqueTypes.size() == 1 ? new SupertypeBound(uniqueTypes.iterator().next()) : new SupertypeSet(new HashSet<IJavaType>(uniqueTypes));
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
		}
		
		/**
		 * Gets a type constraint saying that something must be
		 * a subtype of one of the given types.
		 * @param types The list of types.
		 * @return A constraint that ensures that something is
		 * a subtype of one of the given types.
		 */
		private static SupertypeSet makeSupertypeSet(IJavaType... types) {
			Set<IJavaType> supertypeSet = new HashSet<IJavaType>(types.length);
			for (IJavaType type: types)
				supertypeSet.add(type);
			return new SupertypeSet(supertypeSet);
		}

		/**
		 * Gets the type of the this object.
		 * @return The type of the this object.
		 */
		private IJavaType getThisType() {
			try {
				return stack.getReferenceType();
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
		}

		/**
		 * Gets the this object.
		 * @return The this object.
		 */
		private IJavaValue getThis() {
			try {
				return stack.getThis();
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
		}

		/**
		 * Gets the supertype of the type with the given name,
		 * or the supertype of the this object if name is null.
		 * @param name The type whose supertype to get.
		 * If this is null, we get the supertype of this.
		 * @return The supertype of the type with the given name
		 * or of the this type if name is null.
		 */
		private IJavaType getSuperType(Name name) {
    		try {
    			IJavaType curType = name == null ? stack.getReferenceType() : EclipseUtils.getType(name.toString(), stack, target, typeCache);
				return ((IJavaClassType)curType).getSuperclass();
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
		}
    	
		/**
		 * Gets all the constructors of the given type that
		 * fulfill the given argument constraints.
		 * @param type The type whose constructors we want.
		 * @param argConstraints The constraints on the
		 * arguments to the constructor.
		 * @return All the constructors of the given type
		 * that fulfill the given argument constraints.
		 */
    	private Map<String, ArrayList<Method>> getConstructors(IJavaType type, ArrayList<TypeConstraint> argConstraints) {
			try {
				Map<String, ArrayList<Method>> methodsByType = new HashMap<String, ArrayList<Method>>(1);
				String typeName = type.getName();
	    		for (Method method: ExpressionGenerator.getMethods(type))
					if (ExpressionGenerator.isLegalMethod(method, stack.getReferenceType(), true) && MethodNameConstraint.fulfillsArgConstraints(method, argConstraints, subtypeChecker, typeCache, stack, target))
						Utils.addToMap(methodsByType, typeName, method);
	    		return methodsByType;
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
    	}
		
	}
	
	/**
	 * A class that contains both type constraints the the
	 * possible expressions that can satisfy them.
	 */
	private static class ExpressionsAndTypeConstraints {

		private final Map<String, ArrayList<TypedExpression>> exprs;
		private final TypeConstraint typeConstraint;
		
		public ExpressionsAndTypeConstraints(Map<String, ArrayList<TypedExpression>> exprs, TypeConstraint typeConstraint) {
			this.exprs = exprs;
			this.typeConstraint = typeConstraint;
		}
		
		public ExpressionsAndTypeConstraints(TypedExpression expr, TypeConstraint typeConstraint) {
			this.exprs = new HashMap<String, ArrayList<TypedExpression>>(1);
			try {
				Utils.addToMap(this.exprs, expr.getType() == null ? "null" : expr.getType().getName(), expr);
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
			this.typeConstraint = typeConstraint;
		}
		
		public ExpressionsAndTypeConstraints(TypeConstraint typeConstraint) {
			this.exprs = null;
			this.typeConstraint = typeConstraint;
		}

		/**
		 * Gets the potential expressions this object represents.
		 * The return value is a map from type name to the expressions
		 * of that type.
		 * @return The potential expressions this object represents,
		 * grouped by the types they satisfy.
		 */
		public Map<String, ArrayList<TypedExpression>> getExprs() {
			return exprs;
		}

		/**
		 * Gets the type constraint this object represents.
		 * @return The type constraint this object represents.
		 */
		public TypeConstraint getTypeConstraint() {
			return typeConstraint;
		}
		
	}

}
