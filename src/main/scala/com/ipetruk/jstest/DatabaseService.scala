package com.ipetruk.jstest

import com.datastax.driver.core.{Row, ResultSet, Cluster}
import scala.collection.JavaConversions._
import org.scalatra.{Initializable, ScalatraServlet}
import concurrent.{Future, Promise, ExecutionContext}
import grizzled.slf4j.Logger

trait DatabaseServiceApi {
  def shutdown:Unit

  def query(cql:String, args:AnyRef*):Future[IterableResultSet]
}

trait DatabaseServiceConfig{
  def hosts:List[String] =
    List(Option(System.getenv("OPENSHIFT_CASSANDRA_HOST")).getOrElse("127.0.0.1"))

  def port:Int = Option(System.getenv("OPENSHIFT_CASSANDRA_PORT"))
    .map(i=>java.lang.Integer.parseInt(i))
    .getOrElse(9042)

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

  private[this] val logger = Logger(classOf[CassandraDatabaseServiceComponent])

  lazy val databaseServiceApi: DatabaseServiceApi = new DatabaseServiceApi{
    val (cluster, session) = {
      val cluster = Cluster
        .builder()
        .addContactPoints(databaseServiceConfig.hosts.toArray:_*)
        .build()
      val session = cluster.connect(databaseServiceConfig.keyspace)
      val metadata = cluster.getMetadata();
      logger.info("Connected to cluster: %s".format(
        metadata.getClusterName()));
      for (host <- metadata.getAllHosts()) {
        logger.info("Datacenter: %s; Host: %s; Rack: %s".format(
          host.getDatacenter(), host.getAddress(), host.getRack()));
      }
      (cluster, session)
    }

    def query(cql:String, args:AnyRef*)={
      logger.debug("CQL: "+cql+", args:"+args.toList)
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
      logger.info("Shutting down "+(cluster,session))
      session.shutdown()
      cluster.shutdown()
    }

  }

  override def shutdown(){
    super.shutdown()
    databaseServiceApi.shutdown
  }
}
