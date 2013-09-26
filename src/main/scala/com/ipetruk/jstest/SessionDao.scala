package com.ipetruk.jstest

import concurrent.{ExecutionContext, Future}
import java.util.UUID
import scala.collection.mutable
import ExecutionContext.Implicits.global
import grizzled.slf4j.Logger

case class Session(name:String)

trait SessionDaoApi {
  def getSession(sid:UUID):Future[Map[String,String]]

  def updateSessionFields(sid:UUID, updatedFields:Map[String,String]):Future[Unit]

  def deleteSession(sid:UUID):Future[Unit]
}

trait SessionDaoComponentApi {
  def sessionDao: SessionDaoApi
}

trait SessionDaoComponent extends SessionDaoComponentApi{
  self:DatabaseComponentServiceApi with AsyncSupport =>

  private[this] val logger = Logger(classOf[SessionDaoComponent])

  lazy val sessionDao: SessionDaoApi = new SessionDaoApi {
    def updateSessionFields(sid: UUID, updatedFields: Map[String, String]): Future[Unit] = {
      val list =
        for (field <- updatedFields) yield {
          for (x <- databaseServiceApi.query(
            "insert into session_store(sid, sikey, value) values (?,?,?) USING TTL 10800",
            sid, field._1, field._2
          )) yield {
            x
          }
        }
      val totalFuture = Future.sequence(list)
      totalFuture.map {rows =>
        Unit
      }
    }

    def deleteSession(sid: UUID): Future[Unit] = {
      for (result <- databaseServiceApi.query("delete from session_store where sid=?",sid)) yield {
        Unit
      }
    }

    def getSession(sid: UUID): Future[Map[String, String]] = {
      for (result <- databaseServiceApi.query("select * from session_store where sid=?",sid)) yield {
        val map = result.map{row=>
          (row.getString("sikey"),row.getString("value"))
        }.toList.toMap
        logger.debug("Session: "+map)
        map
      }
    }

  }
}
