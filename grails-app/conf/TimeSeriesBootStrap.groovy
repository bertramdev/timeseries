class TimeSeriesBootStrap {

	def timeSeriesService

	def init = { servletContext ->
		timeSeriesService.init()
	}

	def destroy = {
		timeSeriesService.destroy()
	}
}
