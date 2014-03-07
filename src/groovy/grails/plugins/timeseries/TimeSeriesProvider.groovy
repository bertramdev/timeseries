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

	static SUPPORTED_RESOLUTIONS_SIZE = ['1s': 1000l, '10s': 10000l, '30s': 30000l, '1m': 60000l, '15m': 750000l, '1h': 3000000l, '2h': 6000000l, '4h': 12000000l, '12h': 36000000l, '1d': 72000000l]

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
			'cpu': {
				212-14-2014-02:01:00': 102.83333333333333,
				212-14-2014-02:02:00': 102.83333333333333
			}
			'memory': {
				212-14-2014-02:01:00': 102.83333333333333,
				212-14-2014-02:02:00': 102.83333333333333
			}
		}
	}
	*/
	// options might be includeEndDate:true, includeNulls:true
	Map<String, Map<String, List<Map<String, Object>>>> getMetrics(Date start, Date end, String referenceIdQuery, String metricNameQuery, Map<String, Object> options, groovy.util.ConfigObject config)

	/*   
		{
			'server-0': {
				'cpu': {
					212-14-2014-02:01:00': {'count':12 'total', 1213455, 'avg': 102.83333333333333},
					212-14-2014-02:02:00': {'count':12 'total', 1213455, 'avg': 102.83333333333333}
				}
				'memory': {
					212-14-2014-02:01:00': {'count':12 'total', 1213455, 'avg': 102.83333333333333},
					212-14-2014-02:02:00': {'count':12 'total', 1213455, 'avg': 102.83333333333333}
				}
			}
		}
	*/
	Map<String, Map<String, List<Map<String, Object>>>> getMetricAggregates(String resolution, Date start, Date end, String referenceIdQuery, String metricNameQuery, Map<String, Object> options, groovy.util.ConfigObject config)
}
