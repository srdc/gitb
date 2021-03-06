class PasswordController

    constructor: (@$log, @$scope, @$location, @AccountService, @ErrorService, @ErrorCodes) ->
        @$log.debug 'Constructing PasswordController'

        @ds = @DataService #shorten service name
        @users  = []       # users of the organization
        @alerts = []       # alerts to be displayed
        @$scope.data = {}  # holds scope bindings
        @passwordSpinner = false # spinner to be displayed for password operations

    resetPassword: () ->
        @alerts = []
        @alerts.push({type:'success', msg:"An email to reset your password has been sent."})

    updatePassword: () ->
        if @checkForm()
            @passwordSpinner = true #start spinner
            @AccountService.updateUserProfile(null, @$scope.data.password1, @$scope.data.currentPassword)
            .then(
                (data) => #success handler
                    @alerts.push({type:'success', msg:"Your password has been updated."})
                    @passwordSpinner = false #stop spinner
                ,
                (error) => #error handler
                    if error.data.error_code == @ErrorCodes.INVALID_CREDENTIALS
                        @alerts.push({type:'danger', msg:"You entered a wrong password."})
                    else #unidentified error
                        @ErrorService.showErrorMessage(error)
                    #stop spinner
                    @passwordSpinner = false
            )
            @$scope.data = {}

    checkForm: () ->
        @alerts = []
        valid = true

        if @$scope.data.currentPassword == undefined || @$scope.data.currentPassword == ''
            @alerts.push({type:'danger', msg:"You have to enter your current password."})
            valid = false
        else if @$scope.data.password1 == undefined || @$scope.data.password1 == ''
            @alerts.push({type:'danger', msg:"You have to enter a new password."})
            valid = false
        else if @$scope.data.password2 == undefined || @$scope.data.password2 == ''
            @alerts.push({type:'danger', msg:"You have to confirm your new password."})
            valid = false
        else if @$scope.data.password1 != @$scope.data.password2
            @alerts.push({type:'danger', msg:"Passwords do not match."})
            valid = false

        valid

    closeAlert: (index) ->
        @alerts.splice(index, 1)


controllers.controller('PasswordController', PasswordController)