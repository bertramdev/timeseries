package grails.plugins.timeseries

import grails.transaction.Transactional
import grails.plugins.timeseries.*

@Transactional
class TimeSeriesService {
	private Map<String, TimeSeriesProvider> providers = [:]
	def grailsApplication
	static final DEFAULT_KEY = '__default'
	private Timer timer = new Timer()
	private Long manageStorageInterval = 3600000l
	private TimerTask timerTask = new TimerTask() {
		public void run() {
			callProviderMethod('manageStorage')
		}
	}

	public TimeSeriesService() {
		log.debug('TimeSeriesService constructed')
	}

	void callProviderMethod(m) {
		providers.each {k, provider->
			try {
				// @TODO need to spin up some new threads for this?
				log.debug('Calling '+m+' on timeseries provider '+k)
				provider."$m"(getConfig())
			} catch(Throwable t) {
				log.error(t)
			}
		}
	}

	void destroy() {
		log.info("shutting down TimeSeriesService...")
		timer.cancel()
		timerTask.cancel()
		callProviderMethod('shutdown')
		log.info("TimeSeriesService shutdown.")
	}

	void init() {
		callProviderMethod('init')
		if (grailsApplication.config.grails.plugins.timeseries.manageStorage.containsKey('interval')) {
			this.manageStorageInterval = Long.parseLong(grailsApplication.config.grails.plugins.timeseries.manageStorage.interval)
		}
		// schedule "manageStorage" call to all teh providers
		if (this.manageStorageInterval) {
			timer.scheduleAtFixedRate(timerTask, this.manageStorageInterval, this.manageStorageInterval)
		}
	}

	void registerProvider(TimeSeriesProvider provider, Boolean setAsDefault = true) {
		providers[provider.name] = provider
		// last one in 
		if (setAsDefault) providers[DEFAULT_KEY] = provider
	}


	void unregisterProvider(String name) {
		try {
			providers[provider.name].shutdown()
		} catch(Throwable t) {
			log.error(t)
		}
		def old = providers.remove(name)
		if (providers[DEFAULT_KEY] == old) {
			providers[DEFAULT_KEY] = providers.values()[0]
		}

	}

	groovy.util.ConfigObject getConfig() {
		grailsApplication.config.grails.plugins.timeseries
	}

	TimeSeriesProvider getProvider(providerName = DEFAULT_KEY) {
		providerName = providerName ?: DEFAULT_KEY
		return  providers[providerName]
	}

	void flush(providerName = null) {
		getProvider(providerName).flush()		
	}

	void saveMetric(referenceId, String metricName, Double metricValue, timestamp = new Date(), providerName = null) {
		saveMetrics(referenceId, ["$metricName":metricValue], timestamp, providerName)
	}

	void saveMetrics(referenceId, Map<String, Double> metrics, timestamp = new Date(), providerName = null) {
		getProvider(providerName).saveMetrics(referenceId.toString(), metrics, timestamp, getConfig())
	}

	void bulkSaveMetrics(referenceId, List<Map<Date, Map<String, Double>>> metricsByTime, providerName = null) {
		getProvider(providerName).bulkSaveMetrics(referenceId, metricsByTime, getConfig())
	}

	/*   
		{
			'server-0': {
				'212-14-2014-02:01:00': {
					'cpu' : 102.83333333333333,
					'memory' : 102.83333333333333
				},
				'212-14-2014-02:02:00': {
					'cpu' : 102.83333333333333,
					'memory' : 102.83333333333333
				}
			}
		}
	*/
	def getMetrics(Date start, Date end, String referenceIdQuery = null, String metricNameQuery = null, providerName = null) {
		getProvider(providerName).getMetrics(start, end, referenceIdQuery, metricNameQuery, getConfig())
	}

	/*   
		{
			'server-0': {
				'212-14-2014-02:00:00': {
					'cpu' : {
						'count': 12,
						'total': 1234,
						'avg': 102.83333333333333
					},
					'memory' : {
						'count': 12,
						'total': 1234,
						'avg': 102.83333333333333
					}
				}
				'212-14-2014-03:00:00': {
					'cpu' : {
						'count': 12,
						'total': 1234,
						'avg': 102.83333333333333
					},
					'memory' : {
						'count': 12,
						'total': 1234,
						'avg': 102.83333333333333
					}
				}
	
			}
		}
	*/
	def getMetricAggregates(String resolution, Date start, Date end, String referenceIdQuery = null, String metricNameQuery = null, providerName = null) {
		getProvider(providerName).getMetricAggregates(resolution, start, end, referenceIdQuery, metricNameQuery, getConfig())
	}

}
