package codehint.ast;

public class LongLiteral extends NumberLiteral {
	
	private final long number;
	
	public LongLiteral(long n) {
		this.number = n;
	}

	public long getNumber() {
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
		result = prime * result + (int) (number ^ (number >>> 32));
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
		LongLiteral other = (LongLiteral) obj;
		if (number != other.number)
			return false;
		return true;
	}

}
