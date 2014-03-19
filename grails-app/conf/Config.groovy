log4j = {
	error 'org.codehaus.groovy.grails',
	      'org.springframework',
	      'org.hibernate',
	      'net.sf.ehcache.hibernate'
}

grails.plugins.timeseries.shutdownHook = true
grails.plugins.timeseries.initHook = true
grails.plugins.timeseries.manageStorage.interval = 60000l