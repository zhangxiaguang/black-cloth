(function (window, angular) {
    "use strict";

    angular.module('module.widgets.user')
        .controller('widgets.user.LoginController', [
            '$state',
            '$scope',
            'userService',
            'alertService',
            'appConfig',
            function ($state, $scope, userService, alertService, appConfig) {

                $scope.user = {
                    //username: 'hale@hale.com',
                    //password: 'hale'
                };
                $scope.invalidMessage = {};

                $scope.$watch('user.username', function () {
                    $scope.invalidMessage.$form = "";
                });

                $scope.$watch('user.password', function () {
                    $scope.invalidMessage.$form = '';
                });

                $scope.login = function () {
                    userService.login($scope.user).then(function(){
                        userService.getUserByName($scope.user.username).then(function(resp){
                            userService.setCurrentUser(resp);
                            $scope.postSignIn();
                        });
                    }, function(){
                        $scope.invalidMessage.$form = '用户不存在或者密码错误！';
                    })
                };

                $scope.forgetPwd = function () {
                    $state.go(appConfig.stateName.resetPwd);
                };
            }
        ]);

})(window, window.angular);
