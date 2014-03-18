package grails.plugins.timeseries

import grails.transaction.Transactional
import grails.plugins.timeseries.*

@Transactional
class TimeSeriesService {
	private Map<String, TimeSeriesProvider> providers = [:]
	def grailsApplication
	static final DEFAULT_KEY = '__default'
	private Timer timer = new Timer()
	private Long manageStorageInterval
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
		try {
			timerTask.cancel()
			timer.cancel()
         callProviderMethod('shutdown')
		} catch(Throwable t) {
			log.error(t)
		}
		log.info("TimeSeriesService shutdown.")
	}

	void init() {
		if (grailsApplication.config.grails.plugins.timeseries.manageStorage.containsKey('interval')) {
			this.manageStorageInterval = Long.parseLong(grailsApplication.config.grails.plugins.timeseries.manageStorage.interval.toString())
		}
		// schedule "manageStorage" call to all teh providers
		if (this.manageStorageInterval) {
			timer.scheduleAtFixedRate(timerTask, this.manageStorageInterval, this.manageStorageInterval)
		}
      callProviderMethod('init')
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
		getProvider(providerName).flush(getConfig())		
	}

   void manageStorage(providerName = null) {
      getProvider(providerName).manageStorage(getConfig())    
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
   "start": "1970-01-01T00:00:00Z",
   "end": "2014-03-08T04:04:29Z",
   "resolutionName": "1s",
   "resolutionInterval": 1,
   "items": [{
      "referenceId": "server-0",
      "series": [
         {
            "name": "met1",
            "values": [
               {
                  "timestamp": "2014-03-08T04:01:29Z",
                  "value": 1
               },
               ...
               {
                  "timestamp": "2014-03-08T04:02:03Z",
                  "value": 35
               }
            ]
         },
         {
            "name": "met2",
            "values": [
               {
                  "timestamp": "2014-03-08T04:01:29Z",
                  "value": 120
               },
				...
               {
                  "timestamp": "2014-03-08T04:02:03Z",
                  "value": 86
               }
            ]
         }
      ]
   }]
}	*/
	Map getMetrics(Date start, Date end, String referenceIdQuery = null, String metricNameQuery = null, Map<String, Object> options = null, providerName = null) {
		getProvider(providerName).getMetrics(start, end, referenceIdQuery, metricNameQuery, options, getConfig())
	}

	/*   
{
   "start": "1970-01-01T00:00:00Z",
   "resolutionName": "1m",
   "end": "2014-03-08T04:04:29Z",
   "resolutionInterval": 60,   
   "items": [
      {
         "referenceId": "testSaveMetricsRegularWithAggregatesWithGet",
         "series": [
            {
               "values": [
                  {
                     "count": 32,
                     "start": "2014-03-08T04:01:00Z",
                     "sum": 528,
                     "average": 16.5
                  },
                  ...
                  {
                     "count": 29,
                     "start": "2014-03-08T04:03:00Z",
                     "sum": 3103,
                     "average": 107
                  }
               ],
               "name": "met1"
            },
            {
               "values": [
                  {
                     "count": 32,
                     "start": "2014-03-08T04:01:00Z",
                     "sum": 3344,
                     "average": 104.5
                  },
                  ...
                  {
                     "count": 29,
                     "start": "2014-03-08T04:03:00Z",
                     "sum": 406,
                     "average": 14
                  }
               ],
               "name": "met2"
            }
         ]
      },
      {
         "referenceId": "testSaveMetricsRegularWithAggregatesWithGet2",
         "series": [
            {
               "values": [
                  {
                     "count": 32,
                     "start": "2014-03-08T04:01:00Z",
                     "sum": 649.4399999999999,
                     "average": 20.294999999999998
                  },
                  ...
                  {
                     "count": 29,
                     "start": "2014-03-08T04:03:00Z",
                     "sum": 3816.6899999999996,
                     "average": 131.60999999999999
                  }
               ],
               "name": "met1"
            },
            {
               "values": [
                  {
                     "count": 32,
                     "start": "2014-03-08T04:01:00Z",
                     "sum": 4113.119999999999,
                     "average": 128.53499999999997
                  },
                  ...
                  {
                     "count": 29,
                     "start": "2014-03-08T04:03:00Z",
                     "sum": 499.38,
                     "average": 17.22
                  }
               ],
               "name": "met2"
            }
         ]
      }
   ]
}
	*/
	Map getMetricAggregates(String resolution, Date start, Date end, String referenceIdQuery = null, String metricNameQuery = null,Map<String, Object> options = null,  providerName = null) {
		getProvider(providerName).getMetricAggregates(resolution, start, end, referenceIdQuery, metricNameQuery, options, getConfig())
	}

}
