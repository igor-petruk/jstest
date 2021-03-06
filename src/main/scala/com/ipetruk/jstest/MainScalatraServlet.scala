package com.ipetruk.jstest

import java.util.UUID

class MainScalatraServlet extends AppStack
with CSRFServiceComponent
with CassandraDatabaseServiceComponent
with JNDISupport
with AuthenticationOperations
with SessionDaoComponent
with ContactsOperations{
  val databaseServiceConfig: DatabaseServiceConfig = new DatabaseServiceConfig{
    def keyspace: String = "jstest"
  }
}
