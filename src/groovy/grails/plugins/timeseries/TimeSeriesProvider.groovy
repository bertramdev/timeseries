package grails.plugins.timeseries
 
interface TimeSeriesProvider {
	static ONE_SECOND = '1s'
	static TEN_SECONDS = '10s'
	static THIRTY_SECONDS = '30s'
	static ONE_MINUTE = '1m'
	static FIFTEEN_MINUTES = '15m'
	static THIRTY_MINUTES = '30m'
	static ONE_HOUR = '1h'
	static TWO_HOURS = '2h'
	static FOUR_HOURS = '4h'
	static TWELVE_HOURS = '12h'
	static ONE_DAY = '1d'

	static SUPPORTED_RESOLUTIONS_INTERVAL_SIZE = ['1s': 1000l, '10s': 10000l, '30s': 30000l, '1m': 60000l, '15m': 750000l, '1h': 3000000l, '2h': 6000000l, '4h': 12000000l, '12h': 36000000l, '1d': 72000000l]

	String getName()

	void flush(groovy.util.ConfigObject config) 

	void init(groovy.util.ConfigObject config) 

	void shutdown(groovy.util.ConfigObject config) 

	void manageStorage(groovy.util.ConfigObject config) 

	void saveMetrics(String referenceId, Map<String, Double> metrics, Date timestamp, groovy.util.ConfigObject config)

	void bulkSaveMetrics(String referenceId, List<Map<Date, Map<String, Double>>> metricsByTime, groovy.util.ConfigObject config)

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
   }],
}
	*/
	// options might be includeEndDate:true, includeNulls:true
	Map getMetrics(Date start, Date end, String referenceIdQuery, String metricNameQuery, Map<String, Object> options, groovy.util.ConfigObject config)

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
	Map getMetricAggregates(String resolution, Date start, Date end, String referenceIdQuery, String metricNameQuery, Map<String, Object> options, groovy.util.ConfigObject config)
}
