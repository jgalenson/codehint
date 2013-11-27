package codehint.ast;

public class IntLiteral extends NumberLiteral {
	
	private final int number;
	
	public IntLiteral(int n) {
		this.number = n;
	}

	public int getNumber() {
		return number;
	}
	
	@Override
	protected void accept0(ASTVisitor visitor) {
		visitor.visit(this);
	}
	
	@Override
	public String toString() {
		return String.valueOf(number);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + number;
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
		IntLiteral other = (IntLiteral) obj;
		if (number != other.number)
			return false;
		return true;
	}

}
