
@SuppressWarnings("unused")
public class DepthTwoEffects {

	public static void main(String[] args) {
		int ret = 0;
		System.out.println(ret);
	}
	
	private static int x = 0;
	private static int[] arr = new int[] { 10 };
	
	private static int incX() {
		return ++x;
	}
	
	private static int incincX() {
		x++;
		return ++x;
	}
	
	private static int sum(int a, int b) {
		return a + b;
	}
	
	private static int incArr0() {
		return ++arr[0];
	}
	
	private static int incincArr0() {
		arr[0]++;
		return ++arr[0];
	}
	
	private static int incArrAlloc() {
		arr = new int[] { 50 };
		return arr[0];
	}

}
