package com.ipetruk.jstest

import java.util.UUID

class MainScalatraServlet extends AppStack
with CassandraDatabaseServiceComponent
with AuthenticationOperations
with SessionDaoComponent
with ContactsOperations{
  val databaseServiceConfig: DatabaseServiceConfig = new DatabaseServiceConfig{
    def keyspace: String = "jstest"
  }
}
