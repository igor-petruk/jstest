# jstest #

JS/Scala App for JS Course

## Nice things ##

1. Mozilla Persona for login. No passwords stored

2. Data and Sessions in Apache Cassandra, app is purely stateless

3. Single page app on AngularJS with minimal interaction with server

4. Client side trigram search

5. Gravatar :P

6. CSRF protection

## Not done ##

1. Proper testing

2. Separation of user id from email

3. Better validation

## Build & Run ##

1. Install SBT

2. Install Apache Cassandra (tested on 1.2.*)

3. Add {your cassandra dir}/bin to $PATH

4.

```sh
$ cassandra
$ cd jstest
$ ./bootstrap-db.sh
$ sbt
> compile
> container:start
> browse
```

If `browse` doesn't launch your browser, manually open [http://localhost:8080/](http://localhost:8080/) in your browser.
