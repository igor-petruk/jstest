package com.ipetruk.jstest

import scala.concurrent._
import java.util.UUID

case class ContactGroup(gid:String, title:String)
case class Contact(cid:String, gid:String, name:String, email:String, phone:String)

case class Contacts(groups:List[ContactGroup], contacts: List[Contact])

trait ContactsOperations {
  self:  AppStack with AuthenticationOperations with DatabaseComponentServiceApi =>

  get("/contacts"){
    contentType = formats("json")
    asyncAuth{ session =>
      val res = for {
        groupsResultSet <- databaseServiceApi.query("select * from groups where uid=?", session.name)
        contactsResultSet <- databaseServiceApi.query("select * from contacts where uid=?", session.name)
      } yield {
        val groupsList = groupsResultSet.map{ row=>
          ContactGroup(row.getUUID("gid").toString, row.getString("group_name"))
        }.toList
        val contactsList = contactsResultSet.map{ row=>
          Contact(row.getUUID("cid").toString, row.getUUID("gid").toString, row.getString("name"),
            row.getString("email"),row.getString("email"))
        }.toList

        Contacts(
          groups = groupsList,
          contacts = contactsList
        )
      }
      res
    }
  }
}
