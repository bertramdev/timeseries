timeseries
==========

Grails Plugin for read/write of timeseries data. Defines interface for pluggable storage providers and includes an in-memory storage provider.

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
    grails.plugins.timeseries.poop.aggregates = ['1h':'1d','1d':'60d']
```

The `timeSeriesService` provides methods for reading and writing time series data. 

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
