TimeSeries Grails Plugin
========================

Grails Plugin for read/write of timeseries data. Defines interface for pluggable storage providers and includes an in-memory storage provider. See also [timeseries-elastic-search](https://github.com/bertramdev/timeseries-elastic-search) and [timeseries-gorm](https://github.com/bertramdev/timeseries-gorm)

A time series is a sequence of data points, measured typically at successive points in time spaced at uniform time intervals. Database stats, JVM stats, and network stats are examples of time series data. 

In this plugin time series data is associated with an abitrary string "reference id". This can be a IP address, server name, device id or other unique identifier.

Multiple time series data can be stored for each reference id. Each type of data is an arbitrary string label called a "metric".

Values for each time series metric are stored at a configurable resolution (how often distinct values will be recorded). The plugin currently supports the following resolutions:

* ONE SECOND = '1s'
* TEN SECONDS = '10s'
* THIRTY SECONDS = '30s'
* ONE MINUTE = '1m'
* FIFTEEN MINUTES = '15m'
* THIRTY MINUTES = '30m'
* ONE HOUR = '1h'
* TWO HOURS = '2h'
* FOUR HOURS = '4h'
* TWELVE HOURS = '12h'
* ONE DAY = '1d'

Values for each time series metric also have a configurable expiration. Expiration can be any duration that uses the string syntax of a number followed by 's','m','h', or 'd'.

The resolution and expiration for each "metric" is configured in Config.groovy

```java
grails.plugins.timeseries.myMetric.resolution = '1s'
grails.plugins.timeseries.myMetric.expiration = '1d'
```

The default resolution is 1m and the default expiration is 1d.

The time series data is often reported as date histograms. Data points are aggregated and bucketed in time intervals. Depending on the storage provider, configuration of histograms may be required via Config.groovy (in-memory, Redis, GORM and Mongo) or can be determined at runtime (Elastic Search).

To configure aggregation of histogram data you must specify the resolutions and associated expiration in a map with the key being the resolution and the value being the expiration:

```java
    grails.plugins.timeseries.myMetric.aggregates = ['1h':'1d','1d':'60d']
```

The `timeSeriesService` provides methods for reading and writing time series data. Follow the Grails conventions for auto-wiring the service.

```java
    def timeSeriesService
```


Use the `saveMetric`, `saveMetrics`,  and `bulkSaveMetrics` methods to write time series data.

```java
    // save one data point for one moment in time(i.e. CPU utilization)
    void saveMetric(referenceId, String metricName, Double metricValue, timestamp = new Date(), providerName = null) 
    // save multiple data points for one moment in time (i.e. CPU utilization, Memory usage)
    void saveMetrics(referenceId, Map<String, Double> metrics, timestamp = new Date(), providerName = null)
    // save multiple data points for multiple moments in time
    void bulkSaveMetrics(referenceId, List<Map<Date, Map<String, Double>>> metricsByTime, providerName = null)
```

The optional `providerName` is the name of the time series data storage implementation provider.

Use the `getMetrics` and `getMetricAggregates` methods of `timeSeriesSerice` to read time series data.

```java
    Map getMetrics(Date start, Date end, String referenceIdQuery = null, String metricNameQuery = null, Map<String, Object> options = null, providerName = null)
    Map getMetricAggregates(String resolution, Date start, Date end, String referenceIdQuery = null, String metricNameQuery = null,Map<String, Object> options = null,  providerName = null)
```
The specific syntax of the referenceIdQuery and metricNameQuery depend on the storage provider. Regular expressions can be used for the in-memory and MongoDB implementations. A SQL like expression can be used for the GORM implementation. The Elastic Search implementation supports the ES query string query syntax.

The structures returned by the service read method follow a grammar intended to by compatible with JavaScript charting libraries. A time-series-charts plugin is TBD.

Sample return value from `getMetrics` in JSON:

```javascript
{
   "items": [{
      "series": [{
         "values": [
            {
               "timestamp": "2013-10-31T17:31:31Z",
               "value": 1
            },
            {
               "timestamp": "2013-10-31T17:31:32Z",
               "value": 2
            },
            ...
            {
               "timestamp": "2013-10-31T17:31:45Z",
               "value": 15
            }
         ],
         "name": "myMetric"
      }],
      "referenceId": "server-01"
   }],
   "start": "1970-01-01T00:00:00Z", 
   "end": "2014-03-15T16:02:08Z"
}
```

Sample return value from `getMetricAggregates` in JSON:

```javascript
{
   "items": [{
      "series": [
         {
            "values": [
               {
                  "min": 1,
                  "max": 29,
                  "count": 29,
                  "start": "2013-10-31T17:31:00Z",
                  "sum": 435,
                  "average": 15
               },
               ...
               {
                  "min": 90,
                  "max": 121,
                  "count": 32,
                  "start": "2013-10-31T17:33:00Z",
                  "sum": 3376,
                  "average": 105.5
               }
            ],
            "name": "myMetric"
         },
         {
            "values": [
               {
                  "min": 92,
                  "max": 120,
                  "count": 29,
                  "start": "2013-10-31T17:31:00Z",
                  "sum": 3074,
                  "average": 106
               },
               ...
               {
                  "min": 0,
                  "max": 31,
                  "count": 32,
                  "start": "2013-10-31T17:33:00Z",
                  "sum": 496,
                  "average": 15.5
               }
            ],
            "name": "myOtherMetric"
         }
      ],
      "referenceId": "server-01"
   }],
   "start": "1970-01-01T00:00:00Z",
   "end": "2014-03-15T16:02:17Z"
}
```

The in-memory storage provider supports serializing data to disk with the following configurations in Config.groovy:

```java
    grails.plugins.timeseries.providers.mem.persist = true
    grails.plugins.timeseries.providers.mem.storagePath = '/var/data/timeseries'
```

The expiration of data depends on the the storage provider. Some providers (GORM, in-memory, MongoDB) will run periodic routines to purge expired data by deleting records or dropping collections. Some providers (Redis, Elastic Search) expire data automatically. When applicable, you can configure the frequency of data purging with the following configuration in Config.groovy:

```java
grails.plugins.timeseries.manageStorage.interval=3600000l //ms
```

Custom time series data storage providers can be created by implementing the TimeSeriesProvider interface:

```java
    interface TimeSeriesProvider {
        String getName()
        void flush(groovy.util.ConfigObject config) 
        void init(groovy.util.ConfigObject config) 
        void shutdown(groovy.util.ConfigObject config) 
        void manageStorage(groovy.util.ConfigObject config) 
        void saveMetrics(String referenceId, Map<String, Double> metrics, Date timestamp, groovy.util.ConfigObject config)
        void bulkSaveMetrics(String referenceId, List<Map<Date, Map<String, Double>>> metricsByTime, groovy.util.ConfigObject config)
        Map getMetrics(Date start, Date end, String referenceIdQuery, String metricNameQuery, Map<String, Object> options, groovy.util.ConfigObject config)
        Map getMetricAggregates(String resolution, Date start, Date end, String referenceIdQuery, String metricNameQuery, Map<String, Object> options, groovy.util.ConfigObject config)
    }
```    

The provider must be registered to be available in the service. This can be done in BootStrap or the doWithApplicationContext plugin event handler.

```java
timeSeriesService.registerProvider(new grails.plugins.timeseries.gorm.GORMTimeSeriesProvider())
```

The last provider that is registered becomes the default provider.

The provider can extend `grails.plugins.timeseries.AbstractTimeSeriesProvider` to pick up some useful behavior.
