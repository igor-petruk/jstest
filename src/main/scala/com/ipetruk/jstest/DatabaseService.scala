package com.ipetruk.jstest

import com.datastax.driver.core.{Row, ResultSet, Cluster}
import scala.collection.JavaConversions._
import org.scalatra.{Initializable, ScalatraServlet}
import concurrent.{Future, Promise, ExecutionContext}

trait DatabaseServiceApi {
  def shutdown:Unit

  def query(cql:String, args:AnyRef*):Future[IterableResultSet]
}

trait DatabaseServiceConfig{
  def hosts:List[String] = List("127.0.0.1")

  def keyspace: String
}

trait DatabaseComponentServiceApi {
  def databaseServiceApi: DatabaseServiceApi

  val databaseServiceConfig: DatabaseServiceConfig
}

class IterableResultSet(rs:ResultSet){

  def mapDump = map{row => row.toString}

  def map[A](f: Row=>A):Stream[A]={
    import Stream._

//    println("StartingAll")
//
//    val list = (for (row <- rs.all()) yield {
//      f(row)
//    }).toList
//    println("Ending all")
//
//    list
    val iterator = rs.iterator()

    def nextItemStream:Stream[A] =
      if (iterator.hasNext)
        f(iterator.next()) #:: nextItemStream
      else
        empty[A]

    nextItemStream
  }
}

trait CassandraDatabaseServiceComponent extends DatabaseComponentServiceApi with Initializable{

  private[this] val executor = ExecutionContext.Implicits.global

  lazy val databaseServiceApi: DatabaseServiceApi = new DatabaseServiceApi{
    val (cluster, session) = {
      val cluster = Cluster
        .builder()
        .addContactPoints(databaseServiceConfig.hosts.toArray:_*)
        .build()
      val session = cluster.connect(databaseServiceConfig.keyspace)
      val metadata = cluster.getMetadata();
      System.out.printf("Connected to cluster: %s\n",
        metadata.getClusterName());
      for (host <- metadata.getAllHosts()) {
        System.out.printf("Datacenter: %s; Host: %s; Rack: %s\n",
          host.getDatacenter(), host.getAddress(), host.getRack());
      }
      (cluster, session)
    }

    def query(cql:String, args:AnyRef*)={
      val preparedQuery = session.prepare(cql)
      val bound = preparedQuery.bind(args:_*)
      val result = session.executeAsync(bound)

      val promise = Promise[IterableResultSet]()

      result.addListener(new Runnable(){
        def run() {
          try{
            val resultSet = result.get()
            promise.success(new IterableResultSet(resultSet))
          } catch {
            case e: Exception =>{
              e.printStackTrace()
              promise.failure(e)
            }
          }
        }
      }, ExecutionContext.Implicits.global)

      promise.future
    }

    def shutdown{
      println("Shutting down "+(cluster,session))
      session.shutdown()
      cluster.shutdown()
    }

  }

  override def shutdown(){
    super.shutdown()
    databaseServiceApi.shutdown
  }
}
