package codehint.exprgen;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
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
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeLiteral;

public final class ASTCopyer extends ASTVisitor {
	
	/**
	 * Makes a copy of the given Expression.
	 * Note that we do not use ASTNode.copySubtree
	 * because it does not copy properties.
	 * @param node The node to copy.
	 * @return A copy of the given node.
	 */
	public static Expression copy(Expression node) {
		if (node == null)
			return null;
		else if (node instanceof ArrayAccess) {
			return copy((ArrayAccess)node);
		} else if (node instanceof BooleanLiteral) {
			return copy((BooleanLiteral)node);
		} else if (node instanceof CastExpression) {
			return copy((CastExpression)node);
		} else if (node instanceof CharacterLiteral) {
			return copy((CharacterLiteral)node);
		} else if (node instanceof ClassInstanceCreation) {
			return copy((ClassInstanceCreation)node);
		} else if (node instanceof ConditionalExpression) {
			return copy((ConditionalExpression)node);
		} else if (node instanceof FieldAccess) {
			return copy((FieldAccess)node);
		} else if (node instanceof InfixExpression) {
			return copy((InfixExpression)node);
		} else if (node instanceof InstanceofExpression) {
			return copy((InstanceofExpression)node);
		} else if (node instanceof MethodInvocation) {
			return copy((MethodInvocation)node);
		} else if (node instanceof NullLiteral) {
			return copy((NullLiteral)node);
		} else if (node instanceof NumberLiteral) {
			return copy((NumberLiteral)node);
		} else if (node instanceof ParenthesizedExpression) {
			return copy((ParenthesizedExpression)node);
		} else if (node instanceof PostfixExpression) {
			return copy((PostfixExpression)node);
		} else if (node instanceof PrefixExpression) {
			return copy((PrefixExpression)node);
		} else if (node instanceof QualifiedName) {
			return copy((QualifiedName)node);
		} else if (node instanceof SimpleName) {
			return copy((SimpleName)node);
		} else if (node instanceof StringLiteral) {
			return copy((StringLiteral)node);
		} else if (node instanceof SuperFieldAccess) {
			return copy((SuperFieldAccess)node);
		} else if (node instanceof SuperMethodInvocation) {
			return copy((SuperMethodInvocation)node);
		} else if (node instanceof ThisExpression) {
			return copy((ThisExpression)node);
		} else if (node instanceof TypeLiteral) {
			return copy((TypeLiteral)node);
		} else if (node instanceof Assignment) {
			return copy((Assignment)node);
		} else
			throw new RuntimeException("Unexpected expression type " + node.getClass().toString());
	}
	
	private static Name copy(Name name) {
		if (name == null)
			return null;
		if (name instanceof SimpleName)
			return copy((SimpleName)name);
		else if (name instanceof QualifiedName)
			return copy((QualifiedName)name);
		else
			throw new RuntimeException("Unexpected expression type " + name.getClass().toString());
	}

	/**
	 * Makes a copy of the given Type.
	 * Note that we do not use ASTNode.copySubtree
	 * because it does not copy properties.
	 * @param node The node to copy.
	 * @return A copy of the given node.
	 */
	public static Type copy(Type node) {
		if (node == null)
			return null;
		else if (node instanceof SimpleType)
			return copyProperties(node, node.getAST().newSimpleType(copy(((SimpleType)node).getName())));
		else if (node instanceof QualifiedType) {
			QualifiedType type = (QualifiedType)node;
			return copyProperties(node, node.getAST().newQualifiedType(copy(type.getQualifier()), copy(type.getName())));
		} else if (node instanceof ArrayType) {
			return copyProperties(node, node.getAST().newArrayType(copy(((ArrayType)node).getComponentType())));
		} else if (node instanceof PrimitiveType) {
			return copyProperties(node, node.getAST().newPrimitiveType(((PrimitiveType)node).getPrimitiveTypeCode()));
		} else
			throw new RuntimeException("Unexpected expression type " + node.getClass().toString());
	}
	
	private static ArrayAccess copy(ArrayAccess node) {
		ArrayAccess c = node.getAST().newArrayAccess();
		c.setArray(copy(node.getArray()));
		c.setIndex(copy(node.getIndex()));
		return copyProperties(node, c);
	}

	private static BooleanLiteral copy(BooleanLiteral node) {
		return copyProperties(node, node.getAST().newBooleanLiteral(node.booleanValue()));
	}

	private static CastExpression copy(CastExpression node) {
		CastExpression c = node.getAST().newCastExpression();
		c.setType(copy(node.getType()));
		c.setExpression(copy(node.getExpression()));
		return copyProperties(node, c);
	}

	private static CharacterLiteral copy(CharacterLiteral node) {
		CharacterLiteral c = node.getAST().newCharacterLiteral();
		c.setCharValue(node.charValue());
		return copyProperties(node, c);
	}

	private static ClassInstanceCreation copy(ClassInstanceCreation node) {
		ClassInstanceCreation c = node.getAST().newClassInstanceCreation();
		c.setType(copy(node.getType()));
		copyList(node.arguments(), c.arguments());
		return copyProperties(node, c);
	}

	private static ConditionalExpression copy(ConditionalExpression node) {
		ConditionalExpression c = node.getAST().newConditionalExpression();
		c.setExpression(copy(node.getExpression()));
		c.setThenExpression(copy(node.getThenExpression()));
		c.setElseExpression(copy(node.getElseExpression()));
		return copyProperties(node, c);
	}

	private static FieldAccess copy(FieldAccess node) {
		FieldAccess c = node.getAST().newFieldAccess();
		c.setExpression(copy(node.getExpression()));
		c.setName(copy(node.getName()));
		return copyProperties(node, c);
	}

	private static InfixExpression copy(InfixExpression node) {
		InfixExpression c = node.getAST().newInfixExpression();
		c.setLeftOperand(copy(node.getLeftOperand()));
		c.setOperator(node.getOperator());
		c.setRightOperand(copy(node.getRightOperand()));
		return copyProperties(node, c);
	}

	private static InstanceofExpression copy(InstanceofExpression node) {
		InstanceofExpression c = node.getAST().newInstanceofExpression();
		c.setLeftOperand(copy(node.getLeftOperand()));
		c.setRightOperand(copy(node.getRightOperand()));
		return copyProperties(node, c);
	}

	private static MethodInvocation copy(MethodInvocation node) {
		MethodInvocation c = node.getAST().newMethodInvocation();
		c.setExpression(copy(node.getExpression()));
		c.setName(copy(node.getName()));
		copyList(node.arguments(), c.arguments());
		return copyProperties(node, c);
	}

	private static NullLiteral copy(NullLiteral node) {
		return copyProperties(node, node.getAST().newNullLiteral());
	}

	//private static java.lang.reflect.Method nlSetter;
	
	private static NumberLiteral copy(NumberLiteral node) {
		if (node.getToken().equals("0"))  // Short circuit to avoid an unnecessary slow Scanner in the creation/copy of a NumberLiteral.
			return copyProperties(node, node.getAST().newNumberLiteral());
		return copyProperties(node, node.getAST().newNumberLiteral(node.getToken()));
		/*try {
			NumberLiteral copy = node.getAST().newNumberLiteral();
			if (nlSetter == null) {
				nlSetter = NumberLiteral.class.getDeclaredMethod("internalSetToken", String.class);
				nlSetter.setAccessible(true);
			}
			nlSetter.invoke(copy, node.getToken());
			return copyProperties(node, copy);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}*/
	}

	private static ParenthesizedExpression copy(ParenthesizedExpression node) {
		ParenthesizedExpression c = node.getAST().newParenthesizedExpression();
		c.setExpression(copy(node.getExpression()));
		return copyProperties(node, c);
	}

	private static PostfixExpression copy(PostfixExpression node) {
		PostfixExpression c = node.getAST().newPostfixExpression();
		c.setOperand(copy(node.getOperand()));
		c.setOperator(node.getOperator());
		return copyProperties(node, c);
	}

	private static PrefixExpression copy(PrefixExpression node) {
		PrefixExpression c = node.getAST().newPrefixExpression();
		c.setOperand(copy(node.getOperand()));
		c.setOperator(node.getOperator());
		return copyProperties(node, c);
	}

	private static QualifiedName copy(QualifiedName node) {
		return copyProperties(node, node.getAST().newQualifiedName(copy(node.getQualifier()), copy(node.getName())));
	}

	private static SimpleName copy(SimpleName node) {
		return copyProperties(node, node.getAST().newSimpleName(node.getIdentifier()));
	}

	private static StringLiteral copy(StringLiteral node) {
		StringLiteral c = node.getAST().newStringLiteral();
		c.setEscapedValue(node.getEscapedValue());
		return copyProperties(node, c);
	}

	private static SuperFieldAccess copy(SuperFieldAccess node) {
		SuperFieldAccess c = node.getAST().newSuperFieldAccess();
		c.setQualifier(copy(node.getQualifier()));
		c.setName(copy(node.getName()));
		return copyProperties(node, c);
	}

	private static SuperMethodInvocation copy(SuperMethodInvocation node) {
		SuperMethodInvocation c = node.getAST().newSuperMethodInvocation();
		c.setQualifier(copy(node.getQualifier()));
		c.setName(copy(node.getName()));
		copyList(node.arguments(), c.arguments());
		return copyProperties(node, c);
	}

	private static ThisExpression copy(ThisExpression node) {
		ThisExpression c = node.getAST().newThisExpression();
		c.setQualifier(copy(c.getQualifier()));
		return copyProperties(node, c);
	}

	private static TypeLiteral copy(TypeLiteral node) {
		TypeLiteral c = node.getAST().newTypeLiteral();
		c.setType(copy(node.getType()));
		return copyProperties(node, c);
	}

	private static Assignment copy(Assignment node) {
		Assignment c = node.getAST().newAssignment();
		c.setLeftHandSide(copy(node.getLeftHandSide()));
		c.setOperator(node.getOperator());
		c.setRightHandSide(copy(node.getRightHandSide()));
		return copyProperties(node, c);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static void copyList(List<?> orig, List copy) {
		for (Object arg: orig)
			copy.add(copy((Expression)arg));
	}
	
	private static <T extends ASTNode> T copyProperties(T orig, T copy) {
		Iterator<?> it = orig.properties().entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<?,?> property = (Map.Entry<?,?>)it.next();
			copy.setProperty((String)property.getKey(), property.getValue());
		}
		return copy;
	}

}
