/*
 * Copyright (C) 2015 Romain Reuillon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.iscpif.gridscale.egi

import java.io.InputStream
import java.net.URI

import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.config.RegistryBuilder
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.{ HttpHost, HttpRequest }

import scala.concurrent.duration._
import scala.io.Source

trait HTTPSClient {

  def maxConnections = 20
  def timeout: Duration
  def service: String
  def factory: Duration ⇒ ConnectionSocketFactory

  @transient lazy val pool = {
    val registry = RegistryBuilder.create[ConnectionSocketFactory]().register("https", factory(timeout)).build()
    val pool = new PoolingHttpClientConnectionManager(registry)
    pool.setMaxTotal(maxConnections)
    pool.setDefaultMaxPerRoute(maxConnections)
    pool
  }

  @transient lazy val httpContext = HttpClientContext.create

  def requestConfig = {
    RequestConfig.custom()
      .setSocketTimeout(timeout.toMillis.toInt)
      .setConnectTimeout(timeout.toMillis.toInt)
      .build()
  }

  def httpHost: HttpHost = {
    val uri = new URI(service)
    new HttpHost(uri.getHost, uri.getPort, uri.getScheme)
  }

  def clientBuilder = HttpClients.custom().setConnectionManager(pool)

  def requestContent[T](request: HttpRequestBase with HttpRequest)(f: InputStream ⇒ T): T = {
    val client = clientBuilder.build()

    request.setConfig(requestConfig)

    def close[T <: { def close(): Unit }, R](c: T)(f: T ⇒ R) =
      try f(c)
      finally c.close

    close(client.execute(httpHost, request, httpContext)) { response ⇒
      close(response.getEntity.getContent) { is ⇒
        f(is)
      }
    }
  }

  def request[T](request: HttpRequestBase with HttpRequest)(f: String ⇒ T): T =
    requestContent(request) { is ⇒
      f(Source.fromInputStream(is).mkString)
    }

}
