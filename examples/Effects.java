import java.lang.reflect.Field;


@SuppressWarnings("unused")
public class Effects {
	
	private static int field = 42;
	private static int[] arrField = new int[] { 0, 1, 2 };
	private static final int[] finalArrField = new int[] { 3, 4, 5 };

	public static void main(String[] args) {
		int x = 0;
		int y = 0;
		Counter myCounter = new Counter();
		int[] localArr = new int[] { 0, 1, 2 };
		int[] nullLocalArr = null;
		Field f = null;
		try {
			f = Effects.class.getDeclaredField("aField");
			f.setInt(null, 42);
		} catch (SecurityException e) {
			throw new RuntimeException(e);
		} catch (NoSuchFieldException e) {
			throw new RuntimeException(e);
		} catch (IllegalArgumentException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
		System.out.println(x);
	}
	
	private static int effects(int x) {
		return x + field++;
	}
	
	private static int getField() {
		return field;
	}
	
	private static int changeLocalArr(int[] arr) {
		arr[0] = 4242;
		return 42;
	}
	
	private static int changeArr() {
		arrField[0] = 42;
		return 42;
	}
	
	private static int changeFinalArr() {
		finalArrField[0] = 42;
		return 42;
	}
	
	private static int changeLongArr(int[] arr) {
		return arr[0] = 42;
	}
	
	private static int changeTwice() {
		field = 1;
		field = 2;
		return field;
	}
	
	private static int crash() {
		throw new RuntimeException();
	}
	
	private static int sideEffectOnNewObj() {
		Counter c = new Counter();
		c.increment();
		return 42;
	}
	
	private static int concat() {
		String s = "Hello" + ", " + "world";
		return s.length();
	}
	
	/*private static int printer() {
		System.out.println("Hello" + ", " + "world");
		return 42;
	}
	
	private static int printer2() {
		System.out.println("Hello, world");
		return 42;
	}*/
	
	private static class Counter {
		private int counter;
		private static  int staticCounter = 4242;
		public Counter() {
			counter = 0;
		}
		public int increment() {
			return counter++;
		}
		public static int incrementStatic() {
			return staticCounter++;
		}
	}
	
	private static int aField = 42;
	private static int bField = 42;
	private static int cField = 42;
	
	private static int a() {
		return b() + (aField = 42);
	}
	
	private static int b() {
		return c() + (bField = 42);
	}
	
	private static int c() {
		empty();
		return cField = 42;
	}
	
	private static void empty() {
		
	}
	
	private static int reflectionStatic() {
		try {
			Field field = Effects.class.getDeclaredField("field");
			field.setInt(null, 137);
			return 137;
		} catch (SecurityException e) {
			throw new RuntimeException(e);
		} catch (NoSuchFieldException e) {
			throw new RuntimeException(e);
		} catch (IllegalArgumentException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
	
	private static int reflectionField(Field field) {
		try {
			field.setInt(null, 137);
		} catch (IllegalArgumentException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
		return 137;
	}
	
	private static int reflectionInstance(Counter counter) {
		try {
			Field field = Counter.class.getDeclaredField("counter");
			field.setAccessible(true);
			field.setInt(counter, 137);
			return 137;
		} catch (SecurityException e) {
			throw new RuntimeException(e);
		} catch (NoSuchFieldException e) {
			throw new RuntimeException(e);
		} catch (IllegalArgumentException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
	
	private static int reflectionArrField() {
		try {
			java.lang.reflect.Array.setInt(arrField, 0, 137);
			return 137;
		} catch (SecurityException e) {
			throw new RuntimeException(e);
		} catch (IllegalArgumentException e) {
			throw new RuntimeException(e);
		}
	}
	
	private static int reflectionArrHiddenRead() {
		try {
			java.lang.reflect.Array.setInt(Effects.class.getDeclaredField("arrField").get(null), 0, 137);
			return 137;
		} catch (SecurityException e) {
			throw new RuntimeException(e);
		} catch (IllegalArgumentException e) {
			throw new RuntimeException(e);
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		} catch (NoSuchFieldException e) {
			throw new RuntimeException(e);
		}
	}
	
	private static int reflectionObjRead() {
		try {
			Effects.class.getDeclaredField("field").get(null);
			return 137;
		} catch (SecurityException e) {
			throw new RuntimeException(e);
		} catch (IllegalArgumentException e) {
			throw new RuntimeException(e);
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		} catch (NoSuchFieldException e) {
			throw new RuntimeException(e);
		}
	}

}
