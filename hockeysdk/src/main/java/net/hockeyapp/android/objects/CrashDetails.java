package net.hockeyapp.android.objects;

import android.content.Context;
import android.text.TextUtils;

import net.hockeyapp.android.utils.HockeyLog;
import net.hockeyapp.android.utils.JSONDateUtils;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Date;

@SuppressWarnings({"unused", "WeakerAccess"})
public class CrashDetails {
    private static final String FIELD_CRASH_REPORTER_KEY = "CrashReporter Key";
    private static final String FIELD_APP_START_DATE = "Start Date";
    private static final String FIELD_APP_CRASH_DATE = "Date";
    private static final String FIELD_OS_VERSION = "Android";
    private static final String FIELD_OS_BUILD = "Android Build";
    private static final String FIELD_DEVICE_MANUFACTURER = "Manufacturer";
    private static final String FIELD_DEVICE_MODEL = "Model";
    private static final String FIELD_APP_PACKAGE = "Package";
    private static final String FIELD_APP_VERSION_NAME = "Version Name";
    private static final String FIELD_APP_VERSION_CODE = "Version Code";
    private static final String FIELD_THREAD_NAME = "Thread";

    private static final String FIELD_FORMAT = "Format";
    private static final String FIELD_FORMAT_VALUE = "Xamarin";
    private static final String FIELD_XAMARIN_CAUSED_BY = "Xamarin caused by: "; //Field that marks a Xamarin Exception

    private final String crashIdentifier;
    private String reporterKey;
    private Date appStartDate;
    private Date appCrashDate;
    private String osVersion;
    private String osBuild;
    private String deviceManufacturer;
    private String deviceModel;
    private String appPackage;
    private String appVersionName;
    private String appVersionCode;
    private String threadName;
    private String throwableStackTrace;
    private Boolean isXamarinException;
    private String format;

    public CrashDetails(String crashIdentifier) {
        this.crashIdentifier = crashIdentifier;
        isXamarinException = false;
        throwableStackTrace = "";
    }

    public CrashDetails(String crashIdentifier, Throwable throwable) {
        this(crashIdentifier);

        isXamarinException = false;

        final Writer stackTraceResult = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(stackTraceResult);
        throwable.printStackTrace(printWriter);
        throwableStackTrace = stackTraceResult.toString();
    }


    public CrashDetails(String crashIdentifier, Throwable throwable, String managedExceptionString, Boolean isManagedException) {
        this(crashIdentifier);

        final Writer stackTraceResult = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(stackTraceResult);

        isXamarinException = true;

        //Add the header field "Format" to the crash
        //the value is "Xamarin", for now there are no other values and it's only set in case we have an exception coming from
        //the Xamarin SDK. It can be a java exception, a managed exception, or a mixed one.
        setFormat(FIELD_FORMAT_VALUE);

        if (isManagedException) {
            //add "Xamarin Caused By" before the managed stacktrace. No new line after it.
            printWriter.print(FIELD_XAMARIN_CAUSED_BY);

            //print the managed exception
            throwable.printStackTrace(printWriter);
        } else {
            //If we have managedExceptionString, we have a MIXED (Java & C#)
            //exception, The throwable will be the Java exception.
            if (!TextUtils.isEmpty(managedExceptionString)) {
                //Print the java exception
                throwable.printStackTrace(printWriter);

                //Add "Xamarin Caused By" before the managed stacktrace. No new line after it.
                printWriter.print(FIELD_XAMARIN_CAUSED_BY);
                //print the stacktrace of the managed exception
                printWriter.print(managedExceptionString);
            } else {
                //we have a java exception, no "Xamarin Caused By:"
                throwable.printStackTrace(printWriter);
            }
        }

        throwableStackTrace = stackTraceResult.toString();
    }

    public static CrashDetails fromFile(File file) throws IOException, JSONException {
        String crashIdentifier = file.getName().substring(0, file.getName().indexOf(".stacktrace"));
        return fromReader(crashIdentifier, new FileReader(file));
    }

    public static CrashDetails fromReader(String crashIdentifier, Reader in) throws IOException, JSONException {
        BufferedReader bufferedReader = new BufferedReader(in);

        CrashDetails result = new CrashDetails(crashIdentifier);

        String readLine, headerName, headerValue;
        boolean headersProcessed = false;
        StringBuilder stackTraceBuilder = new StringBuilder();
        while ((readLine = bufferedReader.readLine()) != null) {
            if (!headersProcessed) {

                if (readLine.isEmpty()) {
                    // empty line denotes break between headers and stack trace
                    headersProcessed = true;
                    continue;
                }

                int colonIndex = readLine.indexOf(":");
                if (colonIndex < 0) {
                    HockeyLog.error("Malformed header line when parsing crash details: \"" + readLine + "\"");
                }

                headerName = readLine.substring(0, colonIndex).trim();
                headerValue = readLine.substring(colonIndex + 1, readLine.length()).trim();

                if (headerName.equals(FIELD_CRASH_REPORTER_KEY)) {
                    result.setReporterKey(headerValue);
                } else if (headerName.equals(FIELD_APP_START_DATE)) {
                    try {
                        result.setAppStartDate(JSONDateUtils.toDate(headerValue));
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                } else if (headerName.equals(FIELD_APP_CRASH_DATE)) {
                    try {
                        result.setAppCrashDate(JSONDateUtils.toDate(headerValue));
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                } else if (headerName.equals(FIELD_OS_VERSION)) {
                    result.setOsVersion(headerValue);
                } else if (headerName.equals(FIELD_OS_BUILD)) {
                    result.setOsBuild(headerValue);
                } else if (headerName.equals(FIELD_DEVICE_MANUFACTURER)) {
                    result.setDeviceManufacturer(headerValue);
                } else if (headerName.equals(FIELD_DEVICE_MODEL)) {
                    result.setDeviceModel(headerValue);
                } else if (headerName.equals(FIELD_APP_PACKAGE)) {
                    result.setAppPackage(headerValue);
                } else if (headerName.equals(FIELD_APP_VERSION_NAME)) {
                    result.setAppVersionName(headerValue);
                } else if (headerName.equals(FIELD_APP_VERSION_CODE)) {
                    result.setAppVersionCode(headerValue);
                } else if (headerName.equals(FIELD_THREAD_NAME)) {
                    result.setThreadName(headerValue);
                } else if (headerName.equals(FIELD_FORMAT)) {
                    result.setFormat(headerValue);
                }

            } else {
                stackTraceBuilder.append(readLine).append("\n");
            }
        }
        result.setThrowableStackTrace(stackTraceBuilder.toString());

        return result;
    }

    public void writeCrashReport(Context context) {
        File file = new File(context.getFilesDir(), crashIdentifier + ".stacktrace");
        try {
            writeCrashReport(file);
        } catch (JSONException e) {
            HockeyLog.error("Could not write crash report with error " + e.toString());
        }
    }

    public void writeCrashReport(final File file) throws JSONException {
        HockeyLog.debug("Writing unhandled exception to: " + file.getAbsolutePath());

        BufferedWriter writer = null;

        try {
            writer = new BufferedWriter(new FileWriter(file));

            writeHeader(writer, FIELD_APP_PACKAGE, appPackage);
            writeHeader(writer, FIELD_APP_VERSION_CODE, appVersionCode);
            writeHeader(writer, FIELD_APP_VERSION_NAME, appVersionName);
            writeHeader(writer, FIELD_OS_VERSION, osVersion);
            writeHeader(writer, FIELD_OS_BUILD, osBuild);
            writeHeader(writer, FIELD_DEVICE_MANUFACTURER, deviceManufacturer);
            writeHeader(writer, FIELD_DEVICE_MODEL, deviceModel);
            writeHeader(writer, FIELD_THREAD_NAME, threadName);
            writeHeader(writer, FIELD_CRASH_REPORTER_KEY, reporterKey);

            writeHeader(writer, FIELD_APP_START_DATE, JSONDateUtils.toString(appStartDate));
            writeHeader(writer, FIELD_APP_CRASH_DATE, JSONDateUtils.toString(appCrashDate));

            if (isXamarinException) {
                writeHeader(writer, FIELD_FORMAT, FIELD_FORMAT_VALUE);
            }

            writer.write("\n");
            writer.write(throwableStackTrace);

            writer.flush();

        } catch (IOException e) {
            HockeyLog.error("Error saving crash report!", e);
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e1) {
                HockeyLog.error("Error saving crash report!", e1);
            }
        }


    }

    private void writeHeader(Writer writer, String name, String value) throws IOException {
        writer.write(name + ": " + value + "\n");
    }

    public String getCrashIdentifier() {
        return crashIdentifier;
    }

    public String getReporterKey() {
        return reporterKey;
    }

    public void setReporterKey(String reporterKey) {
        this.reporterKey = reporterKey;
    }

    public Date getAppStartDate() {
        return appStartDate;
    }

    public void setAppStartDate(Date appStartDate) {
        this.appStartDate = appStartDate;
    }

    public Date getAppCrashDate() {
        return appCrashDate;
    }

    public void setAppCrashDate(Date appCrashDate) {
        this.appCrashDate = appCrashDate;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    public String getOsBuild() {
        return osBuild;
    }

    public void setOsBuild(String osBuild) {
        this.osBuild = osBuild;
    }

    public String getDeviceManufacturer() {
        return deviceManufacturer;
    }

    public void setDeviceManufacturer(String deviceManufacturer) {
        this.deviceManufacturer = deviceManufacturer;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public void setDeviceModel(String deviceModel) {
        this.deviceModel = deviceModel;
    }

    public String getAppPackage() {
        return appPackage;
    }

    public void setAppPackage(String appPackage) {
        this.appPackage = appPackage;
    }

    public String getAppVersionName() {
        return appVersionName;
    }

    public void setAppVersionName(String appVersionName) {
        this.appVersionName = appVersionName;
    }

    public String getAppVersionCode() {
        return appVersionCode;
    }

    public void setAppVersionCode(String appVersionCode) {
        this.appVersionCode = appVersionCode;
    }

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    public String getThrowableStackTrace() {
        return throwableStackTrace;
    }

    public void setThrowableStackTrace(String throwableStackTrace) {
        this.throwableStackTrace = throwableStackTrace;
    }

    public Boolean getIsXamarinException() {
        return isXamarinException;
    }

    public void setIsXamarinException(Boolean isXamarinException) {
        this.isXamarinException = isXamarinException;
    }

    //We could to without a Format property and getters/setters, but we will eventually use this
    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }
}
