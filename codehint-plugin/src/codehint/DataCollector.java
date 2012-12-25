package codehint;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.UUID;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.dialogs.PreferencesUtil;

import codehint.utils.EclipseUtils;

/**
 * Collects and reports data on how the plugin is used.
 * It reports the data by encoding it as a URL query string
 * and sending it as a HTTP GET request.
 */
public class DataCollector {

	private static final String UUID_PREFNAME = "codehint.uuid";
	private static final String REPORT_URL = "http://potus.cs.berkeley.edu/~joel/codehint/";
	
	public static void start() {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		if (!store.contains(UUID_PREFNAME)) {  // If this is the first run, store a unique uuid and open the preferences page.
			store.setValue(UUID_PREFNAME, UUID.randomUUID().toString());
	    	Display.getDefault().asyncExec(new Runnable(){
				@Override
				public void run() {
					PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(EclipseUtils.getShell(), "codehint.preferencesPage", null, null);
					dialog.setMessage("Thanks for installing CodeHint");
					dialog.open();
				}
	    	});
		} else  // If this is not the first run, try to send any saved reports.
			sendSavedReports();
	}
	
	/**
	 * Reports the given information if the user allows us to do so.
	 * @param cmd The high-level name of the command being logged.
	 * @param params A sequence of key=value pairs of attributes.
	 */
	public static void log(String cmd, String... params) {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		if (!store.getBoolean(PreferencePage.DATA_REPORT_PREFNAME))
			return;
		// Build a URL query string.
		StringBuilder sb = new StringBuilder(cmd);
		sb.append("?");
		sb.append("uuid=").append(store.getString(UUID_PREFNAME));
		for (String param: params)
			sb.append("&").append(param);
		String msg = sb.toString();
		try {
			msg = URLEncoder.encode(msg, "UTF-8");
			sendReport(msg);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Sends the given string as a report.  We first try
	 * to send it as an HTTP request, and log it to a file
	 * to be sent later if that fails.
	 * @param msg The message to log.
	 */
	private static void sendReport(String msg) {
		if (sendHTTPRequest(msg))
			return;
		saveReport(msg);
	}

	/**
	 * Sends the given query as a HTTP GET request.
	 * @param query The query to log, which should already
	 * have been encoded as a URL string.
	 * @return True if the query was successfully sent
	 * and false otherwise.
	 */
	private static boolean sendHTTPRequest(String query) {
		InputStream response = null;
		try {
			URL url = new URL(REPORT_URL + query);
			URLConnection connection = url.openConnection();
			connection.connect();
			response = connection.getInputStream();
			return true;
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			// The query isn't an actual file on the webserver, so we expect this.
			return true;
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (response != null)
				try {
					response.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
		return false;
	}
	
	/**
	 * Saves the given report to a file to be sent to the
	 * server later.
	 * @param msg The message.
	 */
	private static void saveReport(String msg) {
		BufferedWriter out = null;
		try {
			out = new BufferedWriter(new FileWriter(getReportFileName(getProject()), true));
			out.append(msg + System.getProperty("line.separator"));
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (out != null)
				try {
					out.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
	}
	
	/**
	 * Tries to send any unsent reports that were saved
	 * to a file.
	 */
	public static void sendSavedReports() {
		for (IProject project: ResourcesPlugin.getWorkspace().getRoot().getProjects())
			sendSavedReports(JavaCore.create(project));
	}

	/**
	 * Tries to send any unsent reports that were saved
	 * to a file.
	 * @param project The project whose saved reports
	 * we should try to send.
	 */
	private static void sendSavedReports(IJavaProject project) {
		File file = new File(getReportFileName(project));
		if (!file.exists())
			return;
		String unsentReports = "";  // The reports that fail to be sent.
		BufferedReader in = null;
		try {
			in = new BufferedReader(new FileReader(file));
		    String line;
		    while ((line = in.readLine()) != null)
		    	if (!sendHTTPRequest(line))
		    		unsentReports += (unsentReports.isEmpty() ? "" : System.getProperty("line.separator")) + line;
		} catch (FileNotFoundException e) {
			// Do nothing if there is no file, i.e., nothing has been saved.
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (in != null)
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
		// Delete the file of saved reports and write out any reports that we could not send.
		file.delete();
		if (!unsentReports.isEmpty())
			saveReport(unsentReports);
	}

	/**
	 * Gets the name of the file to which we save reports
	 * that fail to be sent.
	 * @param project The current project.
	 * @return The name of the file that stores reports
	 * whose send failed.
	 */
	private static String getReportFileName(IJavaProject project) {
		return EclipseUtils.getPluginWorkingLocation(project) + System.getProperty("file.separator") + "data.txt";
	}
	
	/**
	 * Gets the current project.
	 * We have to be a bit careful about this, since we don't
	 * know the context from which this is called.
	 * @return The current project.
	 */
	private static IJavaProject getProject() {
		IJavaStackFrame stack = EclipseUtils.getStackFrame();
		if (stack != null)
			return EclipseUtils.getProject(stack);
		return JavaCore.create(EclipseUtils.getEditorFile(EclipseUtils.getActiveTextEditor()).getProject());
	}

}
