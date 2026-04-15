import com.virtenio.commander.io.DataConnection;
import com.virtenio.commander.toolsets.preon32.Preon32Helper;
import com.fazecast.jSerialComm.SerialPort;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.apache.tools.ant.DefaultLogger;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.*;
import java.util.*;
import java.io.File;

public class deskApp {
	Calendar cal = Calendar.getInstance();

	private BufferedWriter writer;
	private Scanner choice;
	private volatile boolean exit = false;

	Preon32Helper c;
	private ArrayList<SerialPort> listSerialPorts;

	public void writeToFileAndDisplay(String fName, String folName, BufferedInputStream in) throws Exception {
		new Thread() {
			byte[] buffer = new byte[2048];
			String s;
			File newFolder = new File(folName);

			public void run() {
				if (!newFolder.exists()) newFolder.mkdir();
				String path = folName + "/" + fName;

				try {
					FileWriter fw = new FileWriter(path);
					writer = new BufferedWriter(fw);
				} catch (Exception e) { e.printStackTrace(); }

				StringBuilder sb = new StringBuilder();

				System.out.println("╔══════════════════════════════════════════════════════════╗");
				System.out.println("║              REAL-TIME SENSING DATA                      ║");
				System.out.println("║  Saving to: " + folName + "/" + fName);
				System.out.println("╚══════════════════════════════════════════════════════════╝");
				System.out.println();

				while (!exit) {
					try {
						int len = in.read(buffer);
						if (len <= 0) continue;

						s = new String(buffer, 0, len);
						sb.append(s);

						int idx;
						while ((idx = sb.indexOf("#")) != -1) {
							String frame = sb.substring(0, idx).trim();
							sb.delete(0, idx + 1);

							if (frame.startsWith("SENSE")) {
							    System.out.println("[DATA] " + frame);

							    try {
							        writer.write(frame);
							        writer.newLine();
							        writer.flush();
							    } catch (IOException e) { e.printStackTrace(); }
							}
						}
					} catch (IOException e) {}
					Arrays.fill(buffer, (byte) 0);
				}
				try {
					writer.flush();
					writer.close();
					System.out.println();
					System.out.println("File tersimpan di: " + path);
				} catch (Exception e) { e.printStackTrace(); }
			}
		}.start();
	}

	// ----------------------------------------------------------------
	// ANT HELPERS
	// ----------------------------------------------------------------
	private void context_set(String target) throws Exception {
		DefaultLogger consoleLogger = getConsoleLogger();
		File buildFile = new File("C:\\Users\\Alvin Chandra\\eclipse-workspace\\BaseStationWSN\\buildUser.xml");
		Project antProject = new Project();
		antProject.setUserProperty("ant.file", buildFile.getAbsolutePath());
		antProject.addBuildListener(consoleLogger);
		try {
			antProject.fireBuildStarted();
			antProject.init();
			ProjectHelper helper = ProjectHelper.getProjectHelper();
			antProject.addReference("ant.ProjectHelper", helper);
			helper.parse(antProject, buildFile);
			antProject.executeTarget(target);
			antProject.fireBuildFinished(null);
		} catch (BuildException e) { e.printStackTrace(); }
	}

	private void time_synchronize() throws Exception {
		DefaultLogger consoleLogger = getConsoleLogger();
		File buildFile = new File("C:\\Users\\Alvin Chandra\\eclipse-workspace\\BaseStationWSN\\build.xml");
		Project antProject = new Project();
		antProject.setUserProperty("ant.file", buildFile.getAbsolutePath());
		antProject.addBuildListener(consoleLogger);
		try {
			antProject.fireBuildStarted();
			antProject.init();
			ProjectHelper helper = ProjectHelper.getProjectHelper();
			antProject.addReference("ant.ProjectHelper", helper);
			helper.parse(antProject, buildFile);
			antProject.executeTarget("cmd.time.synchronize");
			antProject.fireBuildFinished(null);
		} catch (BuildException e) { e.printStackTrace(); }
	}

	// ----------------------------------------------------------------
	// MAIN MENU
	// ----------------------------------------------------------------
	public void init() throws Exception {
		try {
			SerialPort[] arrSerialPort = SerialPort.getCommPorts();
			this.listSerialPorts = this.filterPort(arrSerialPort, "Preon32");

			for (SerialPort serialport : this.listSerialPorts) {
				System.out.println("Port ditemukan: " + serialport.getSystemPortName());
			}

			Preon32Helper nodeHelper = new Preon32Helper("COM5", 115200);
			DataConnection conn = nodeHelper.runModule("progBS");

			BufferedInputStream in = new BufferedInputStream(conn.getInputStream());

			int choiceentry;
			String s;
			choice = new Scanner(System.in);
			conn.flush();

			do {
				System.out.println();
				System.out.println("╔══════════════════════╗");
				System.out.println("║        MENU          ║");
				System.out.println("╠══════════════════════╣");
				System.out.println("║  1. Hi All           ║");
				System.out.println("║  2. Set Your Time    ║");
				System.out.println("║  3. Tell Your Time   ║");
				System.out.println("║  4. Go Sense NOW!    ║");
				System.out.println("║  0. Exit             ║");
				System.out.println("╚══════════════════════╝");
				System.out.print("Choice: ");

				choiceentry = choice.nextInt();
				conn.write(choiceentry);
				Thread.sleep(200);

				switch (choiceentry) {
					case 0: {
						exit = true;
						System.out.println("Keluar...");
						Thread.sleep(500);
						System.exit(0);   
						break;
					}
					case 1: {
						byte[] buffer = new byte[1024];
						while (in.available() > 0) {
							in.read(buffer);
							s = new String(buffer);
							conn.flush();
							String[] subStr = s.split("#");
							for (String w : subStr) {
								if (w.startsWith("HELLO"))
									System.out.println("[HELLO] " + w);
								else if (w.startsWith("RSSI"))
									System.out.println("[RSSI]  " + w);
							}
						}
						break;
					}
					case 2: {
						byte[] buffer = new byte[1024];
						while (in.available() > 0) {
							in.read(buffer);
							conn.flush();
							s = new String(buffer);
							String[] subStr = s.split("#");
							for (String w : subStr) {
								if (w.startsWith("SET"))
									System.out.println("[SET]   " + w);
								else if (w.startsWith("RSSI"))
									System.out.println("[RSSI]  " + w);
							}
						}
						break;
					}
					case 3: {
						byte[] buffer = new byte[1024];
						while (in.available() > 0) {
							in.read(buffer);
							conn.flush();
							s = new String(buffer);
							String[] subStr = s.split("#");
							for (String w : subStr) {
								if (w.startsWith("NOW"))
									System.out.println("[NOW]   " + w);
								else if (w.startsWith("RSSI"))
									System.out.println("[RSSI]  " + w);
							}
						}
						
						String desktopTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
					            .format(new java.util.Date());
					    System.out.println("[BS]    " + desktopTime);
						break;
					}
					case 4: {
						String folderName = new java.text.SimpleDateFormat("yyyy-MM-dd")
								.format(new java.util.Date());
						String fileName = "sensing_" + System.currentTimeMillis() + ".txt";
						writeToFileAndDisplay(fileName, folderName, in);
						break;
					}
				}

			} while (choiceentry != 0);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static DefaultLogger getConsoleLogger() {
		DefaultLogger consoleLogger = new DefaultLogger();
		consoleLogger.setErrorPrintStream(System.err);
		consoleLogger.setOutputPrintStream(System.out);
		consoleLogger.setMessageOutputLevel(Project.MSG_INFO);
		return consoleLogger;
	}

	private ArrayList<SerialPort> filterPort(SerialPort[] arr, String target) {
		ArrayList<SerialPort> res = new ArrayList<SerialPort>();
		for (SerialPort serialport : arr) {
			if (serialport.getDescriptivePortName().contains(target)) {
				res.add(serialport);
			}
		}
		return res;
	}

	public static void main(String[] args) throws Exception {
		deskApp aGet = new deskApp();
		aGet.context_set("context.set.1");
		aGet.time_synchronize();
		aGet.init();
	}
}