#!/bin/bash

cqlsh -3 -f ./db/keyspace.cql

ls ./db/*__*.cql | xargs -I {} cqlsh -3 -k jstest -f {}

