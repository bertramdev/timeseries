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

    static SUPPORTED_RESOLUTIONS_SIZE = ['1s': 1, '10s': 2, '30s': 3, '1m': 4, '15m': 5, '1h': 6, '2h': 7, '4h': 8, '12h': 9, '1d': 10]

	String getName()

	void flush(groovy.util.ConfigObject config) 

	void init(groovy.util.ConfigObject config) 

	void shutdown(groovy.util.ConfigObject config) 

	void manageStorage(groovy.util.ConfigObject config) 

    void saveMetrics(String referenceId, Map<String, Double> metrics, Date timestamp, groovy.util.ConfigObject config)

    void bulkSaveMetrics(String referenceId, List<Map<Date, Map<String, Double>>> metricsByTime, groovy.util.ConfigObject config)

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
    Map<String, Map<Date, Map<String, Double>>> getMetrics(Date start, Date end, String referenceIdQuery, String metricNameQuery, groovy.util.ConfigObject config)

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
    Map<String, Map<Date, Map<String, Map<String, Double>>>> getMetricAggregates(String resolution, Date start, Date end, String referenceIdQuery, String metricNameQuery, groovy.util.ConfigObject config)
}
