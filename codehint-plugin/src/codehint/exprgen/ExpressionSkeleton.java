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
import org.eclipse.core.runtime.OperationCanceledException;
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
import org.eclipse.jdt.debug.core.IJavaReferenceType;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;

import com.sun.jdi.Field;
import com.sun.jdi.Method;

import codehint.dialogs.InitialSynthesisDialog;
import codehint.expreval.EvaluatedExpression;
import codehint.expreval.EvaluationManager;
import codehint.exprgen.typeconstraint.DesiredType;
import codehint.exprgen.typeconstraint.FieldConstraint;
import codehint.exprgen.typeconstraint.FieldNameConstraint;
import codehint.exprgen.typeconstraint.MethodConstraint;
import codehint.exprgen.typeconstraint.MethodNameConstraint;
import codehint.exprgen.typeconstraint.SameHierarchy;
import codehint.exprgen.typeconstraint.SingleTypeConstraint;
import codehint.exprgen.typeconstraint.SupertypeBound;
import codehint.exprgen.typeconstraint.SupertypeSet;
import codehint.exprgen.typeconstraint.TypeConstraint;
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
     * @param evalManager The evaluation manager.
     * @param expressionGenerator The expression generator.
     */
	private ExpressionSkeleton(String sugaredString, Expression node, Map<String, HoleInfo> holeInfos, IJavaDebugTarget target, IJavaStackFrame stack, SubtypeChecker subtypeChecker, EvaluationManager evalManager, ExpressionGenerator expressionGenerator) {
		this.sugaredString = sugaredString;
		this.expression = node;
		this.holeInfos = holeInfos;
		this.target = target;
		this.stack = stack;
		this.subtypeChecker = subtypeChecker;
		this.evalManager = evalManager;
		this.expressionGenerator = expressionGenerator;
	}

	/**
	 * Creates an ExpressionSkeleton from the given sugared string.
	 * @param skeletonStr The sugared string from which to create the skeleton.
	 * @param target The debug target.
	 * @param stack The stack frame.
     * @param subtypeChecker The subtype checker.
     * @param evalManager The evaluation manager.
     * @param expressionGenerator The expression generator.
	 * @return The ExpressionSkeleton representing the given sugared string.
	 */
	public static ExpressionSkeleton fromString(String skeletonStr, IJavaDebugTarget target, IJavaStackFrame stack, SubtypeChecker subtypeChecker, EvaluationManager evalManager, ExpressionGenerator expressionGenerator) {
		return SkeletonParser.rewriteHoleSyntax(skeletonStr, target, stack, subtypeChecker, evalManager, expressionGenerator);
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
		public static String isLegalSkeleton(String str, String varTypeName, IJavaStackFrame stackFrame, IAstEvaluationEngine evaluationEngine) {
			try {
				Expression skeletonExpr = rewriteHoleSyntax(str, new HashMap<String, HoleInfo>());
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
		private static Expression rewriteHoleSyntax(String sugaredString, Map<String, HoleInfo> holeInfos) {
			String str = sugaredString;
			for (int holeNum = 0; true; holeNum++) {
				int holeStart = str.indexOf(HOLE_SYNTAX);
				if (holeStart != -1)
					str = rewriteSingleHole(str, holeStart, holeNum, holeInfos);
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
	     * @param subtypeChecker The subtype checker.
	     * @param evalManager The evaluation manager.
	     * @param expressionGenerator The expression generator.
		 * @return The ExpressionSkeleton representing the given sugared string.
		 */
		private static ExpressionSkeleton rewriteHoleSyntax(String sugaredString, IJavaDebugTarget target, IJavaStackFrame stack, SubtypeChecker subtypeChecker, EvaluationManager evalManager, ExpressionGenerator expressionGenerator) {
			Map<String, HoleInfo> holeInfos = new HashMap<String, HoleInfo>();
			Expression expr = rewriteHoleSyntax(sugaredString, holeInfos);
			return new ExpressionSkeleton(sugaredString, (Expression)ExpressionMaker.resetAST(expr), holeInfos, target, stack, subtypeChecker, evalManager, expressionGenerator);
		}
	
		/**
		 * Rewrites a single ?? hole in a skeleton string to its
		 * desugared form.
		 * @param str The string.
		 * @param holeIndex The starting index of the hole.
		 * @param holeNum A unique number identifying the hole.
		 * @param holeInfos Map that stores information about holes.
		 * @return The original string with this hole desugared.
		 */
		private static String rewriteSingleHole(String str, int holeIndex, int holeNum, Map<String, HoleInfo> holeInfos) {
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
			holeInfos.put(holeName, makeHoleInfo(args, isNegative));
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
		 * @return The HoleInfo representing information
		 * about this hole.
		 */
		private static HoleInfo makeHoleInfo(String argsStr, boolean isNegative) {
			ArrayList<String> args = null;
			if (argsStr != null) {
				ASTNode node = EclipseUtils.parseExpr(parser, DESUGARED_HOLE_NAME + "(" + argsStr + ")");
				if (node instanceof MethodInvocation) {
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
	
	public ArrayList<EvaluatedExpression> synthesize(Property property, IJavaType varStaticType, InitialSynthesisDialog synthesisDialog, IProgressMonitor monitor) {
		try {
			long startTime = System.currentTimeMillis();
			// TODO: Improve progress monitor so it shows you which evaluation it is.
			TypeConstraint typeConstraint = getInitialTypeConstraint(varStaticType, property, stack, target);
			ArrayList<EvaluatedExpression> results;
			if (HOLE_SYNTAX.equals(sugaredString))  // Optimization: Optimize special case of "??" skeleton by simply calling old ExprGen code directly.
				results = expressionGenerator.generateExpression(property, typeConstraint, synthesisDialog, monitor, 1);
			else {
				monitor.beginTask("Skeleton generation", holeInfos.size() + 2);
				ArrayList<TypedExpression> exprs = SkeletonFiller.fillSkeleton(expression, typeConstraint, holeInfos, stack, target, evalManager, expressionGenerator, subtypeChecker, monitor);
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
	
	private static TypeConstraint getInitialTypeConstraint(IJavaType varStaticType, Property property, IJavaStackFrame stack, IJavaDebugTarget target) throws DebugException {
		if (property instanceof ValueProperty)
			return new DesiredType(((ValueProperty)property).getValue().getJavaType());
		else if (property instanceof TypeProperty)
			return new SupertypeBound(EclipseUtils.getType(((TypeProperty)property).getTypeName(), stack, target));
		else if (property instanceof LambdaProperty) {
			String typeName = ((LambdaProperty)property).getTypeName();
			if (typeName != null)
				return new SupertypeBound(EclipseUtils.getType(typeName, stack, target));
		}
		return new SupertypeBound(varStaticType);
	}
	
	private static class HoleParentSetter extends ASTVisitor {

		private final Map<String, HoleInfo> holeInfos;
		private final Set<ASTNode> parentsOfHoles;
		
		private HoleParentSetter(Map<String, HoleInfo> holeInfos) {
			this.holeInfos = holeInfos;
			parentsOfHoles = new HashSet<ASTNode>();
		}
		
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
	
	public static class TypeError extends RuntimeException {
		
		private static final long serialVersionUID = 1L;

		public TypeError(String msg) {
			super(msg);
		}
		
	}
	
	private static class SkeletonFiller {

		private final Map<String, Map<String, ArrayList<EvaluatedExpression>>> holeValues;
		private final Map<String, Map<String, ArrayList<Field>>> holeFields;
		private final Map<String, Map<String, ArrayList<Method>>> holeMethods;
		private final Map<String, HoleInfo> holeInfos;
		private final EvaluationManager evalManager;
		private final ExpressionGenerator expressionGenerator;
		private final SubtypeChecker subtypeChecker;
		private final IJavaStackFrame stack;
		private final IJavaDebugTarget target;
		private final IProgressMonitor monitor;
		
		private final IJavaType intType;
		private final IJavaType booleanType;
		
		private SkeletonFiller(Map<String, HoleInfo> holeInfos, IJavaStackFrame stack, IJavaDebugTarget target, EvaluationManager evalManager, ExpressionGenerator expressionGenerator, SubtypeChecker subtypeChecker, IProgressMonitor monitor) {
			this.holeValues = new HashMap<String, Map<String, ArrayList<EvaluatedExpression>>>();
			this.holeFields = new HashMap<String, Map<String, ArrayList<Field>>>();
			this.holeMethods = new HashMap<String, Map<String, ArrayList<Method>>>();
			this.holeInfos = holeInfos;
			this.evalManager = evalManager;
			this.expressionGenerator = expressionGenerator;
			this.subtypeChecker = subtypeChecker;
			this.stack = stack;
			this.target = target;
			this.monitor = monitor;
			intType = EclipseUtils.getFullyQualifiedType("int", target);
			booleanType = EclipseUtils.getFullyQualifiedType("boolean", target);
		}
		
		public static ArrayList<TypedExpression> fillSkeleton(Expression skeleton, TypeConstraint initialTypeConstraint, Map<String, HoleInfo> holeInfos, IJavaStackFrame stack, IJavaDebugTarget target, EvaluationManager evalManager, ExpressionGenerator expressionGenerator, SubtypeChecker subtypeChecker, IProgressMonitor monitor) {
			SkeletonFiller filler = new SkeletonFiller(holeInfos, stack, target, evalManager, expressionGenerator, subtypeChecker, monitor);
			ExpressionsAndTypeConstraints result = filler.fillSkeleton(skeleton, initialTypeConstraint, HoleParentSetter.getParentsOfHoles(holeInfos, skeleton));
			ArrayList<TypedExpression> exprs = new ArrayList<TypedExpression>();
			for (ArrayList<TypedExpression> curExprs: result.getExprs().values())
				exprs.addAll(curExprs);
			return exprs;
		}
		
		// TODO: Instead of simply setting the node's constraint to the new thing, should we also keep what we have from the old one?  E.g., in get{Fields,Methods}AndSetConstraint, the old constraint might be "thing with a field" and the new might be "thing of type Foo".  We want to combine those two.
		private ExpressionsAndTypeConstraints fillSkeleton(Expression node, TypeConstraint curConstraint, Set<ASTNode> parentsOfHoles) {
			if (node instanceof ArrayAccess) {
				return fillArrayAccess((ArrayAccess)node, curConstraint, parentsOfHoles);
			} else if (node instanceof BooleanLiteral) {
	    		return new ExpressionsAndTypeConstraints(new TypedExpression(node, booleanType, null), new SupertypeBound(booleanType));
			} else if (node instanceof CastExpression) {
				return fillCast((CastExpression)node, curConstraint, parentsOfHoles);
			} else if (node instanceof CharacterLiteral) {
				IJavaType type = EclipseUtils.getFullyQualifiedType("char", target);
	    		return new ExpressionsAndTypeConstraints(new TypedExpression(node, type, null), new SupertypeBound(type));
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
				ArrayList<TypeConstraint> argTypes = getArgTypes(node, call.arguments(), parentsOfHoles);
				ExpressionsAndTypeConstraints receiverResult = null;
				if (call.getExpression() != null) {
					TypeConstraint expressionConstraint = new MethodConstraint(parentsOfHoles.contains(call.getName()) ? null : call.getName().getIdentifier(), curConstraint, argTypes);
					receiverResult = fillSkeleton(call.getExpression(), expressionConstraint, parentsOfHoles);
				} else
					receiverResult = new ExpressionsAndTypeConstraints(new SupertypeBound(getThisType()));
				return fillMethod(call, call.getName(), call.arguments(), parentsOfHoles, curConstraint, argTypes, receiverResult);
			} else if (node instanceof NullLiteral) {
	    		return null;
			} else if (node instanceof NumberLiteral) {
	    		return fillNumberLiteral(node);
			} else if (node instanceof ParenthesizedExpression) {
				return fillParenthesized((ParenthesizedExpression)node, curConstraint, parentsOfHoles);
			} else if (node instanceof PostfixExpression) {
				return fillPostfix((PostfixExpression)node, curConstraint, parentsOfHoles);
			} else if (node instanceof PrefixExpression) {
				return fillPrefix((PrefixExpression)node, curConstraint, parentsOfHoles);
			} else if (node instanceof QualifiedName) {
				IJavaType type = EclipseUtils.getTypeAndLoadIfNeededAndExists(node.toString(), stack, target);
				if (type != null)
					return new ExpressionsAndTypeConstraints(new TypedExpression(node, type, null), new SupertypeBound(type));
				else {
					QualifiedName name = (QualifiedName)node;
					return fillNormalField(name.getQualifier(), name.getName(), parentsOfHoles, curConstraint);
				}
			} else if (node instanceof SimpleName) {
				return fillSimpleName(node, curConstraint);
			} else if (node instanceof StringLiteral) {
				IJavaType type = EclipseUtils.getFullyQualifiedType("java.lang.String", target);
				return new ExpressionsAndTypeConstraints(new TypedExpression(node, type, null), new SupertypeBound(type));
			} else if (node instanceof SuperFieldAccess) {
				SuperFieldAccess superAccess = (SuperFieldAccess)node;
				IJavaType superType = getSuperType(superAccess.getQualifier());
				return fillField(superAccess.getName(), superAccess.getQualifier(), parentsOfHoles, curConstraint, new ExpressionsAndTypeConstraints(new TypedExpression(null, superType, null), new SupertypeBound(superType)));
			} else if (node instanceof SuperMethodInvocation) {
				SuperMethodInvocation superAccess = (SuperMethodInvocation)node;
				IJavaType superType = getSuperType(superAccess.getQualifier());
				ArrayList<TypeConstraint> argTypes = getArgTypes(node, superAccess.arguments(), parentsOfHoles);
				return fillMethod(superAccess, superAccess.getName(), superAccess.arguments(), parentsOfHoles, curConstraint, argTypes, new ExpressionsAndTypeConstraints(new TypedExpression(null, superType, null), new SupertypeBound(superType)));
			} else if (node instanceof ThisExpression) {
				IJavaType type = getThisType();
				return new ExpressionsAndTypeConstraints(new TypedExpression(node, type, null), new SupertypeBound(type)); 
			} else if (node instanceof TypeLiteral) {
				return fillTypeLiteral(node);
			} else
				throw new RuntimeException("Unexpected expression type " + node.getClass().toString());
		}

		private ExpressionsAndTypeConstraints fillArrayAccess(ArrayAccess arrayAccess, TypeConstraint curConstraint, Set<ASTNode> parentsOfHoles) {
			try {
				TypeConstraint arrayConstraint = null;
				if (parentsOfHoles.contains(arrayAccess.getArray())) {
					IJavaType[] componentConstraints = curConstraint.getTypes(target);
					List<IJavaType> componentTypes = new ArrayList<IJavaType>(componentConstraints.length);
					for (IJavaType type: componentConstraints)
						componentTypes.add(EclipseUtils.getFullyQualifiedType(type.getName() + "[]", target));
					arrayConstraint = getSupertypeConstraintForTypes(componentTypes);
				}
				ExpressionsAndTypeConstraints arrayResult = fillSkeleton(arrayAccess.getArray(), arrayConstraint, parentsOfHoles);
				arrayConstraint = arrayResult.getTypeConstraint();
				ExpressionsAndTypeConstraints indexResult = fillSkeleton(arrayAccess.getIndex(), new SupertypeBound(intType), parentsOfHoles);
				IJavaType[] arrayConstraints = arrayConstraint.getTypes(target);
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

		private ExpressionsAndTypeConstraints fillCast(CastExpression cast, TypeConstraint curConstraint, Set<ASTNode> parentsOfHoles) {
			// TODO: Allow any type or ensure comparable with the given type with SameHierarchy?
			ExpressionsAndTypeConstraints exprResult = fillSkeleton(cast.getExpression(), curConstraint, parentsOfHoles);
			IJavaType castType = EclipseUtils.getType(cast.getType().toString(), stack, target);
			Map<String, ArrayList<TypedExpression>> resultExprs = new HashMap<String, ArrayList<TypedExpression>>(exprResult.getExprs().size());
			for (Map.Entry<String, ArrayList<TypedExpression>> res: exprResult.getExprs().entrySet())
				for (TypedExpression expr: res.getValue())
					Utils.addToMap(resultExprs, res.getKey(), ExpressionMaker.makeCast(expr, castType, null));
			return new ExpressionsAndTypeConstraints(resultExprs, new SupertypeBound(castType));
		}

		private ExpressionsAndTypeConstraints fillClassInstanceCreation(Expression node, Set<ASTNode> parentsOfHoles) {
			ClassInstanceCreation cons = (ClassInstanceCreation)node;
			assert cons.getExpression() == null;  // TODO: Handle this case.
			IJavaType consType = EclipseUtils.getType(cons.getType().toString(), stack, target);
			ArrayList<TypeConstraint> argTypes = getArgTypes(node, cons.arguments(), parentsOfHoles);
			Map<String, ArrayList<Method>> constructors = getConstructors(consType, argTypes);
			boolean isListHole = argTypes == null;
			ArrayList<ExpressionsAndTypeConstraints> argResults = getAndFillArgs(cons.arguments(), parentsOfHoles, constructors, isListHole);
			OverloadChecker overloadChecker = new OverloadChecker(consType);
			Map<String, ArrayList<TypedExpression>> resultExprs = new HashMap<String, ArrayList<TypedExpression>>();
			for (Method method: Utils.singleton(constructors.values()))
				buildCalls(method, new TypedExpression(null, consType, null), cons, argResults, isListHole, resultExprs, overloadChecker);
			return new ExpressionsAndTypeConstraints(resultExprs, new DesiredType(consType));
		}

		private ExpressionsAndTypeConstraints fillConditionalExpression(Expression node, TypeConstraint curConstraint, Set<ASTNode> parentsOfHoles) {
			ConditionalExpression cond = (ConditionalExpression)node;
			// TODO: Do a better job of constraining left/right to be similar?  E.g., if we have right but not left.
			ExpressionsAndTypeConstraints condResult = fillSkeleton(cond.getExpression(), new SupertypeBound(booleanType), parentsOfHoles);
			ExpressionsAndTypeConstraints thenResult = fillSkeleton(cond.getThenExpression(), curConstraint, parentsOfHoles);
			ExpressionsAndTypeConstraints elseResult = fillSkeleton(cond.getElseExpression(), curConstraint, parentsOfHoles);
			Map<String, ArrayList<TypedExpression>> resultExprs = new HashMap<String, ArrayList<TypedExpression>>(thenResult.getExprs().size());
			for (TypedExpression condExpr: Utils.singleton(condResult.getExprs().values()))
				for (Map.Entry<String, ArrayList<TypedExpression>> thenExprs: thenResult.getExprs().entrySet())
					if (elseResult.getExprs().containsKey(thenExprs.getKey()))
						for (TypedExpression thenExpr: thenExprs.getValue())
							for (TypedExpression elseExpr: elseResult.getExprs().get(thenExprs.getKey()))
								Utils.addToMap(resultExprs, thenExprs.getKey(), ExpressionMaker.makeConditional(condExpr, thenExpr, elseExpr, thenExpr.getType()));
			return new ExpressionsAndTypeConstraints(resultExprs, thenResult.getTypeConstraint());
		}

		private ExpressionsAndTypeConstraints fillInfixExpression(Expression node, TypeConstraint curConstraint, Set<ASTNode> parentsOfHoles) {
			try {
				InfixExpression infix = (InfixExpression)node;
				boolean isBooleanResult = infix.getOperator() == InfixExpression.Operator.CONDITIONAL_AND || infix.getOperator() == InfixExpression.Operator.CONDITIONAL_OR || infix.getOperator() == InfixExpression.Operator.EQUALS || infix.getOperator() == InfixExpression.Operator.NOT_EQUALS || infix.getOperator() == InfixExpression.Operator.LESS || infix.getOperator() == InfixExpression.Operator.LESS_EQUALS || infix.getOperator() == InfixExpression.Operator.GREATER || infix.getOperator() == InfixExpression.Operator.GREATER_EQUALS;
				if (isBooleanResult && !curConstraint.isFulfilledBy(booleanType, subtypeChecker, stack, target))
					throw new TypeError("Incorrectly-typed operator " + infix.getOperator());
				TypeConstraint leftConstraint = curConstraint;
				TypeConstraint rightConstraint = curConstraint;
				if (infix.getOperator() == InfixExpression.Operator.CONDITIONAL_AND || infix.getOperator() == InfixExpression.Operator.CONDITIONAL_OR) {
					leftConstraint = new SupertypeBound(booleanType);
					rightConstraint = new SupertypeBound(booleanType);
				} else if (infix.getOperator() == InfixExpression.Operator.MINUS || infix.getOperator() == InfixExpression.Operator.TIMES || infix.getOperator() == InfixExpression.Operator.DIVIDE || infix.getOperator() == InfixExpression.Operator.LESS || infix.getOperator() == InfixExpression.Operator.LESS_EQUALS || infix.getOperator() == InfixExpression.Operator.GREATER || infix.getOperator() == InfixExpression.Operator.GREATER_EQUALS) {
					leftConstraint = makeSupertypeSet(intType);  // TODO: These should be all numeric types.
					rightConstraint = makeSupertypeSet(intType);
					if (!isBooleanResult && !curConstraint.isFulfilledBy(intType, subtypeChecker, stack, target))
						throw new TypeError("Incorrectly-typed operator " + infix.getOperator());
				}
				ExpressionsAndTypeConstraints leftResult = fillSkeleton(infix.getLeftOperand(), leftConstraint, parentsOfHoles);
				ExpressionsAndTypeConstraints rightResult = fillSkeleton(infix.getRightOperand(), rightConstraint, parentsOfHoles);
				Map<String, ArrayList<TypedExpression>> resultExprs = new HashMap<String, ArrayList<TypedExpression>>(leftResult.getExprs().size());
				for (Map.Entry<String, ArrayList<TypedExpression>> leftExprs: leftResult.getExprs().entrySet())
					if (rightResult.getExprs().containsKey(leftExprs.getKey()))
						for (TypedExpression leftExpr: leftExprs.getValue())
							for (TypedExpression rightExpr: rightResult.getExprs().get(leftExprs.getKey()))
								if ((leftExpr.getValue() == null || !leftExpr.getValue().isNull() || rightExpr.getValue() == null || !rightExpr.getValue().isNull())  // TODO: These two checks should be part of my constraint when I search for the child holes above.
									&& !(EclipseUtils.isObject(leftExpr.getType()) && EclipseUtils.isObject(rightExpr.getType()) && !"java.lang.String".equals(leftExpr.getType().getName()) && !"java.lang.String".equals(rightExpr.getType().getName()))) {
									IJavaType resultType = isBooleanResult ? booleanType : leftExpr.getValue() == null || !leftExpr.getValue().isNull() ? leftExpr.getType() : rightExpr.getType();
									Utils.addToMap(resultExprs, leftExprs.getKey(), ExpressionMaker.makeInfix(target, leftExpr, infix.getOperator(), rightExpr, resultType));
								}
				TypeConstraint resultConstraint = isBooleanResult ? new SupertypeBound(booleanType) : leftConstraint;
				return new ExpressionsAndTypeConstraints(resultExprs, resultConstraint);
			} catch (DebugException ex) {
				throw new RuntimeException(ex);
			}
		}

		private ExpressionsAndTypeConstraints fillInstanceof(InstanceofExpression instance, Set<ASTNode> parentsOfHoles) {
			IJavaType castType = EclipseUtils.getType(instance.getRightOperand().toString(), stack, target);
			ExpressionsAndTypeConstraints exprResult = fillSkeleton(instance.getLeftOperand(), new SameHierarchy(castType), parentsOfHoles);
			Map<String, ArrayList<TypedExpression>> resultExprs = new HashMap<String, ArrayList<TypedExpression>>(exprResult.getExprs().size());
			for (Map.Entry<String, ArrayList<TypedExpression>> res: exprResult.getExprs().entrySet())
				for (TypedExpression expr: res.getValue())
					Utils.addToMap(resultExprs, res.getKey(), ExpressionMaker.makeInstanceOf(expr, (Type)ExpressionMaker.resetAST(instance.getRightOperand()), booleanType, null));
			return new ExpressionsAndTypeConstraints(resultExprs, new SupertypeBound(booleanType));
		}

		private ExpressionsAndTypeConstraints fillNumberLiteral(Expression node) {
			String str = ((NumberLiteral)node).getToken();
			int lastChar = str.charAt(str.length() - 1);
			// Rules taken from: http://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html.
			IJavaType resultType = null;
			if (lastChar == 'l' || lastChar == 'L')
				resultType = EclipseUtils.getFullyQualifiedType("long", target);
			else if (lastChar == 'f' || lastChar == 'f')
				resultType = EclipseUtils.getFullyQualifiedType("float", target);
			else if (lastChar == 'd' || lastChar == 'd' || str.indexOf('.') != -1)
				resultType = EclipseUtils.getFullyQualifiedType("double", target);
			else
				resultType = intType;
			return new ExpressionsAndTypeConstraints(new TypedExpression(node, resultType, null), new SupertypeBound(resultType));
		}

		private ExpressionsAndTypeConstraints fillParenthesized(ParenthesizedExpression paren, TypeConstraint curConstraint, Set<ASTNode> parentsOfHoles) {
			ExpressionsAndTypeConstraints exprResult = fillSkeleton(paren.getExpression(), curConstraint, parentsOfHoles);
			Map<String, ArrayList<TypedExpression>> resultExprs = new HashMap<String, ArrayList<TypedExpression>>(exprResult.getExprs().size());
			for (Map.Entry<String, ArrayList<TypedExpression>> res: exprResult.getExprs().entrySet())
				for (TypedExpression expr: res.getValue())
					Utils.addToMap(resultExprs, res.getKey(), ExpressionMaker.makeParenthesized(expr));
			return new ExpressionsAndTypeConstraints(resultExprs, curConstraint);
		}
		
		// TODO: Support increment/decrement.  I need to ensure that the expr is a non-final variable.

		private ExpressionsAndTypeConstraints fillPostfix(PostfixExpression postfix, TypeConstraint curConstraint, Set<ASTNode> parentsOfHoles) {
			ExpressionsAndTypeConstraints exprResult = fillSkeleton(postfix.getOperand(), curConstraint, parentsOfHoles);
			Map<String, ArrayList<TypedExpression>> resultExprs = new HashMap<String, ArrayList<TypedExpression>>(exprResult.getExprs().size());
			for (Map.Entry<String, ArrayList<TypedExpression>> res: exprResult.getExprs().entrySet())
				for (TypedExpression expr: res.getValue())
					Utils.addToMap(resultExprs, res.getKey(), ExpressionMaker.makePostfix(target, expr, postfix.getOperator(), expr.getType()));
			return new ExpressionsAndTypeConstraints(resultExprs, curConstraint);
		}

		private ExpressionsAndTypeConstraints fillPrefix(PrefixExpression prefix, TypeConstraint curConstraint, Set<ASTNode> parentsOfHoles) {
			ExpressionsAndTypeConstraints exprResult = fillSkeleton(prefix.getOperand(), curConstraint, parentsOfHoles);
			Map<String, ArrayList<TypedExpression>> resultExprs = new HashMap<String, ArrayList<TypedExpression>>(exprResult.getExprs().size());
			for (Map.Entry<String, ArrayList<TypedExpression>> res: exprResult.getExprs().entrySet())
				for (TypedExpression expr: res.getValue())
					Utils.addToMap(resultExprs, res.getKey(), ExpressionMaker.makePrefix(target, expr, prefix.getOperator(), expr.getType()));
			return new ExpressionsAndTypeConstraints(resultExprs, curConstraint);
		}

		private ExpressionsAndTypeConstraints fillSimpleName(Expression node, TypeConstraint curConstraint) {
			try {
				SimpleName name = (SimpleName)node;
				if (holeInfos.containsKey(name.getIdentifier())) {
					HoleInfo holeInfo = holeInfos.get(name.getIdentifier());
					SubMonitor childMonitor = SubMonitor.convert(monitor, "Hole generation and evaluation", 1);
					assert !holeInfo.isNegative();  // TODO: Use negative arg information.
					// TODO: Optimization: If this is the only hole, evaluate the skeleton directly?
					// TODO: Improve heuristics to reduce search space when too many holes.
					if (curConstraint instanceof FieldNameConstraint) {
						FieldNameConstraint fieldNameConstraint = (FieldNameConstraint)curConstraint;
						if (holeInfo.getArgs() != null)
							fieldNameConstraint.setLegalNames(new HashSet<String>(holeInfo.getArgs()));
						return new ExpressionsAndTypeConstraints(getFieldsAndSetConstraint(name, fieldNameConstraint, true));
					} else if (curConstraint instanceof MethodNameConstraint) {
						MethodNameConstraint methodNameConstraint = (MethodNameConstraint)curConstraint;
						if (holeInfo.getArgs() != null)
							methodNameConstraint.setLegalNames(new HashSet<String>(holeInfo.getArgs()));
						return new ExpressionsAndTypeConstraints(getMethodsAndSetConstraint(name, methodNameConstraint, true));
					} else {
						ArrayList<EvaluatedExpression> values;
						if (holeInfo.getArgs() != null) {
							ArrayList<String> nonCrashingStrings = filterCrashingExpression(holeInfo.getArgs(), childMonitor);
							ArrayList<TypedExpression> fakeTypedHoleInfos = new ArrayList<TypedExpression>(nonCrashingStrings.size());
							for (String s: nonCrashingStrings) {
								Expression e = (Expression)ExpressionMaker.resetAST(EclipseUtils.parseExpr(parser, s));
								IJavaType type = curConstraint instanceof DesiredType ? ((DesiredType)curConstraint).getDesiredType() : ((SingleTypeConstraint)fillSkeleton(e, curConstraint, null).getTypeConstraint()).getTypeConstraint();
								fakeTypedHoleInfos.add(new TypedExpression(e, type, null));
							}
							values = evalManager.evaluateExpressions(fakeTypedHoleInfos, null, null, childMonitor);
						} else
							values = expressionGenerator.generateExpression(null, curConstraint, null, childMonitor, holeInfos.size() == 1 ? 1 : 0);
						Map<String, ArrayList<EvaluatedExpression>> valuesByType = new HashMap<String, ArrayList<EvaluatedExpression>>();
						List<IJavaType> resultTypes = new ArrayList<IJavaType>(values.size());
						for (EvaluatedExpression e: values) {
							resultTypes.add(e.getType());
							String typeName = e.getType() == null ? "null" : e.getType().getName();
							Utils.addToMap(valuesByType, typeName, e);
						}
						TypeConstraint resultConstraint = getSupertypeConstraintForTypes(resultTypes);
						IJavaType[] constraintTypes = curConstraint.getTypes(target);
						Map<String, ArrayList<TypedExpression>> typedValuesForType = new HashMap<String, ArrayList<TypedExpression>>(constraintTypes.length);
						Map<String, ArrayList<EvaluatedExpression>> evaluatedValuesForType = new HashMap<String, ArrayList<EvaluatedExpression>>(constraintTypes.length);
						Set<EvaluatedExpression> added = new HashSet<EvaluatedExpression>();
						for (IJavaType constraintType: constraintTypes) {
							for (Entry<String, ArrayList<EvaluatedExpression>> exprs: valuesByType.entrySet()) {
								String typeName = exprs.getKey();
								if (subtypeChecker.isSubtypeOf(exprs.getValue().get(0).getType(), constraintType)) {
									for (EvaluatedExpression e: exprs.getValue()) {
										if (!added.contains(e)) {
											Utils.addToMap(typedValuesForType, typeName, new TypedExpression(e.getExpression(), e.getType(), e.getResult()));
											Utils.addToMap(evaluatedValuesForType, typeName, e);
											added.add(e);
										}
									}
								}
							}
						}
						holeValues.put(name.getIdentifier(), evaluatedValuesForType);
						return new ExpressionsAndTypeConstraints(typedValuesForType, resultConstraint);
					}
				} else if (curConstraint instanceof FieldNameConstraint) {
					return new ExpressionsAndTypeConstraints(getFieldsAndSetConstraint(name, (FieldNameConstraint)curConstraint, false));
				} else if (curConstraint instanceof MethodNameConstraint) {
					return new ExpressionsAndTypeConstraints(getMethodsAndSetConstraint(name, (MethodNameConstraint)curConstraint, false));
				} else {
					IJavaVariable var = stack.findVariable(name.getIdentifier());
					if (var != null)
						return new ExpressionsAndTypeConstraints(new TypedExpression(name, var.getJavaType(), null), new SupertypeBound(var.getJavaType()));
					IJavaType type = EclipseUtils.getTypeAndLoadIfNeeded(name.getIdentifier(), stack, target);
					assert type != null : name.getIdentifier();
					return new ExpressionsAndTypeConstraints(ExpressionMaker.makeStaticName(name.getIdentifier(), type), new SupertypeBound(type));
				}
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
		}

		private ExpressionsAndTypeConstraints fillTypeLiteral(Expression node) {
			try {
				IJavaType type = EclipseUtils.getType(((TypeLiteral)node).getType().toString(), stack, target);
				IJavaClassObject classObj = ((IJavaReferenceType)type).getClassObject();
				IJavaType classObjType = classObj.getJavaType();
				return new ExpressionsAndTypeConstraints(new TypedExpression(node, classObjType, classObj), new SupertypeBound(classObjType));
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
		}
		
		/* Helper functions. */

		private TypeConstraint getFieldsAndSetConstraint(SimpleName name, FieldNameConstraint fieldNameConstraint, boolean isHole) {
			Map<String, ArrayList<Field>> fieldsByType = fieldNameConstraint.getFields(stack, target, subtypeChecker);
			List<IJavaType> types = new ArrayList<IJavaType>();
			for (ArrayList<Field> fields: fieldsByType.values())
				for (Field field: fields)
					types.add(EclipseUtils.getFullyQualifiedType(field.typeName(), target));
			if (isHole)
				holeFields.put(name.getIdentifier(), fieldsByType);
			return getSupertypeConstraintForTypes(types);
		}

		private TypeConstraint getMethodsAndSetConstraint(SimpleName name, MethodNameConstraint methodNameConstraint, boolean isHole) {
			Map<String, ArrayList<Method>> methodsByType = methodNameConstraint.getMethods(stack, target, subtypeChecker);
			List<IJavaType> types = new ArrayList<IJavaType>();
			for (ArrayList<Method> methods: methodsByType.values())
				for (Method method: methods)
					types.add(EclipseUtils.getFullyQualifiedTypeIfExists(method.returnTypeName(), target));
			if (isHole)
				holeMethods.put(name.getIdentifier(), methodsByType);
			return getSupertypeConstraintForTypes(types);
		}

		private ExpressionsAndTypeConstraints fillNormalField(Expression qualifier, SimpleName name, Set<ASTNode> parentsOfHoles, TypeConstraint curConstraint) {
			TypeConstraint qualifierConstraint = new FieldConstraint(parentsOfHoles.contains(name) ? null : name.getIdentifier(), curConstraint);
			ExpressionsAndTypeConstraints receiverResult = fillSkeleton(qualifier, qualifierConstraint, parentsOfHoles);
			return fillField(name, null, parentsOfHoles, curConstraint, receiverResult);
		}

		private ExpressionsAndTypeConstraints fillField(SimpleName name, Name superQualifier, Set<ASTNode> parentsOfHoles, TypeConstraint curConstraint, ExpressionsAndTypeConstraints receiverResult) {
			FieldNameConstraint fieldNameConstraint = new FieldNameConstraint(receiverResult.getTypeConstraint(), curConstraint);
			if (!parentsOfHoles.contains(name))
				fieldNameConstraint.setLegalNames(new HashSet<String>(Arrays.asList(new String[] { name.toString() })));
			ExpressionsAndTypeConstraints fieldResult = fillSkeleton(name, fieldNameConstraint, parentsOfHoles);
			Map<String, ArrayList<Field>> fields = null;
			if (holeFields.containsKey(name.getIdentifier()))
				fields = holeFields.get(name.getIdentifier());
			else
				fields = fieldNameConstraint.getFields(stack, target, subtypeChecker);
			Map<String, ArrayList<TypedExpression>> resultExprs = new HashMap<String, ArrayList<TypedExpression>>(fieldResult.getTypeConstraint().getTypes(target).length);
			for (Map.Entry<String, ArrayList<TypedExpression>> receiverExprs: receiverResult.getExprs().entrySet())
				if (fields.containsKey(receiverExprs.getKey())) {
					for (TypedExpression receiverExpr: receiverExprs.getValue())
						for (Field field: fields.get(receiverExprs.getKey())) {
							if (!ExpressionMaker.isStatic(receiverExpr.getExpression()) || field.isStatic()) {
								String fieldTypeName = field.typeName();
								TypedExpression newExpr = null;
								if (receiverExpr.getExpression() == null)
									newExpr = ExpressionMaker.makeSuperFieldAccess(superQualifier, field.name(), EclipseUtils.getTypeAndLoadIfNeeded(fieldTypeName, stack, target), null);
								else
									newExpr = ExpressionMaker.makeFieldAccess(receiverExpr, field.name(), EclipseUtils.getTypeAndLoadIfNeeded(fieldTypeName, stack, target), null);
								Utils.addToMap(resultExprs, fieldTypeName, newExpr);
							}
						}
				}
			return new ExpressionsAndTypeConstraints(resultExprs, fieldResult.getTypeConstraint());
		}

		private ExpressionsAndTypeConstraints fillMethod(Expression node, SimpleName name, List<?> arguments, Set<ASTNode> parentsOfHoles, TypeConstraint curConstraint, ArrayList<TypeConstraint> argTypes, ExpressionsAndTypeConstraints receiverResult) {
			MethodNameConstraint methodNameConstraint = new MethodNameConstraint(receiverResult.getTypeConstraint(), curConstraint, argTypes);
			if (!parentsOfHoles.contains(name))
				methodNameConstraint.setLegalNames(new HashSet<String>(Arrays.asList(new String[] { name.toString() })));
			ExpressionsAndTypeConstraints methodResult = fillSkeleton(name, methodNameConstraint, parentsOfHoles);
			Map<String, ArrayList<Method>> methods = null;
			if (holeMethods.containsKey(name.getIdentifier()))
				methods = holeMethods.get(name.getIdentifier());
			else
				methods = methodNameConstraint.getMethods(stack, target, subtypeChecker);
			boolean isListHole = argTypes == null;
			ArrayList<ExpressionsAndTypeConstraints> argResults = getAndFillArgs(arguments, parentsOfHoles, methods, isListHole);
			if (receiverResult.getExprs() != null) {
				Map<String, ArrayList<TypedExpression>> resultExprs = new HashMap<String, ArrayList<TypedExpression>>(methodResult.getTypeConstraint().getTypes(target).length);
				for (Map.Entry<String, ArrayList<TypedExpression>> receiverExprs: receiverResult.getExprs().entrySet())
					if (methods.containsKey(receiverExprs.getKey()))
						for (TypedExpression receiverExpr: receiverExprs.getValue()) {
							OverloadChecker overloadChecker = new OverloadChecker(receiverExpr.getType());
							for (Method method: methods.get(receiverExprs.getKey()))
								if (!ExpressionMaker.isStatic(receiverExpr.getExpression()) || method.isStatic())
									buildCalls(method, receiverExpr, node, argResults, isListHole, resultExprs, overloadChecker);
						}
				return new ExpressionsAndTypeConstraints(resultExprs, methodResult.getTypeConstraint());
			} else {
				Map<String, ArrayList<TypedExpression>> resultExprs = new HashMap<String, ArrayList<TypedExpression>>(methodResult.getTypeConstraint().getTypes(target).length);
				IJavaType thisType = getThisType();
				OverloadChecker overloadChecker = new OverloadChecker(thisType);
				if (!methods.isEmpty())
					for (Method method: Utils.singleton(methods.values()))
						buildCalls(method, ExpressionMaker.makeThis(null, thisType), node, argResults, isListHole, resultExprs, overloadChecker);
				return new ExpressionsAndTypeConstraints(resultExprs, methodResult.getTypeConstraint());
			}
		}
		
		private ArrayList<TypeConstraint> getArgTypes(Expression node, List<?> argNodes, Set<ASTNode> parentsOfHoles) {
			if (argNodes.size() == 1) {  // Special case to check for a single list hole.
				Expression arg = (Expression)argNodes.get(0);
				if (arg instanceof SimpleName) {
					SimpleName argName = (SimpleName)arg;
					if (holeInfos.containsKey(argName.getIdentifier()) && holeInfos.get(argName.getIdentifier()) instanceof ListHoleInfo)
						return null;
				}
			}
			if (parentsOfHoles.contains(node)) {
				ArrayList<TypeConstraint> argTypes = new ArrayList<TypeConstraint>(argNodes.size());
				for (Object argObj: argNodes) {
					Expression arg = (Expression)argObj;
					argTypes.add(parentsOfHoles.contains(arg) ? null : fillSkeleton(arg, null, parentsOfHoles).getTypeConstraint());
				}
				return argTypes;
			} else
				return null;
		}

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
								argConstraints.add(EclipseUtils.getTypeAndLoadIfNeeded((String)method.argumentTypeNames().get(j), stack, target));
						else
							argConstraints.add(EclipseUtils.getTypeAndLoadIfNeeded((String)method.argumentTypeNames().get(i), stack, target));
					}
				TypeConstraint argConstraint = getSupertypeConstraintForTypes(argConstraints);
				argResults.add(fillSkeleton(arg, argConstraint, parentsOfHoles));
			}
			return argResults;
		}

		private void buildCalls(Method method, TypedExpression receiverExpr, Expression callNode, ArrayList<ExpressionsAndTypeConstraints> argResults, boolean isListHole, Map<String, ArrayList<TypedExpression>> resultExprs, OverloadChecker overloadChecker) {
			try {
				String methodReturnTypeName = method.isConstructor() ? receiverExpr.getType().getName() : method.returnTypeName();  // The method class returns void for the return type of constructors....
				overloadChecker.setMethod(method);
				ArrayList<ArrayList<TypedExpression>> allPossibleActuals = new ArrayList<ArrayList<TypedExpression>>(method.argumentTypeNames().size());
				for (int i = 0; i < method.argumentTypeNames().size(); i++) {
					IJavaType argType = EclipseUtils.getFullyQualifiedType((String)method.argumentTypeNames().get(i), target);
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
				makeAllCalls(method, method.name(), methodReturnTypeName, receiverExpr, callNode, EclipseUtils.getTypeAndLoadIfNeeded(methodReturnTypeName, stack, target), getThisType(), allPossibleActuals, new ArrayList<TypedExpression>(allPossibleActuals.size()), resultExprs);
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
		}
		
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
		
		private static SupertypeSet makeSupertypeSet(IJavaType... types) {
			Set<IJavaType> supertypeSet = new HashSet<IJavaType>(types.length);
			for (IJavaType type: types)
				supertypeSet.add(type);
			return new SupertypeSet(supertypeSet);
		}

		private IJavaType getThisType() {
			try {
				return stack.getReferenceType();
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
		}

		private IJavaType getSuperType(Name name) {
    		try {
    			IJavaType curType = name == null ? stack.getReferenceType() : EclipseUtils.getType(name.toString(), stack, target);
				return ((IJavaClassType)curType).getSuperclass();
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
		}
    	
    	private Map<String, ArrayList<Method>> getConstructors(IJavaType type, ArrayList<TypeConstraint> argConstraints) {
			try {
				Map<String, ArrayList<Method>> methodsByType = new HashMap<String, ArrayList<Method>>(1);
				String typeName = type.getName();
	    		for (Method method: ExpressionGenerator.getMethods(type))
					if (ExpressionGenerator.isLegalMethod(method, stack.getReferenceType(), true) && MethodNameConstraint.fulfillsArgConstraints(method, argConstraints, subtypeChecker, stack, target))
						Utils.addToMap(methodsByType, typeName, method);
	    		return methodsByType;
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
    	}
    	
    	private ArrayList<String> filterCrashingExpression(ArrayList<String> expressionStrings, IProgressMonitor monitor) {
			SubMonitor evalMonitor = SubMonitor.convert(monitor, "Expression typecheck", expressionStrings.size());
			ArrayList<String> result = new ArrayList<String>();
			IAstEvaluationEngine engine = EclipseUtils.getASTEvaluationEngine(stack);
			for (String s: expressionStrings) {
    			if (monitor.isCanceled())
    				throw new OperationCanceledException();
				if (EclipseUtils.getCompileErrors(s, null, stack, engine) == null)
					result.add(s);
				evalMonitor.worked(1);
			}
			return result;
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
				Utils.addToMap(this.exprs, expr.getType().getName(), expr);
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
			this.typeConstraint = typeConstraint;
		}
		
		public ExpressionsAndTypeConstraints(TypeConstraint typeConstraint) {
			this.exprs = null;
			this.typeConstraint = typeConstraint;
		}

		public Map<String, ArrayList<TypedExpression>> getExprs() {
			return exprs;
		}

		public TypeConstraint getTypeConstraint() {
			return typeConstraint;
		}
		
	}

}
