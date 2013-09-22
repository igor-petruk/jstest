/* Controller */

function ContactsController($scope, contacts, $timeout, $http){
    var self = this;

    self.$http = $http;

    $scope.selectingGroups = function(){
        return (!$scope.selectedGroup)?true:false;
    };

    $scope.searchQuery = "";
    $scope.newGroup = {};

    $scope.selectedContact = null;

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
        $scope.selectedContact = null;
    }

    $scope.groupDialogOpen = function(group){
        $scope.currentGroup = group;
        $scope.newGroup = {
            title:group.title
        };
    }

    $scope.addGroup = function(){
        self.$http.post('/rest/contactsService/groups', {title: "New group"}).
            success(function(data, status, headers) {
                $scope.groups.push(data);
            });
    }

    $scope.editContactFormOpen = function(){
        $scope.newContact = {
            cid:$scope.selectedContact.cid,
            gid:$scope.selectedContact.gid,
            name:$scope.selectedContact.name,
            phone:$scope.selectedContact.phone,
            email:$scope.selectedContact.email
        }
    }

    $scope.editContactFormSubmit = function(){
        self.$http.post('/rest/contactsService/contacts', $scope.newContact).
            success(function(data, status, headers) {
                $scope.selectedContact.name = data.name;
                $scope.selectedContact.email = data.email;
                $scope.selectedContact.phone = data.phone;
            });
        angular.element("#editContactForm").modal('hide');
    }


    $scope.moveToGroup = function(gid){
        $scope.selectedContact.gid = gid;
        self.$http.post('/rest/contactsService/contacts', $scope.selectedContact);
    }

    $scope.deleteContact = function(){
        contacts.removeContact($scope.selectedContact);
        self.$http.delete('/rest/contactsService/contacts/'+$scope.selectedContact.cid);
        $scope.selectedContact = null;
    }

    $scope.addContact = function(){
        var selectedGid = null;
        if ($scope.selectedGroup.gid != "*"){
            selectedGid = $scope.selectedGroup.gid;
        }
        var contact = {
            name:"New Contact",
            gid: selectedGid
        }
        self.$http.post('/rest/contactsService/contacts', contact).
            success(function(data, status, headers) {
                contacts.addContacts([data]);
                $scope.selectedContact = data;
            });
    }

    $scope.removeGroupDialogSubmit = function(){
        $scope.groups = _.filter($scope.groups, function(i){ return i.gid!=$scope.currentGroup.gid;});
        angular.element("#removeGroupForm").modal('hide');
        self.$http.delete('/rest/contactsService/groups/'+$scope.currentGroup.gid);
    }

    $scope.renameGroupDialogSubmit = function(){
        $scope.currentGroup.title = $scope.newGroup.title;
        angular.element("#renameGroupForm").modal('hide');
        self.$http.post('/rest/contactsService/groups', {
            gid: $scope.currentGroup.gid,
            title: $scope.currentGroup.title
        })
    }

    $scope.selectContact = function(contact){
        $scope.selectedContact = contact;
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

ContactsService.prototype.removeContact = function(contact){
    var self = this;
    self.contactList = _.filter(self.contactList, function(i){return i.cid != contact.cid;})
}

ContactsService.prototype.addContacts = function(contacts){
    var self = this;

    angular.forEach(contacts, function(value){
        self.removeContact(value);
        self.refreshUpToSearchTerm(value);
    });

    self.contactList = self.contactList.concat(contacts);
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
        this.$http.get('/rest/contactsService/all').
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
