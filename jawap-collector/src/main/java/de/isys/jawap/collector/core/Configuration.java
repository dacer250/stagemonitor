package de.isys.jawap.collector.core;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.SECONDS;

public class Configuration {

	private final Log logger = LogFactory.getLog(getClass());
	private Properties properties;
	private ScheduledExecutorService propertiesReloader;
	private ConcurrentMap<String, Object> propertiesCache = new ConcurrentHashMap<String, Object>();

	public Configuration() {
		loadProperties();
		long reloadInterval = getLong("jawap.properties.reloadIntervalSeconds", -1);
		if (reloadInterval > 0) {
			propertiesReloader = Executors.newSingleThreadScheduledExecutor();
			propertiesReloader.scheduleAtFixedRate(new Runnable() {
				@Override
				public void run() {
					loadProperties();
				}
			}, reloadInterval, reloadInterval, SECONDS);
		}
	}

	public int 		getNoOfWarmupRequests() { 				return getInt(		"jawap.monitor.noOfWarmupRequests", 				0); }
	public int 		getWarmupSeconds() { 					return getInt(		"jawap.monitor.warmupSeconds", 						0); }
	public boolean 	isCollectRequestStats() { 				return getBoolean(	"jawap.monitor.collectRequestStats", 				true); }
	public boolean 	isRequestTimerEnabled() { 				return getBoolean(	"jawap.monitor.requestTimerEnabled", 				true); }
	public boolean	isCollectHeaders() {					return getBoolean(	"jawap.monitor.http.collectHeaders",				true);}
	public List<String> getExcludedHeaders() {				return getLowerStrings("jawap.monitor.http.headers.excluded", 			"cookie");}
	public List<Pattern> getConfidentialQueryParams() {		return getPatterns(	"jawap.monitor.http.queryparams.confidential.regex","(?i).*pass.*, (?i).*credit.*, (?i).*pwd.*");}
	public long 	getConsoleReportingInterval() { 		return getLong(		"jawap.reporting.interval.console", 				60); }
	public boolean 	reportToJMX() { 						return getBoolean(	"jawap.reporting.jmx", 								true); }
	public long 	getGraphiteReportingInterval() { 		return getLong(		"jawap.reporting.interval.graphite", 				60); }
	public String 	getGraphiteHostName() { 				return getString(	"jawap.reporting.graphite.hostName"); }
	public int 		getGraphitePort() { 					return getInt(		"jawap.reporting.graphite.port", 					2003); }
	public long 	getMinExecutionTimeNanos() { 			return getLong(		"jawap.profiler.minExecutionTimeNanos", 			0L); }
	public int 		getCallStackEveryXRequestsToGroup() { 	return getInt(		"jawap.profiler.callStackEveryXRequestsToGroup", 	-1); }
	public boolean 	isLogCallStacks() { 					return getBoolean(	"jawap.profiler.logCallStacks", 					true); }
	public boolean 	isReportCallStacksToServer() { 			return getBoolean(	"jawap.profiler.reportCallStacksToServer", 			false); }
	public String 	getApplicationName() { 					return getString(	"jawap.applicationName"); }
	public String	getInstanceName() { 					return getString(	"jawap.instanceName"); }
	public String 	getServerUrl() { 						return getString(	"jawap.serverUrl"); }
	public List<String> getExcludedMetricsPatterns() {		return getStrings("jawap.metrics.excluded.pattern", "");}
	public Map<Pattern, String> getGroupUrls() {			return getPatternMap("jawap.groupUrls",
						"/\\d+:     /{id}," +
						"(.*).js:   *.js," +
						"(.*).css:  *.css," +
						"(.*).js:   *.jpg," +
						"(.*).jpeg: *.jpeg," +
						"(.*).png:  *.png");
	}

	private void loadProperties() {
		logger.info("reloading properties");
		properties = new Properties();
		InputStream resourceStream = getClass().getClassLoader().getResourceAsStream("jawap.properties");
		if (resourceStream != null) {
			try {
				properties.load(resourceStream);
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			} finally {
				try {
					resourceStream.close();
				} catch (IOException e) {
					logger.error(e.getMessage(), e);
				}
				propertiesCache.clear();
			}
		}
	}

	public String getString(final String key) {
		return getString(key, null);
	}

	public String getString(final String key, final String defaultValue) {
		return getAndCache(key, null, new PropertyLoader<String>() {
			@Override
			public String load() {
				return properties.getProperty(key, defaultValue);
			}
		});
	}

	public List<String> getLowerStrings(final String key, final String defaultValue) {
		return getAndCache(key, null, new PropertyLoader<List<String>>() {
			@Override
			public List<String> load() {
				String property = properties.getProperty(key, defaultValue);
				if (property != null && property.length() > 0) {
					final String[] split = property.split(",");
					for (int i = 0; i < split.length; i++) {
						split[i] = split[i].trim().toLowerCase();
					}
					return Arrays.asList(split);
				}
				return emptyList();
			}
		});
	}

	public List<Pattern> getPatterns(final String key, final String defaultValue) {
		final List<String> strings = getStrings(key, defaultValue);
		List<Pattern> patterns = new ArrayList<Pattern>(strings.size());
		for (String patternString : strings) {
			try {
				patterns.add(Pattern.compile(patternString));
			} catch (PatternSyntaxException e) {
				logger.error("Error while compiling pattern " + patternString, e);
			}
		}
		return patterns;
	}
	public List<String> getStrings(final String key, final String defaultValue) {
		return getAndCache(key, null, new PropertyLoader<List<String>>() {
			@Override
			public List<String> load() {
				String property = properties.getProperty(key, defaultValue);
				if (property != null && property.length() > 0) {
					final String[] split = property.split(",");
					for (int i = 0; i < split.length; i++) {
						split[i] = split[i].trim();
					}
					return Arrays.asList(split);
				}
				return emptyList();
			}
		});
	}

	public boolean getBoolean(final String key, final boolean defaultValue) {
		return getAndCache(key, defaultValue, new PropertyLoader<Boolean>() {
			@Override
			public Boolean load() {
				return Boolean.parseBoolean(properties.getProperty(key, Boolean.toString(defaultValue)));
			}
		});
	}

	public int getInt(String key, int defaultValue) {
		return (int) getLong(key, defaultValue);
	}

	public long getLong(final String key, final long defaultValue) {
		return getAndCache(key, defaultValue, new PropertyLoader<Long>() {
			@Override
			public Long load() {
				String value = properties.getProperty(key, Long.toString(defaultValue));
				try {
					return Long.parseLong(value);
				} catch (NumberFormatException e) {
					logger.error(e.getMessage(), e);
					return defaultValue;
				}
			}
		});
	}

	public Map<Pattern, String> getPatternMap(final String key, final String defaultValue) {
		return getAndCache(key, null, new PropertyLoader<Map<Pattern, String>>() {
			@Override
			public Map<Pattern, String> load() {
				String patternString = properties.getProperty(key, defaultValue);
				try {
					String[] groups = patternString.split(",");
					Map<Pattern, String> pattenGroupMap = new HashMap<Pattern, String>(groups.length);

					for (String group : groups) {
						group = group.trim();
						String[] keyValue = group.split(":");
						pattenGroupMap.put(Pattern.compile(keyValue[0].trim()), keyValue[1].trim());
					}
					return pattenGroupMap;
				} catch (RuntimeException e) {
					logger.error("Error while parsing pattern map. Expected format <regex>: <name>[, <regex>: <name>]. " +
							"Actual value: " + patternString, e);
					return Collections.emptyMap();
				}
			}
		});
	}

	private <T> T getAndCache(String key, T defaultValue, PropertyLoader<T> propertyLoader) {
		T result = (T) propertiesCache.get(key);
		if (result == null) {
			result = propertyLoader.load();
			if (result == null) {
				result = defaultValue;
			}
			if (result != null) {
				propertiesCache.put(key, result);
			}
		}
		return result;
	}

	private interface PropertyLoader<T> {
		T load();
	}

}
