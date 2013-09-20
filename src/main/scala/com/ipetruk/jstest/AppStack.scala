package com.ipetruk.jstest

import org.scalatra._
import scalate.ScalateSupport
import org.fusesource.scalate.{ TemplateEngine, Binding }
import org.fusesource.scalate.layout.DefaultLayoutStrategy
import javax.servlet.http.HttpServletRequest
import collection.mutable

import org.json4s.{DefaultFormats, Formats}

import org.scalatra.json._
import concurrent.{Future, ExecutionContext}

trait AppStack extends ScalatraServlet with JacksonJsonSupport with FutureSupport with AsyncSupport{

  protected implicit val jsonFormats: Formats = DefaultFormats

  protected implicit def executor: ExecutionContext = ExecutionContext.Implicits.global
}
