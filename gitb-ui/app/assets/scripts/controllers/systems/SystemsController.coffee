class SystemsController

	constructor:(@$log, @$location, @$scope, @SystemService, @ErrorService) ->
		@$log.debug "Constructing SystemsController"

		@systems  = []       # systems of the organization
		@alerts   = []       # alerts to be displayed
		@modalAlerts   = []    # alerts to be displayed within modals
		@$scope.sdata  = {}    # bindings for new user
		@systemSpinner = false # spinner to be displayed for new system operations

		#initially get the registered Systems of the vendor
		@getSystems()

	getSystems: () ->
		@systemSpinner = true #start spinner
		@SystemService.getSystems()
		.then(
			(data) =>
				@systems = data
				@$log.debug angular.toJson(@systems)
				#stop spinner
				@systemSpinner = false
			,
			(error) =>
				@ErrorService.showErrorMessage(error)
				#stop spinner
				@systemSpinner = false
		)

	checkForm: () ->
		@closeAlerts()
		valid = true

		if @$scope.sdata.sname == undefined || @$scope.sdata.sname == ''
			@modalAlerts.push({type:'danger', msg:"You have to enter the short name of your system."})
			valid = false
		else if @$scope.sdata.fname == undefined || @$scope.sdata.fname == ''
			 @modalAlerts.push({type:'danger', msg:"You have to enter the full name of your system."})
			 valid = false
		else if @$scope.sdata.version == undefined || @$scope.sdata.version == ''
			 @modalAlerts.push({type:'danger', msg:"You have to enter the version of your system."})
			 valid = false

		valid


	registerSystem: () ->
		if @checkForm()
			@systemSpinner = true #start spinner
			@SystemService.registerSystem(@$scope.sdata.sname, @$scope.sdata.fname,
										  @$scope.sdata.description, @$scope.sdata.version)
			.then(
				(data) =>
					@getSystems() # get the list of systems again
				,
				(error) =>
					@ErrorService.showErrorMessage(error)
					#stop spinner
					@systemSpinner = false
			)
			#close modal when we are done
			$('#addSystemModal').modal('hide')
			return true

	closeAlert: (index) ->
		@alerts.splice(index, 1)

	closeModalAlert: (index) ->
		@modalAlerts.splice(index, 1)

	closeAlerts: () ->
		@alerts = []
		@modalAlerts = []

	redirect: (address, systemId) ->
		@$location.path(address + "/" + systemId)

controllers.controller('SystemsController', SystemsController)