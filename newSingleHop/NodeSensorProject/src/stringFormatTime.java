import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class stringFormatTime {

	public static String SFFull(long msecs) {
    	GregorianCalendar cal = new GregorianCalendar();
    	StringBuffer sBuf = new StringBuffer(8);

    	cal.setTime(new Date(msecs));

    	int hour = cal.get(Calendar.HOUR_OF_DAY);
    	if (hour == 0) {
			hour = 12;
		}
   		if (hour < 10) {
			sBuf.append("0");
		}
    	sBuf.append(Integer.toString(hour));
    	sBuf.append(":");

    	int minute = cal.get(Calendar.MINUTE);
    	if (minute < 10) {
			sBuf.append("0");
		}
    	sBuf.append(Integer.toString(minute));
    	sBuf.append(":");

    	int second = cal.get(Calendar.SECOND);
    	if (second < 10) {
			sBuf.append("0");
		}
    	sBuf.append(Integer.toString(second));

    	sBuf.append(".");
    	int msecond = cal.get(Calendar.MILLISECOND);
    	sBuf.append(Integer.toString(msecond));
    	sBuf.append(" ");

		int day = cal.get(Calendar.DAY_OF_MONTH);
    	sBuf.append(Integer.toString(day));
    	sBuf.append("-");

    	int month = cal.get(Calendar.MONTH) + 1;
		sBuf.append(Integer.toString(month));
		sBuf.append("-");

    	int year = cal.get(Calendar.YEAR);
    	sBuf.append(Integer.toString(year));

    	return (sBuf.toString());
	}

	public String SFFile(long msecs) {
    	GregorianCalendar cal = new GregorianCalendar();
    	StringBuffer sBuf = new StringBuffer(8);

    	cal.setTime(new Date(msecs));

    	int hour = cal.get(Calendar.HOUR_OF_DAY);
    	if (hour == 0) {
			hour = 12;
		}
   		if (hour < 10) {
			sBuf.append("0");
		}
    	sBuf.append(Integer.toString(hour));
    	sBuf.append("_");

    	int minute = cal.get(Calendar.MINUTE);
    	if (minute < 10) {
			sBuf.append("0");
		}
    	sBuf.append(Integer.toString(minute));
    	sBuf.append("_");

    	int second = cal.get(Calendar.SECOND);
    	if (second < 10) {
			sBuf.append("0");
		}
    	sBuf.append(Integer.toString(second));
       	sBuf.append("_");

		int day = cal.get(Calendar.DAY_OF_MONTH);
    	sBuf.append(Integer.toString(day));
    	sBuf.append("_");

    	int month = cal.get(Calendar.MONTH) + 1;
		sBuf.append(Integer.toString(month));
		sBuf.append("_");

    	int year = cal.get(Calendar.YEAR);
    	sBuf.append(Integer.toString(year));

    	return (sBuf.toString());
	}


	public String SFDate(long msecs) {
    	GregorianCalendar cal = new GregorianCalendar();
    	StringBuffer sBuf = new StringBuffer(8);

    	cal.setTime(new Date(msecs));

		int day = cal.get(Calendar.DAY_OF_MONTH);
    	sBuf.append(Integer.toString(day));
    	sBuf.append("-");

    	int month = cal.get(Calendar.MONTH) + 1;
		sBuf.append(Integer.toString(month));
		sBuf.append("-");

    	int year = cal.get(Calendar.YEAR);
    	sBuf.append(Integer.toString(year));

    	return (sBuf.toString());
	}

	public String SFTime(long msecs)
	{
		GregorianCalendar cal = new GregorianCalendar();
    	StringBuffer sBuf = new StringBuffer(8);

		cal.setTime(new Date(msecs));
	 	int hour = cal.get(Calendar.HOUR_OF_DAY);
    	if (hour == 0) {
			hour = 12;
		}
   		if (hour < 10) {
			sBuf.append("0");
		}
    	sBuf.append(Integer.toString(hour));
    	sBuf.append(":");

    	int minute = cal.get(Calendar.MINUTE);
    	if (minute < 10) {
			sBuf.append("0");
		}
    	sBuf.append(Integer.toString(minute));
    	sBuf.append(":");

    	int second = cal.get(Calendar.SECOND);
    	if (second < 10) {
			sBuf.append("0");
		}
    	sBuf.append(Integer.toString(second));

    	sBuf.append(".");
    	int msecond = cal.get(Calendar.MILLISECOND);
    	sBuf.append(Integer.toString(msecond));
    	sBuf.append(" ");

    	return (sBuf.toString());
	}
}


