
@SuppressWarnings("unused")
public class EffectEquivalence {

	public static void main(String[] args) {
		int ret = 0;
		//System.out.println(incX() + xPlus42());
		//System.out.println(sum(incX() + xPlus42()));
		System.out.println(ret);
	}
	
	private static int x = 0;
	
	private static int incX() {
		return ++x;
	}
	
	private static int zfortyTwo() {
		return 42;
	}
	
	private static int xPlus42() {
		return x + 42;
	}
	
	private static int sum(int x, int y) {
		return x + y;
	}

}
