package codehint.property;

import java.util.Iterator;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
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

/**
 * Adapted from org.eclipse.jdt.internal.core.dom.NaiveASTFlattener.
 */
public abstract class ASTFlattener extends ASTVisitor {
	
	protected StringBuilder sb;
	
	protected ASTFlattener() {
		sb = new StringBuilder();
	}
	
	public String getResult(ASTNode node) {
		return flatten(node).toString();
	}
	
	protected StringBuilder flatten(ASTNode node) {
		if (node instanceof ArrayAccess) {
			return flatten((ArrayAccess)node);
		} else if (node instanceof BooleanLiteral) {
			return flatten((BooleanLiteral)node);
		} else if (node instanceof CastExpression) {
			return flatten((CastExpression)node);
		} else if (node instanceof CharacterLiteral) {
			return flatten((CharacterLiteral)node);
		} else if (node instanceof ClassInstanceCreation) {
			return flatten((ClassInstanceCreation)node);
		} else if (node instanceof ConditionalExpression) {
			return flatten((ConditionalExpression)node);
		} else if (node instanceof FieldAccess) {
			return flatten((FieldAccess)node);
		} else if (node instanceof InfixExpression) {
			return flatten((InfixExpression)node);
		} else if (node instanceof InstanceofExpression) {
			return flatten((InstanceofExpression)node);
		} else if (node instanceof MethodInvocation) {
			return flatten((MethodInvocation)node);
		} else if (node instanceof NullLiteral) {
			return flatten((NullLiteral)node);
		} else if (node instanceof NumberLiteral) {
			return flatten((NumberLiteral)node);
		} else if (node instanceof ParenthesizedExpression) {
			return flatten((ParenthesizedExpression)node);
		} else if (node instanceof PostfixExpression) {
			return flatten((PostfixExpression)node);
		} else if (node instanceof PrefixExpression) {
			return flatten((PrefixExpression)node);
		} else if (node instanceof QualifiedName) {
			return flatten((QualifiedName)node);
		} else if (node instanceof SimpleName) {
			return flatten((SimpleName)node);
		} else if (node instanceof StringLiteral) {
			return flatten((StringLiteral)node);
		} else if (node instanceof SuperFieldAccess) {
			return flatten((SuperFieldAccess)node);
		} else if (node instanceof SuperMethodInvocation) {
			return flatten((SuperMethodInvocation)node);
		} else if (node instanceof ThisExpression) {
			return flatten((ThisExpression)node);
		} else if (node instanceof TypeLiteral) {
			return flatten((TypeLiteral)node);
		} else if (node instanceof Type) {
			return sb.append(node.toString());
		} else
			throw new RuntimeException("Unexpected expression type " + node.getClass().toString());
	}
	
	protected StringBuilder flatten(ArrayAccess node) {
		flatten(node.getArray());
		sb.append("[");
		flatten(node.getIndex());
		sb.append("]");
		return sb;
	}

	protected StringBuilder flatten(BooleanLiteral node) {
		if (node.booleanValue() == true)
			sb.append("true");
		else
			sb.append("false");
		return sb;
	}

	protected StringBuilder flatten(CastExpression node) {
		sb.append("(");
		flatten(node.getType());
		sb.append(")");
		flatten(node.getExpression());
		return sb;
	}

	protected StringBuilder flatten(CharacterLiteral node) {
		sb.append(node.getEscapedValue());
		return sb;
	}

	protected StringBuilder flatten(ClassInstanceCreation node) {
		sb.append("new ");
		if (!node.typeArguments().isEmpty()) {
			sb.append("<");
			doList(node.typeArguments());
			sb.append(">");
		}
		flatten(node.getType());
		sb.append("(");
		doList(node.arguments());
		sb.append(")");
		return sb;
	}

	protected StringBuilder flatten(ConditionalExpression node) {
		flatten(node.getExpression());
		sb.append(" ? ");
		flatten(node.getThenExpression());
		sb.append(" : ");
		flatten(node.getElseExpression());
		return sb;
	}

	protected StringBuilder flatten(FieldAccess node) {
		flatten(node.getExpression());
		sb.append(".");
		flatten(node.getName());
		return sb;
	}

	protected StringBuilder flatten(InfixExpression node) {
		flatten(node.getLeftOperand());
		sb.append(' ');
		sb.append(node.getOperator().toString());
		sb.append(' ');
		flatten(node.getRightOperand());
		return sb;
	}

	protected StringBuilder flatten(InstanceofExpression node) {
		flatten(node.getLeftOperand());
		sb.append(" instanceof ");
		flatten(node.getRightOperand());
		return sb;
	}

	protected StringBuilder flatten(MethodInvocation node) {
		if (node.getExpression() != null) {
			flatten(node.getExpression());
			sb.append(".");
		}
		if (!node.typeArguments().isEmpty()) {
			sb.append("<");
			doList(node.typeArguments());
			sb.append(">");
		}
		flatten(node.getName());
		sb.append("(");
		doList(node.arguments());
		sb.append(")");
		return sb;
	}

	protected StringBuilder flatten(NullLiteral node) {
		sb.append("null");
		return sb;
	}

	protected StringBuilder flatten(NumberLiteral node) {
		sb.append(node.getToken());
		return sb;
	}

	protected StringBuilder flatten(ParenthesizedExpression node) {
		sb.append("(");
		flatten(node.getExpression());
		sb.append(")");
		return sb;
	}

	protected StringBuilder flatten(PostfixExpression node) {
		flatten(node.getOperand());
		sb.append(node.getOperator().toString());
		return sb;
	}

	protected StringBuilder flatten(PrefixExpression node) {
		sb.append(node.getOperator().toString());
		flatten(node.getOperand());
		return sb;
	}

	protected StringBuilder flatten(QualifiedName node) {
		flatten(node.getQualifier());
		sb.append(".");
		flatten(node.getName());
		return sb;
	}

	protected StringBuilder flatten(SimpleName node) {
		sb.append(node.getIdentifier());
		return sb;
	}

	protected StringBuilder flatten(StringLiteral node) {
		sb.append(node.getEscapedValue());
		return sb;
	}

	protected StringBuilder flatten(SuperFieldAccess node) {
		if (node.getQualifier() != null) {
			flatten(node.getQualifier());
			sb.append(".");
		}
		sb.append("super.");
		flatten(node.getName());
		return sb;
	}

	protected StringBuilder flatten(SuperMethodInvocation node) {
		if (node.getQualifier() != null) {
			flatten(node.getQualifier());
			sb.append(".");
		}
		sb.append("super.");
		if (!node.typeArguments().isEmpty()) {
			sb.append("<");
			doList(node.typeArguments());
			sb.append(">");
		}
		flatten(node.getName());
		sb.append("(");
		doList(node.arguments());
		sb.append(")");
		return sb;
	}

	protected StringBuilder flatten(ThisExpression node) {
		if (node.getQualifier() != null) {
			flatten(node.getQualifier());
			sb.append(".");
		}
		sb.append("this");
		return sb;
	}

	protected StringBuilder flatten(TypeLiteral node) {
		flatten(node.getType());
		sb.append(".class");
		return sb;
	}
	
	protected void doList(List<?> children) {
		for (Iterator<?> it = children.iterator(); it.hasNext(); ) {
			ASTNode e = (ASTNode)it.next();
			flatten(e);
			if (it.hasNext())
				sb.append(",");
		}
	}

}
