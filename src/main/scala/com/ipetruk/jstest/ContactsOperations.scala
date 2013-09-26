package com.ipetruk.jstest

import scala.concurrent._
import java.util.UUID

case class ContactGroup(gid:Option[String], title:String)
case class Contact(cid:Option[String], gid:Option[String], name:String, email:Option[String], phone:Option[String])

case class Contacts(groups:List[ContactGroup], contacts: List[Contact])

trait ContactsOperations {
  self:  AppStack with AuthenticationOperations with DatabaseComponentServiceApi =>

  implicit private[this] val executor = ExecutionContext.Implicits.global

  delete("/contactsService/groups/:id"){
    assertCSRF

    val gid = params("id")

    asyncAuth { session =>
      for (_ <- databaseServiceApi.query("delete from groups where uid=? and gid=?",
        session.name, UUID.fromString(gid))) yield { }
    }
  }

  post("/contactsService/groups"){
    assertCSRF

    val bodyGroup = parsedBody.extract[ContactGroup]
    val updatedGroup = bodyGroup.copy(
      gid = Some(bodyGroup.gid.getOrElse(UUID.randomUUID().toString))
    )
    asyncAuth { session =>
      for (_ <- databaseServiceApi.query("insert into groups(uid,gid,group_name) values (?,?,?)",
        session.name, UUID.fromString(updatedGroup.gid.get), updatedGroup.title)) yield {
        updatedGroup
      }
    }
  }

  delete("/contactsService/contacts/:id"){
    assertCSRF

    val cid = params("id")

    asyncAuth { session =>
      for (_ <- databaseServiceApi.query("delete from contacts where uid=? and cid=?",
        session.name, UUID.fromString(cid))) yield { }
    }
  }

  post("/contactsService/contacts"){
    assertCSRF

    val bodyContact = parsedBody.extract[Contact]
    val updatedContact = bodyContact.copy(
      cid = Some(bodyContact.cid.getOrElse(UUID.randomUUID().toString))
    )
    import updatedContact._
    asyncAuth { session =>
      for (_ <- databaseServiceApi.query(
        "insert into contacts(uid,cid,email,gid,name,phone) values (?,?,?,?,?,?)",
        session.name, UUID.fromString(cid.get),
        email.getOrElse(null), gid.map(s=>UUID.fromString(s)).getOrElse(null),
        name, phone.getOrElse(null)
      )) yield {
        updatedContact
      }
    }
  }

  get("/contactsService/all"){
    assertCSRF

    contentType = formats("json")
    asyncAuth{ session =>
      val res = for {
        groupsResultSet <- databaseServiceApi.query("select * from groups where uid=?", session.name)
        contactsResultSet <- databaseServiceApi.query("select * from contacts where uid=?", session.name)
      } yield {
        val groupsList = groupsResultSet.map{ row=>
          ContactGroup(Some(row.getUUID("gid").toString), row.getString("group_name"))
        }.toList
        val contactsList = contactsResultSet.map{ row=>
          Contact(Some(row.getUUID("cid").toString), Option(row.getUUID("gid")).map(_.toString), row.getString("name"),
            Option(row.getString("email")),Option(row.getString("phone")))
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
