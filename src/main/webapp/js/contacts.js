/* Controller */

function ContactsController($scope, contacts, $timeout){
    var self = this;

    $scope.selectingGroups = function(){
        return (!$scope.selectedGroup)?true:false;
    };

    $scope.searchQuery = "";

    var timer=false;

    $scope.$watch('searchQuery', function(){
        if(timer){
            $timeout.cancel(timer);
        }
        timer= $timeout(function(){
            contacts.submitSearchTerm($scope.searchQuery);
        },100);
    });

    $scope.gravatarUrl = function(email){
        var emailLC = (email)?email.toLowerCase():"";
        return "http://www.gravatar.com/avatar/"+md5(emailLC);
    }

    contacts.setOnRefreshListener(function (data){
        $scope.groups = data.groups;
        //self.allContacts = data.contacts;
        console.log("Refreshed to "+JSON.stringify(data))
    });

    $scope.sortedContacts = function(){
        var result = null;
        if (($scope.selectedGroup == null) || ($scope.selectedGroup.id=="*")){
            result =  contacts.sortedContacts();
        }else{
            result =  _.filter(contacts.sortedContacts(), function(i){return i.gid == $scope.selectedGroup.gid;});
        }
        return _.filter(result, function(i){return i.matchedTrigrams==-1 || i.matchedTrigrams>=3});
    };

    $scope.groupsListLinkClicked = function(){
        $scope.selectedGroup = null;
    }

    $scope.groupItemClicked = function(item){
        if (item) {
            $scope.selectedGroup = item;
        }else{
            $scope.selectedGroup = {
                id: "*",
                title: "All groups"
            };
        }
    }
}

/* Service */

function ContactsService($http){
    this.$http = $http;
    this.contactIndex = {};
    this.contactList = [];
    this.searchTerm = "";
}

ContactsService.prototype.setOnRefreshListener = function(refreshListener){
    this.onRefresh = refreshListener;
}

ContactsService.prototype.reset = function(){
    var self = this;

    self.contactIndex = {};
    self.contactList = [];

    if (self.onRefresh){
        self.onRefresh({
            groups: [],
            contacts: []
        });
    }
}

ContactsService.prototype.getTrigrams = function(string){
    var self = this;

    var processedString = "  "+string.toLocaleUpperCase()+"  ";

    var result = [];

    for (var s = 0, e = 3; e<= processedString.length; s++, e++){
        result.push(processedString.substring(s, e));
    }

    return result;
}

ContactsService.prototype.sortedContacts = function(){
    var self = this;
    return _.sortBy(self.contactList, function(i){
        return i.matchedTrigrams;
    });
}

ContactsService.prototype.addContacts = function(contacts){
    var self = this;

    var newList = self.contactList.concat(contacts);

    angular.forEach(contacts, function(value){
        self.refreshUpToSearchTerm(value);
    });

    self.contactList = newList;


//    angular.forEach(contacts, function(value){
//        var trigrams = self.getTrigrams(value.name);
//
//        angular.forEach(trigrams, function(trigram){
//            var oci = self.contactIndex[trigram];
//            var ci = (oci)?oci:[];
//
//            ci.push(value);
//
//            self.contactIndex[trigram] = ci;
//        });
//    });
}

ContactsService.prototype.refreshUpToSearchTerm = function(contact){
    var self = this;

    if (self.searchTerm){
        var intersectionSet = _.intersection(self.getTrigrams(contact.name), self.searchTrigrams);
        contact.matchedTrigrams = intersectionSet.length;
    }else{
        contact.matchedTrigrams = -1;
    }
    contact.matchedTrigramsFunc = function(c){
        return function(){
            return c.matchedTrigrams;
        }
    }(contact);
}

ContactsService.prototype.submitSearchTerm = function(searchTerm){
    var self = this;

    self.searchTerm = searchTerm.trim().toLocaleUpperCase();
    self.searchTrigrams = self.getTrigrams(searchTerm.trim().toLocaleUpperCase());

    angular.forEach(self.contactList, function(value){
        self.refreshUpToSearchTerm(value);
    });
}

ContactsService.prototype.refresh = function(){
    var self = this;

    if (self.onRefresh){
        this.$http.get('/rest/contacts').
            success(function(data, status, headers) {
                self.addContacts(data.contacts);
                self.onRefresh({
                    groups: data.groups,
                    contacts: data.contacts
                });
            });
    }
}

angular.module('app.contacts',[])
    .factory('contacts', function($http){
        return new ContactsService($http);
    });
