package codehint;

/**
 * This class is used in the implementation of the CodeHint
 * plugin and should not be used by clients.
 */
public final class CodeHintImpl {
	
	public static Object[] objects;
	public static int[] ints;
	public static boolean[] booleans;
	public static long[] longs;
	public static byte[] bytes;
	public static char[] chars;
	public static short[] shorts;
	public static float[] floats;
	public static double[] doubles;

	public static boolean[] valid;
	public static String[] toStrings;
	
	public static int valueCount;
	public static int fullCount;
	
	public static Object[] methodResults;

	private static SynthesisSecurityManager newSecurityManager;
	
	public static void init() {
		newSecurityManager = new SynthesisSecurityManager();
		System.setSecurityManager(newSecurityManager);
		newSecurityManager.setEnabled();
	}
	
	public static void reset() {
		objects = null;
		ints = null;
		booleans = null;
		longs = null;
		bytes = null;
		chars = null;
		shorts = null;
		floats = null;
		doubles = null;
		valid = null;
		toStrings = null;
		methodResults = null;
		if (newSecurityManager != null) {  // For some reason, this seems to become null when we fail to set a new SecurityManager.
			newSecurityManager.disable();
			newSecurityManager = null;
		}
	}

}
