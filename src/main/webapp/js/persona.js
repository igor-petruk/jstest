
function AuthController($scope, authenticator, contacts){
    $scope.loggedIn = false;
    $scope.currentUser = "";

    $scope.gravatarUrl = function(){
        return "http://www.gravatar.com/avatar/"+md5($scope.currentUser);
    }

    function safeApply(fn) {
        ($scope.$$phase || $scope.$root.$$phase) ? fn() : $scope.$apply(fn);
    }

    $scope.init = function(){
        console.log("Calling register");
        authenticator.register({
            onWatch: function(){
                var signinLink = document.getElementById('signin');
                if (signinLink) {
                    signinLink.onclick = function() { authenticator.login(); };
                }

                var signoutLink = document.getElementById('signout');
                if (signoutLink) {
                    signoutLink.onclick = function() { authenticator.logout(); };
                }
            },
            onLogin: function(username){
                console.log("Logged in "+username);
                $scope.loggedIn = true;
                $scope.currentUser = username;
                contacts.refresh();
            },
            onLogout: function(){
                safeApply( function(){
                    console.log("Logged out");
                    $scope.loggedIn = false;
                    $scope.currentUser = "(no user)";
                    console.log(contacts)
                    contacts.reset();
                });
            }
        });
    }
}

function Authenticator($http){
    this.$http = $http;
    this.loggedIn = false;
}

Authenticator.prototype.ensureWatch = function(){
    var self = this;

    if (!self.watching){
        navigator.id.watch({
    //                    loggedInUser: currentUser,
            onlogin: function(assertion) {
                console.log("Got assertion "+assertion)
                var assertionObj = {
                    value:assertion
                }
                if (self.config.onVerification){
                    self.config.onVerification();
                }
                self.$http.post('/rest/auth/validateAssertion',assertionObj).
                    success(function(data, status, headers){
                        self.loggedIn = true;
                        if (self.config.onLogin)
                            self.config.onLogin(data.email);
                    }).
                    error(function(data, status, headers){
                        navigator.id.logout();
                        alert("Login failure: " + err);
                    });
            },
            onlogout: function() {
                self.logout();
            }
        });
        self.watching = true;
    }
}

Authenticator.prototype.register =  function(config){
    var self = this;

    self.config = config;

    this.$http.get('/rest/auth/currentUser').
        success(function(data, status, headers) {
            var currentUser = (data)?data:null;
            console.log("Got user: "+currentUser)
            if (currentUser){
                self.loggedIn = true;
                if (config.onLogin)
                    config.onLogin(currentUser);
            }else{
                self.ensureWatch();
            }
            if (config.onWatch){
                config.onWatch();
            }
        }).
        error(function(data, status, headers){
            if (status==403){
                self.ensureWatch();
                if (config.onWatch){
                    config.onWatch();
                }
            }
        });
};

Authenticator.prototype.login = function(){
    navigator.id.request();
}

Authenticator.prototype.logout = function(){
    var self = this;
    if (self.loggedIn){
        self.loggedIn = false;
        self.ensureWatch()
        this.$http.post('/rest/auth/logout').
            success(function(data, status, headers){
                if (self.config.onLogout)
                    self.config.onLogout();
            });
        navigator.id.logout();
    }
}

angular.module('app.persona',[])
    .factory('authenticator', function($http){
        return new Authenticator($http);
    });
