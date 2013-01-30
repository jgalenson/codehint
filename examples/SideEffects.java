import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@SuppressWarnings("unused")
public class SideEffects {

	public static void main(String[] args) {
		int a = 1;
		int b = 2;
		Foo foo = new Foo();
		List<Integer> list = new ArrayList<Integer>();
		list.add(0);
		list.add(1);
		list.add(2);
		int[] localArr = new int[] { 0, 1, 2 };
		Object localArrObj = new int[] { 0, 1 };
		
		int ret = 0;
		System.out.println(ret);
	}
	
	private static int x = 42;
	private static int y = 42;
	private static int[] intArr = new int[] { 0, 1, 2 };
	private static int[][] intintArr = new int[][] { new int[] { 0, 1 }, new int[] { 2, 3 } };
	private static Bar[] barArr = new Bar[] { new Bar(0), new Bar(1) };
	private static int[] nullArr = null;
	private static int[][] nestedNullArr = new int[][] { null, null };
	private static Object objIntArr = new int[] { 0, 1, 2 };
	private static Object objString = "Hello, world";
	
	private static class Foo {
		private int foo = 42;
		public int inc() {
			return foo++;
		}
	}
	
	private static class Bar {
		private int x;
		public Bar(int x) {
			this.x = x;
		}
		public int get() {
			return x;
		}
		public int inc() {
			return ++x;
		}
		public String toString() {
			return "Bar(" + x + ")";
		}
	}
	
	/*private static int print() {
		System.out.println("");
		return 42;
	}*/
	
	private static int modX(int _) {
		int ret = x;
		x = _;
		return ret;
	}
	
	private static int incY(int _) {
		return y++;
	}
	
	private static int intArr0Inc() {
		return intArr[0]++;
	}
	
	private static int intintArr0Inc() {
		return intintArr[0][0]++;
	}
	
	private static int intintArrSwap() {
		int[] tmp = intintArr[0];
		intintArr[0] = intintArr[1];
		intintArr[1] = tmp;
		return intintArr[1][0];
	}
	
	private static int addList(List<Integer> l) {
		l.add(42);
		return l.size();
	}
	
	private static int barArr0Inc() {
		return barArr[0].inc();
	}
	
	private static int barArrSwap() {
		Bar tmp = barArr[0];
		barArr[0] = barArr[1];
		barArr[1] = tmp;
		return barArr[1].get();
	}
	
	private static int intArrNull() {
		intArr = null;
		return 42;
	}
	
	private static int intArrNew() {
		intArr = new int[] { };
		return 42;
	}
	
	private static int barArr0Null() {
		barArr[0] = null;
		return 42;
	}
	
	private static int nullArrAlloc() {
		nullArr = new int[] { 0, 1 };
		return 42;
	}
	
	private static int nullArrRead() {
		int[] tmp = nestedNullArr[0];
		return 42;
	}
	
	private static int nestedNullArrAlloc() {
		nestedNullArr[0] = new int[] { 0, 1 };
		return 42;
	}
	
	private static int intArrIncAlloc() {
		intArr[0]++;
		intArr = new int[] { -100 };
		return 42;
	}
	
	private static int intArrAllocInc() {
		intArr = new int[] { -100 };
		intArr[0]++;
		return 42;
	}
	
	private static class Prepared {
		private int instance;
		private static int stat = 42;
		private static int stat2;
		public Prepared(int i) {
			instance = i;
		}
		public int incInstance() {
			return ++instance;
		}
		public int incStatic() {
			return ++stat + ++stat2;
		}
		public String toString() {
			return "Prepared(" + instance + "," + stat + ")";
		}
	}
	
	private static int preparation() {
		Prepared prep = new Prepared(42);
		prep.incInstance();
		prep.incStatic();
		return 42;
	}
	
	private static int modLocalArr(int[] localArr) {
		if (localArr == null)
			return 42;
		return localArr[0]++;
	}
	
	private static int unchangeLocalArr(int[] localArr) {
		return 42;
	}
	
	private static int allocNewLocalArrParam(int[] localArr) {
		localArr = new int[] { 5, 6, 7 };
		return 42;
	}
	
	private static int reflectField() {
		try {
			SideEffects.class.getDeclaredField("x").set(null, 137);
		} catch (IllegalArgumentException e) {
			throw new RuntimeException(e);
		} catch (SecurityException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		} catch (NoSuchFieldException e) {
			throw new RuntimeException(e);
		}
		return x;
	}
	
	private static int objIntArrToString() {
		objIntArr = "ERROR";
		return 42;
	}
	
	private static int objStringToArr() {
		objString = new String[] { "ERROR" };
		return 42;
	}
	
	private static int objIntArr0Inc() {
		((int[])objIntArr)[0]++;
		return 42;
	}
	
	private static int objIntArr0IncToString() {
		((int[])objIntArr)[0]++;
		objIntArr = "ERROR";
		return 42;
	}
	
	private static int objStringToArrInc() {
		objString = new int[] { 42 };
		((int[])objString)[0]++;
		return 42;
	}
	
	private static int modNonLocalArr(int[] localArr) {
		return intArr[0]++;
	}
	
	private static int modLocalObjArr(Object localObjArr) {
		return ((int[])localObjArr)[0]++;
	}
	
	private static List<Integer> addToList(List<Integer> l) {
		l.add(42);
		return l;
	}

}
