package codehint.ast;

import codehint.utils.Utils;

public class StringLiteral extends Expression {
	
	private final String str;
	
	public StringLiteral(String str) {
		this.str = str;
	}

	public String getLiteralValue() {
		return str;
	}
	
	public String getEscapedValue() {
		StringBuilder sb = new StringBuilder();
		sb.append("\"");
		for (int i = 0; i < str.length(); i++)
			sb.append(Utils.getEscapedValue(str.charAt(i)));
		return sb.append("\"").toString();
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
		result = prime * result + ((str == null) ? 0 : str.hashCode());
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
		StringLiteral other = (StringLiteral) obj;
		if (str == null) {
			if (other.str != null)
				return false;
		} else if (!str.equals(other.str))
			return false;
		return true;
	}

}
