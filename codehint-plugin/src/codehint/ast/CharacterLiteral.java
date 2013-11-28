package codehint.ast;

import org.eclipse.jdt.debug.core.IJavaType;

import codehint.utils.Utils;

public class CharacterLiteral extends Expression {
	
	private final char ch;
	
	public CharacterLiteral(IJavaType staticType, char ch) {
		super(staticType);
		this.ch = ch;
	}
	
	public String getEscapedValue() {
		if (ch == '\'')
			return "'\\''";
		return "'" + Utils.getEscapedValue(ch) + "'";
	}

	public char charValue() {
		return ch;
	}
	
	@Override
	protected void accept0(ASTVisitor visitor) {
		visitor.visit(this);
	}
	
	@Override
	public String toString() {
		return getEscapedValue();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ch;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CharacterLiteral other = (CharacterLiteral) obj;
		if (ch != other.ch)
			return false;
		return true;
	}

}
